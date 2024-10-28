package com.apollographql.execution.tracing

import com.apollographql.apollo.api.ExecutionContext
import com.apollographql.apollo.ast.GQLBooleanValue
import com.apollographql.apollo.ast.GQLEnumValue
import com.apollographql.apollo.ast.GQLFloatValue
import com.apollographql.apollo.ast.GQLIntValue
import com.apollographql.apollo.ast.GQLListValue
import com.apollographql.apollo.ast.GQLNullValue
import com.apollographql.apollo.ast.GQLObjectValue
import com.apollographql.apollo.ast.GQLStringValue
import com.apollographql.apollo.ast.GQLValue
import com.apollographql.apollo.ast.GQLVariableValue
import com.apollographql.execution.Coercing
import com.apollographql.execution.JsonValue
import com.apollographql.execution.Resolver

fun Resolver.withApolloTracing(): Resolver = Resolver {
  val context = it.executionContext[ApolloTracingContext]
  require(context != null) {
    "Using an Apollo tracing resolver requires an ApolloTracingContext"
  }


}

class ApolloTracingContext(): ExecutionContext.Element {
  override val key = Key

  companion object Key: ExecutionContext.Key<ApolloTracingContext>
}

object _AnyCoercing: Coercing<Any?> {
  override fun serialize(internalValue: Any?): JsonValue {
    return internalValue
  }

  override fun deserialize(value: JsonValue): Any? {
    return value
  }

  override fun parseLiteral(value: GQLValue): Any? {
    return when (value) {
      is GQLBooleanValue -> value.value
      is GQLEnumValue -> value.value
      is GQLFloatValue -> value.value
      is GQLIntValue -> value.value
      is GQLListValue -> value.values.map { parseLiteral(it) }
      is GQLNullValue -> null
      is GQLObjectValue -> value.fields.map { it.name to parseLiteral(it.value) }.toMap()
      is GQLStringValue -> value.value
      is GQLVariableValue -> error("Cannot coerce variable")
    }
  }
}
