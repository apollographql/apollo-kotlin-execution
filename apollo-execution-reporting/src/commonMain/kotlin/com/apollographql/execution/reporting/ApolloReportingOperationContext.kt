@file:OptIn(ExperimentalEncodingApi::class)

package com.apollographql.execution.reporting

import com.apollographql.apollo.api.ExecutionContext
import com.apollographql.execution.FieldCallback
import com.apollographql.execution.ResolveInfo
import kotlin.io.encoding.ExperimentalEncodingApi

class ApolloReportingOperationContext(
  private val clientName: String? = null,
  private val clientVersion: String? = null,
) : ExecutionContext.Element {
  private val apolloOperationTracing = ApolloOperationTracing()

  fun beforeField(info: ResolveInfo): FieldCallback? = apolloOperationTracing.beforeField(info)

  fun toProtoTrace(): Trace = apolloOperationTracing.toProtoTrace(clientName, clientVersion)

  override val key = Key

  companion object Key : ExecutionContext.Key<ApolloReportingOperationContext>
}

