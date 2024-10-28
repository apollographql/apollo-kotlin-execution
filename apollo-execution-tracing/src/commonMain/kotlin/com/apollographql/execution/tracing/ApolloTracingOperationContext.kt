@file:OptIn(ExperimentalEncodingApi::class)

package com.apollographql.execution.tracing

import com.apollographql.apollo.api.Error
import com.apollographql.apollo.api.ExecutionContext
import com.apollographql.apollo.ast.responseName
import com.apollographql.apollo.ast.toUtf8
import com.apollographql.execution.ExternalValue
import com.apollographql.execution.ResolveInfo
import kotlin.io.encoding.ExperimentalEncodingApi

class ApolloTracingOperationContext() : ExecutionContext.Element {
  val apolloOperationTracing = ApolloOperationTracing()

  override val key = Key

  companion object Key : ExecutionContext.Key<ApolloTracingOperationContext>
}

