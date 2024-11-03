package com.apollographql.execution.reporting

import com.apollographql.apollo.api.Error
import com.apollographql.execution.ExternalValue

/**
 * An internal node. It is mostly a copy of [Trace.Node] but is 100% mutable so we can build the tree as
 * execution is happening.
 * Note that a node does not match a resolver invocation due to lists.
 */
internal class ApolloNode(private val id: Any?) {
  val children = mutableListOf<ApolloNode>()
  var parentType: String = ""
  var originalFieldName: String = ""
  var type: String = ""
  var startNanos: Long = 0L
  var endNanos: Long = 0L
  var value: ExternalValue? = null

  fun toProtoNode(): Trace.Node {
    val errors = mutableListOf<Trace.Error>()
    val value = value
    if (value is Error) {
      errors.add(
        Trace.Error(
          message = value.message,
          location = value.locations.orEmpty().map { Trace.Location(it.line, it.column) }
        )
      )
    }
    return Trace.Node(
      response_name = id as? String,
      index = id as? Int,
      original_field_name = originalFieldName,
      type = type,
      parent_type = parentType,
      cache_policy = null,
      start_time = startNanos,
      end_time = endNanos,
      error = errors,
      child = children.map { it.toProtoNode() }
    )
  }
}