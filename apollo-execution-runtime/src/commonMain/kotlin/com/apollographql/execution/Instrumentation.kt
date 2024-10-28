package com.apollographql.execution

import com.apollographql.apollo.api.ExecutionContext


/**
 * An [Instrumentation] monitors the execution algorithm.
 *
 * Compared to a [Resolver], it's also called for built-in introspection fields and can monitor the completed value.
 */
abstract class Instrumentation {
  /**
   * Called before the [Resolver] is called.
   * @return an [InstrumentationCallback] called after the field is executed
   * @throws Exception if something goes wrong. If an instrumentation fails, the whole field
   * fails and an error is returned.
   */
  open fun beforeField(resolveInfo: ResolveInfo): InstrumentationCallback? {
    return null
  }

  /**
   * Allows modifying [onResponse]
   */
  open fun onResponse(response: GraphQLResponse, executionContext: ExecutionContext): GraphQLResponse {
    return response
  }
}

fun interface InstrumentationCallback {
  /**
   * Called when a field value is completed.
   *
   * @param value the value after completion
   * @throws Exception if something goes wrong. If an instrumentation fails, the whole field
   * fails and an error is returned.
   */
  fun afterComplete(value: ExternalValue)
}