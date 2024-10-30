@file:OptIn(ExperimentalEncodingApi::class)

package com.apollographql.execution.reporting

import com.apollographql.apollo.api.ExecutionContext
import kotlin.io.encoding.ExperimentalEncodingApi

class ApolloReportsOperationContext() : ExecutionContext.Element {
  val apolloOperationTracing = ApolloOperationTracing()

  override val key = Key

  companion object Key : ExecutionContext.Key<ApolloReportsOperationContext>
}

