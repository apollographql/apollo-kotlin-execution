@file:OptIn(ApolloExperimental::class)

package com.apollographql.execution.reporting

import com.apollographql.apollo.annotations.ApolloExperimental
import com.apollographql.apollo.ast.GQLField
import com.apollographql.apollo.ast.GQLFloatValue
import com.apollographql.apollo.ast.GQLFragmentSpread
import com.apollographql.apollo.ast.GQLInlineFragment
import com.apollographql.apollo.ast.GQLIntValue
import com.apollographql.apollo.ast.GQLInterfaceTypeDefinition
import com.apollographql.apollo.ast.GQLNode
import com.apollographql.apollo.ast.GQLSelection
import com.apollographql.apollo.ast.GQLStringValue
import com.apollographql.apollo.ast.GQLTypeDefinition
import com.apollographql.apollo.ast.TransformResult
import com.apollographql.apollo.ast.definitionFromScope
import com.apollographql.apollo.ast.rawType
import com.apollographql.apollo.ast.toUtf8
import com.apollographql.apollo.ast.transform
import com.apollographql.execution.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class ApolloReportingInstrumentation(val apolloKey: String? = null) : Instrumentation() {
  val client = HttpClient(CIO)

  private val operations = mutableMapOf<String, OperationData>()
  private var operationCount: Int = 0

  @OptIn(DelicateCoroutinesApi::class)
  private fun scheduleReport() {
    val report = Report(
      traces_pre_aggregated = false,
      traces_per_query = operations.mapValues {
        TracesAndStats(
          trace = it.value.traces,
          stats_with_context = emptyList(),
          referenced_fields_by_type = it.value.referencedFields,
        )
      },
      operation_count = operations.size.toLong(),
    )

    operations.clear()
    GlobalScope.launch {
      sendReport(client, apolloKey!!, report)
    }
  }

  override fun onOperation(operationInfo: OperationInfo): OperationCallback? {
    return OperationCallback { response ->
      if (apolloKey == null) {
        return@OperationCallback response
      }
      if (response.data != null) {
        // TODO: send errors
        val operationData = getOperationData(operationInfo)
        if (operationData != null) {
          val trace =
            operationInfo.executionContext[ApolloReportingOperationContext]!!.apolloOperationTracing.toProtoTrace()

          val operation = operations.getOrPut(operationData.statsReportKey) { operationData }
          operation.traces.add(trace)
          operationCount++

          if (operationCount >= 1) {
            scheduleReport()
          }
        }
      }
      response
    }
  }

  override fun onField(resolveInfo: ResolveInfo): FieldCallback? {
    val atc = resolveInfo.executionContext[ApolloReportingOperationContext]
    require(atc != null) {
      "ApolloTracingInstrumentation requires an ApolloTracingContext"
    }
    return atc.apolloOperationTracing.beforeField(resolveInfo)
  }
}

private suspend fun sendReport(client: HttpClient, apolloKey: String, report: Report) {
  try {
    val response = client.post("https://usage-reporting.api.apollographql.com/api/ingress/traces") {
      setBody(report.encode())
      accept(ContentType.parse("application/json"))
      header("x-api-key", apolloKey)
    }

    require(response.status.isSuccess()) {
      "Reporting failed with status '${response.status}': ${response.bodyAsText()}"
    }
  } catch (e: Exception) {
    println("Cannot send reporting")
    e.printStackTrace()
  }
}

private class OperationData(val statsReportKey: String, val referencedFields: Map<String, ReferencedFieldsForType>) {
  val traces = mutableListOf<Trace>()
}

private fun getOperationData(operationInfo: OperationInfo): OperationData? {
  val operation = operationInfo.operation
  if (operation.name == null) {
    return null
  }

  try {
    val statsReportKey = buildString {
      append("# ${operation.name}")
      appendLine()
      append(operationInfo.operation.minimized())
    }

    val referencedFields = mutableMapOf<String, ApolloFields>()
    walk(
      operation.selections,
      operationInfo.schema.rootTypeNameFor(operation.operationType),
      referencedFields,
      operationInfo
    )

    return OperationData(statsReportKey, referencedFields.mapValues {
      ReferencedFieldsForType(
        field_names = it.value.fields.toList(),
        is_interface = it.value.isInterface
      )
    })
  } catch (e: Exception) {

    return null
  }
}

private class ApolloFields(val isInterface: Boolean) {
  val fields = mutableSetOf<String>()
}

private fun walk(
  selections: List<GQLSelection>,
  parentType: String,
  map: MutableMap<String, ApolloFields>,
  info: OperationInfo
) {
  val type = info.schema.typeDefinition(parentType)
  selections.forEach {
    walk(it, type, map, info)
  }
}

private fun walk(
  selection: GQLSelection,
  parentType: GQLTypeDefinition,
  map: MutableMap<String, ApolloFields>,
  info: OperationInfo
) {
  when (selection) {
    is GQLField -> {
      val fields = map.getOrPut(parentType.name) { ApolloFields(parentType is GQLInterfaceTypeDefinition) }
      fields.fields.add(selection.name)

      walk(
        selection.selections,
        selection.definitionFromScope(info.schema, parentType.name)!!.type.rawType().name,
        map,
        info
      )
    }

    is GQLFragmentSpread -> {
      val fragmentDefinition = info.fragments.get(selection.name)!!
      val parentType = fragmentDefinition.typeCondition.name
      walk(fragmentDefinition.selections, parentType, map, info)
    }

    is GQLInlineFragment -> {
      walk(selection.selections, selection.typeCondition?.name ?: parentType.name, map, info)
    }
  }
}

private fun GQLNode.minimized(): String {
  return removeLiterals().toUtf8("").replace(Regex("\\s+"), " ")
    .replace(Regex("([^_a-zA-Z0-9]) ")) { it.groupValues[1] }.replace(Regex(" ([^_a-zA-Z0-9])")) { it.groupValues[1] }
}

private fun GQLNode.removeLiterals(): GQLNode {
  return transform {
    when (it) {
      is GQLIntValue -> {
        TransformResult.Replace(it.copy(value = "0"))
      }

      is GQLFloatValue -> {
        /**
         * Because in JS (0.0).toString == "0" (vs "0.0" on the JVM), we replace the FloatValue by an IntValue
         * Since we always hide literals, this should be correct
         * See https://youtrack.jetbrains.com/issue/KT-33358
         */
        TransformResult.Replace(
          GQLIntValue(
            sourceLocation = it.sourceLocation, value = "0"
          )
        )
      }

      is GQLStringValue -> {
        TransformResult.Replace(it.copy(value = ""))
      }

      else -> TransformResult.Continue
    }
  }!!
}