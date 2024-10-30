@file:OptIn(ExperimentalEncodingApi::class)

package com.apollographql.execution.subgraph

import com.apollographql.apollo.api.ExecutionContext
import com.apollographql.execution.Instrumentation
import com.apollographql.execution.FieldCallback
import com.apollographql.execution.OperationCallback
import com.apollographql.execution.OperationInfo
import com.apollographql.execution.ResolveInfo
import com.apollographql.execution.reporting.ApolloOperationTracing
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class Ftv1Instrumentation : Instrumentation() {
  override fun onField(resolveInfo: ResolveInfo): FieldCallback? {
    val ftv1Context = resolveInfo.executionContext[Ftv1Context]
    if (ftv1Context == null) {
      return null
    }
    return ftv1Context.apolloOperationTracing.beforeField(resolveInfo)
  }

  override fun onOperation(operationInfo: OperationInfo): OperationCallback? = OperationCallback { response ->
    val ftv1Context = operationInfo.executionContext[Ftv1Context]
    if (ftv1Context == null) {
      response
    } else {
      response.newBuilder()
        .extensions(mapOf("ftv1" to Base64.encode(ftv1Context.apolloOperationTracing.toProtoTrace().encode())))
        .build()
    }
  }
}

class Ftv1Context() : ExecutionContext.Element {
  val apolloOperationTracing: ApolloOperationTracing = ApolloOperationTracing()

  override val key = Key

  companion object Key : ExecutionContext.Key<Ftv1Context>
}
