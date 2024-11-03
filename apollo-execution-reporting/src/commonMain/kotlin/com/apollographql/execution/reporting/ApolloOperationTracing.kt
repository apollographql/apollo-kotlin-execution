package com.apollographql.execution.reporting

import com.apollographql.apollo.ast.toUtf8
import com.apollographql.execution.FieldCallback
import com.apollographql.execution.ResolveInfo
import com.squareup.wire.ofEpochSecond
import kotlinx.datetime.Clock
import kotlin.time.TimeSource.Monotonic.markNow

class ApolloOperationTracing {
  private val nodes = mutableMapOf<List<Any>, ApolloNode>()

  private val rootNode = ApolloNode(null)

  internal val operationStartMark = markNow()
  private val startInstant = Clock.System.now()

  init {
    nodes.put(emptyList(), rootNode)
  }

  internal fun newNode(path: List<Any>): ApolloNode {
    val node = ApolloNode(path.last())
    nodes.put(path, node)

    val parent = ensureParent(path)
    parent.children.add(node)
    return node
  }

  private fun ensureParent(path: List<Any>): ApolloNode {
    val parentPath = path.dropLast(1)
    val parentNode = nodes.get(parentPath)
    if (parentNode != null) {
      return parentNode
    }
    return newNode(parentPath)
  }

  fun toProtoTrace(): Trace {
    val endInstant = Clock.System.now()
    return Trace(
      start_time = ofEpochSecond(startInstant.epochSeconds, startInstant.nanosecondsOfSecond.toLong()),
      end_time = ofEpochSecond(endInstant.epochSeconds, endInstant.nanosecondsOfSecond.toLong()),
      duration_ns = (markNow() - operationStartMark).inWholeNanoseconds,
      root = rootNode.toProtoNode()
    )
  }

  fun beforeField(resolveInfo: ResolveInfo): FieldCallback? {
    val node = newNode(resolveInfo.path)
    node.parentType = resolveInfo.parentType
    node.originalFieldName = resolveInfo.fieldName
    node.type = resolveInfo.fieldDefinition().type.toUtf8()
    node.startNanos = markNow().minus(operationStartMark).inWholeNanoseconds
    return FieldCallback {
      node.value = it
      node.endNanos = markNow().minus(operationStartMark).inWholeNanoseconds
    }
  }
}