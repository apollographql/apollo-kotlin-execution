package com.apollographql.execution.tracing

import com.apollographql.execution.Instrumentation
import com.apollographql.execution.InstrumentationCallback
import com.apollographql.execution.ResolveInfo

class ApolloTracingInstrumentation: Instrumentation() {
  override fun beforeField(resolveInfo: ResolveInfo): InstrumentationCallback? {
    val atc = resolveInfo.executionContext[ApolloTracingOperationContext]
    require(atc != null) {
      "ApolloTracingInstrumentation requires an ApolloTracingContext"
    }
    return atc.apolloOperationTracing.beforeField(resolveInfo)
  }
}