package com.apollographql.execution

import com.apollographql.apollo.api.json.JsonNumber
import com.apollographql.apollo.ast.*
import com.apollographql.execution.internal.ExternalValue
import com.apollographql.execution.internal.InternalValue

/**
 * See https://www.graphql.de/blog/scalars-in-depth/
 */
interface Coercing<T> {
  /**
   * Serializes from an internal value (Kotlin) to an external value (typically JSON).
   *
   * For an example Date --> String
   */
  fun serialize(internalValue: T): ExternalValue

  /**
   * Deserializes from an external value (typically JSON) to an internal value (Kotlin).
   *
   * For an example String --> Date
   */
  fun deserialize(value: ExternalValue): T

  /**
   * Parses from a GraphQL value to an internal value (Kotlin)
   */
  fun parseLiteral(value: GQLValue): T
}


internal fun leafCoercingSerialize(value: InternalValue, coercings: Map<String, Coercing<*>>, typedefinition: GQLTypeDefinition): ExternalValue {
  return when (typedefinition.name) {
    "Int" -> {
      check(value is Int)
      value
    }
    "Float" -> {
      check(value is Double)
      value
    }
    "String" -> {
      check(value is String)
      value
    }
    "Boolean" -> {
      check(value is Boolean)
      value
    }
    "ID" -> {
      when (value) {
        is String -> value
        is Int -> value.toString()
        is Long -> value.toString()
        is JsonNumber -> value.value
        else -> error("Cannot coerce '$value' to an ID")
      }
    }
    else -> {
      @Suppress("UNCHECKED_CAST")
      val coercing = coercings.get(typedefinition.name) as Coercing<ExternalValue>?
      if (coercing == null) {
        if (typedefinition is GQLEnumTypeDefinition) {
          check(value is String)
          value
        } else {
          error("Cannot get coercing for '${typedefinition.name}'")
        }
      } else {
        coercing.serialize(value)
      }
    }
  }
}

object IntCoercing: Coercing<Int> {
  override fun serialize(internalValue: Int): ExternalValue {
    return internalValue
  }

  override fun deserialize(value: ExternalValue): Int {
    check(value is Number)
    return value.toInt()
  }

  override fun parseLiteral(value: GQLValue): Int {
    check(value is GQLIntValue)
    return value.value.toInt()
  }
}

object FloatCoercing: Coercing<Double> {
  override fun serialize(internalValue: Double): ExternalValue {
    return internalValue
  }

  override fun deserialize(value: ExternalValue): Double {
    check(value is Number)
    return value.toDouble()
  }

  override fun parseLiteral(value: GQLValue): Double {
    return when (value) {
      is GQLIntValue -> value.value.toDouble()
      is GQLFloatValue -> value.value.toDouble()
      else -> error("")
    }
  }
}

object BooleanCoercing: Coercing<Boolean> {
  override fun serialize(internalValue: Boolean): ExternalValue {
    return internalValue
  }

  override fun deserialize(value: ExternalValue): Boolean {
    check(value is Boolean)
    return value
  }

  override fun parseLiteral(value: GQLValue): Boolean {
    check(value is GQLIntValue)
    return value.value.toBooleanStrict()
  }
}

object StringCoercing: Coercing<String> {
  override fun serialize(internalValue: String): ExternalValue {
    return internalValue
  }

  override fun deserialize(value: ExternalValue): String {
    check(value is String)
    return value
  }

  override fun parseLiteral(value: GQLValue): String {
    check(value is GQLStringValue)
    return value.value
  }
}

internal fun scalarCoercingParseLiteral(value: GQLValue, coercings: Map<String, Coercing<*>>, typename: String): InternalValue {
  return when (typename) {
    "Int" -> {
      check(value is GQLIntValue)
      value.value.toInt()
    }
    "Float" -> {
      check(value is GQLFloatValue)
      value.value.toDouble()
    }
    "String" -> {
      check(value is GQLStringValue) {
        "'$value' is not a String"
      }
      value.value
    }
    "Boolean" -> {
      check(value is GQLBooleanValue)
      value.value
    }
    "ID" -> {
      when (value) {
        is GQLStringValue -> value.value
        is GQLIntValue -> value.value
        else -> error("Cannot parse '$value' to an ID String")
      }
    }
    else -> {
      @Suppress("UNCHECKED_CAST")
      val coercing = coercings.get(typename) as Coercing<ExternalValue>?
      if (coercing == null) {
        error("Cannot get coercing for '${typename}'")
      }
      coercing.parseLiteral(value)
    }
  }
}

internal fun scalarCoercingDeserialize(value: ExternalValue, coercings: Map<String, Coercing<*>>, typename: String): InternalValue {
  return when (typename) {
    "Int" -> {
      when(value) {
        is Int -> value
        is JsonNumber -> value.value.toInt()
        else -> error("Cannot deserialize '$value' to an Int")
      }
    }
    "Float" -> {
      when(value) {
        is Int -> value.toDouble()
        is Double -> value
        is JsonNumber -> value.value.toDouble()
        else -> error("Cannot deserialize '$value' to a Double")
      }
    }
    "String" -> {
      check(value is String)
      value
    }
    "Boolean" -> {
      check(value is Boolean)
      value
    }
    "ID" -> {
      when (value) {
        is String -> value
        is Int -> value.toString()
        is JsonNumber -> value.toString()
        else -> error("Cannot deserialize '$value' to an ID String")
      }
    }
    else -> {
      @Suppress("UNCHECKED_CAST")
      val coercing = coercings.get(typename) as Coercing<ExternalValue>?
      if (coercing == null) {
        error("Cannot get coercing for '${typename}'")
      }
      coercing.deserialize(value)
    }
  }
}
