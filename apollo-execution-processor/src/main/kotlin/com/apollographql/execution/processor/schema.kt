package com.apollographql.execution.processor

import com.apollographql.apollo.ast.*
import com.apollographql.execution.processor.sir.*
import okio.Buffer

internal fun schemaString(definitions: List<SirDefinition>): String {
  val schemaDefinition = GQLSchemaDefinition(
    sourceLocation = null,
    description = null,
    directives = emptyList(),
    rootOperationTypeDefinitions = listOf("query", "mutation", "subscription").mapNotNull { operationType ->
      definitions.filterIsInstance<SirObjectDefinition>()
        .firstOrNull {
          it.operationType == operationType
        }?.name
        ?.let {
          GQLOperationTypeDefinition(
            null,
            operationType,
            it
          )
        }
    }
  )

  return GQLDocument(
    definitions = listOf(schemaDefinition) + definitions.map { it.toGQL() },
    sourceLocation = null
  ).toSDL("  ")
}

private fun SirDefinition.toGQL(): GQLDefinition {
  return when (this) {
    is SirEnumDefinition -> GQLEnumTypeDefinition(
      sourceLocation = null,
      description = description,
      name = name,
      directives = directives.map { it.toGQL() },
      enumValues = this.values.map {
        it.toGQL()
      }
    )

    is SirInputObjectDefinition -> GQLInputObjectTypeDefinition(
      sourceLocation = null,
      description = description,
      name = name,
      directives = directives.map { it.toGQL() },
      inputFields = this.inputFields.map {
        it.toGQL()
      }
    )

    is SirInterfaceDefinition -> GQLInterfaceTypeDefinition(
      sourceLocation = null,
      description = description,
      name = name,
      directives = directives.map { it.toGQL() },
      implementsInterfaces = interfaces,
      fields = this.fields.map {
        it.toGQL()
      }
    )

    is SirObjectDefinition -> GQLObjectTypeDefinition(
      sourceLocation = null,
      description = description,
      name = name,
      implementsInterfaces = interfaces,
      directives = directives.map { it.toGQL() },
      fields = this.fields.map {
        it.toGQL()
      }
    )

    is SirScalarDefinition -> GQLScalarTypeDefinition(
      sourceLocation = null,
      description = description,
      name = name,
      directives = directives.map { it.toGQL() },
    )

    is SirUnionDefinition -> GQLUnionTypeDefinition(
      sourceLocation = null,
      description = description,
      name = name,
      directives = directives.map { it.toGQL() },
      memberTypes = memberTypes.map { GQLNamedType(null, it) }
    )

    is SirDirectiveDefinition -> GQLDirectiveDefinition(
      sourceLocation = null,
      description = description,
      name = name,
      arguments = argumentDefinitions.map { it.toGQL() },
      repeatable = repeatable,
      locations = locations
    )
  }
}

private fun SirDirective.toGQL(): GQLDirective {
  return GQLDirective(
    sourceLocation = null,
    name = name,
    arguments = arguments.map {
      GQLArgument(
        sourceLocation = null,
        name = it.name,
        value = it.value
      )
    }
  )
}

private fun SirFieldDefinition.toGQL(): GQLFieldDefinition {
  return GQLFieldDefinition(
    sourceLocation = null,
    description = description,
    directives = directives.map { it.toGQL() },
    name = name,
    arguments = arguments.filterIsInstance<SirInputValueDefinition>().map { it.toGQL() },
    type = type.toGQL()
  )
}

private fun SirInputValueDefinition.toGQL(): GQLInputValueDefinition {
  return GQLInputValueDefinition(
    sourceLocation = null,
    description = description,
    directives = directives.map { it.toGQL() },
    name = name,
    type = type.toGQL(),
    defaultValue = defaultValue.toGQLDefaultValue()
  )
}

private fun String?.toGQLDefaultValue(): GQLValue? {
  if (this == null) {
    return null
  }
  return Buffer().writeUtf8(this).parseAsGQLValue().value
}


private fun SirType.toGQL(): GQLType {
  return when (this) {
    SirErrorType -> GQLNamedType(null, "APOLLO_ERROR")
    is SirListType -> GQLListType(null, type.toGQL())
    is SirNamedType -> GQLNamedType(null, name = name)
    is SirNonNullType -> GQLNonNullType(null, type.toGQL())
  }
}


private fun SirEnumValueDefinition.toGQL(): GQLEnumValueDefinition {
  return GQLEnumValueDefinition(
    sourceLocation = null,
    description = description,
    directives = directives.map { it.toGQL() },
    name = name
  )
}

