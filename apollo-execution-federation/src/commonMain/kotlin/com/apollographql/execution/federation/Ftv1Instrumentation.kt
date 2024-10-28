@file:OptIn(ExperimentalEncodingApi::class)

package com.apollographql.execution.federation

import com.apollographql.apollo.api.ExecutionContext
import com.apollographql.execution.GraphQLResponse
import com.apollographql.execution.Instrumentation
import com.apollographql.execution.InstrumentationCallback
import com.apollographql.execution.ResolveInfo
import com.apollographql.execution.tracing.ApolloOperationTracing
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class Ftv1Instrumentation: Instrumentation() {
  override fun beforeField(resolveInfo: ResolveInfo): InstrumentationCallback? {
    val ftv1Context = resolveInfo.executionContext[Ftv1Context]
    if (ftv1Context == null) {
      return null
    }
    return ftv1Context.apolloOperationTracing.beforeField(resolveInfo)
  }
  
  override fun onResponse(response: GraphQLResponse, executionContext: ExecutionContext): GraphQLResponse {
    val ftv1Context = executionContext[Ftv1Context]
    if (ftv1Context == null) {
      return response
    }
    return response.newBuilder()
      .extensions(mapOf("ftv1" to Base64.encode(ftv1Context.apolloOperationTracing.toProtoTrace().encode())))
      .build()
  }
}

class Ftv1Context() : ExecutionContext.Element {
  val apolloOperationTracing: ApolloOperationTracing = ApolloOperationTracing()

  override val key = Key

  companion object Key : ExecutionContext.Key<Ftv1Context>
}
