package com.apollographql.apollo3.execution.internal

import com.apollographql.apollo3.ast.GQLEnumTypeDefinition
import com.apollographql.apollo3.ast.GQLEnumValue
import com.apollographql.apollo3.ast.GQLInputObjectTypeDefinition
import com.apollographql.apollo3.ast.GQLInterfaceTypeDefinition
import com.apollographql.apollo3.ast.GQLListType
import com.apollographql.apollo3.ast.GQLNamedType
import com.apollographql.apollo3.ast.GQLNonNullType
import com.apollographql.apollo3.ast.GQLObjectTypeDefinition
import com.apollographql.apollo3.ast.GQLScalarTypeDefinition
import com.apollographql.apollo3.ast.GQLType
import com.apollographql.apollo3.ast.GQLUnionTypeDefinition
import com.apollographql.apollo3.ast.GQLValue
import com.apollographql.apollo3.ast.GQLVariableDefinition
import com.apollographql.apollo3.ast.Schema
import com.apollographql.apollo3.execution.Coercing
import com.apollographql.apollo3.execution.scalarCoercingDeserialize

internal fun coerceVariablesValues(
    schema: Schema,
    variableDefinitions: List<GQLVariableDefinition>,
    variables: Map<String, ExternalValue>,
    coercings: Map<String, Coercing<*>>,
): Map<String, InternalValue> {
  val coercedValues = mutableMapOf<String, InternalValue>()

  variableDefinitions.forEach { variableDefinition ->
    val hasValue = variables.containsKey(variableDefinition.name)
    if (!hasValue && variableDefinition.defaultValue != null) {
      /**
       * Sadly defaultValues are not coerced 😞
       * This is conceptually wrong but also what the spec is saying so this is what we want I guess
       * See https://github.com/graphql/graphql-spec/pull/793
       */
      coercedValues.put(variableDefinition.name, variableDefinition.defaultValue!!.toInternalValue())
      return@forEach
    }
    val value = variables.get(variableDefinition.name)
    if (variableDefinition.type is GQLNonNullType) {
      if (!hasValue) {
        error("No variable found for '${variableDefinition.name}'")
      }
      if (value == null) {
        error("'null' is not accepted for '${variableDefinition.name}'")
      }
    }
    if (hasValue) {
      if (value == null) {
        coercedValues.put(variableDefinition.name, null)
      } else {
        coercedValues.put(variableDefinition.name, coerceExternalToInternal(schema, value, variableDefinition.type, coercings))
      }
    }
  }

  return coercedValues
}

/**
 *
 */
private fun coerceExternalToInternal(schema: Schema, value: ExternalValue, type: GQLType, coercings: Map<String, Coercing<*>>): InternalValue {
  if (value == null) {
    check(type !is GQLNonNullType) {
      error("'null' found in non-null position")
    }

    return null
  }

  return when (type) {
    is GQLNonNullType -> {
      coerceExternalToInternal(schema, value, type.type, coercings)
    }

    is GQLListType -> {
      if (value is List<*>) {
        value.map { coerceExternalToInternal(schema, it, type.type, coercings) }
      } else {
        // Single items are mapped to a list of 1
        listOf(coerceExternalToInternal(schema, value, type.type, coercings))
      }
    }

    is GQLNamedType -> {
      val definition = schema.typeDefinition(type.name)
      when (definition) {
        is GQLEnumTypeDefinition -> {
          coerceEnumExternalToInternal(value = value, coercings = coercings, definition = definition)
        }

        is GQLInputObjectTypeDefinition -> {
          coerceInputObject(schema, definition, value, coercings)
        }

        is GQLInterfaceTypeDefinition,
        is GQLObjectTypeDefinition,
        is GQLUnionTypeDefinition,
        -> {
          error("Output type '${definition.name}' cannot be used in input position")
        }

        is GQLScalarTypeDefinition -> {
          scalarCoercingDeserialize(value, coercings, definition.name)
        }
      }
    }
  }
}

fun coerceEnumExternalToInternal(value: ExternalValue, coercings: Map<String, Coercing<*>>, definition: GQLEnumTypeDefinition): InternalValue {
  check(value is String) {
    error("Don't know how to coerce '$value' to a '${definition.name}' enum value")
  }

  val coercing = coercings.get(definition.name)

  return if (coercing == null) {
    check(definition.enumValues.any { it.name == value }) {
      val possibleValues = definition.enumValues.map { it.name }.toSet()
      "'$value' cannot be coerced to a '${definition.name}' enum value. Possible values are: '$possibleValues'"
    }
    value
  } else {
    coercing.deserialize(value)
  }
}


private fun coerceInputObject(schema: Schema, definition: GQLInputObjectTypeDefinition, externalValue: ExternalValue, coercings: Map<String, Coercing<*>>): InternalValue {
  if (externalValue !is Map<*, *>) {
    error("Don't know how to coerce '$externalValue' to a '${definition.name}' input object")
  }
  val map = definition.inputFields.mapNotNull { inputValueDefinition ->
    val inputFieldType = inputValueDefinition.type
    if (!externalValue.containsKey(inputValueDefinition.name)) {
      if (inputValueDefinition.defaultValue != null) {
        inputValueDefinition.name to inputValueDefinition.defaultValue!!.toInternalValue()
      } else {
        if (inputFieldType is GQLNonNullType) {
          error("Missing input field '${inputValueDefinition.name}")
        }
        // Skip this field
        null
      }
    } else {
      val inputFieldValue = externalValue.get(inputValueDefinition.name)
      inputValueDefinition.name to coerceExternalToInternal(schema, inputFieldValue, inputFieldType, coercings)
    }
  }.toMap()

  val coercing = coercings.get(definition.name)
  return if (coercing != null) {
    coercing.deserialize(map)
  } else {
    map
  }
}
