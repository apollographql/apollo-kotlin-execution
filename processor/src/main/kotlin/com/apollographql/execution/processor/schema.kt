package com.apollographql.execution.processor

import com.apollographql.apollo3.ast.GQLArgument
import com.apollographql.apollo3.ast.GQLDirective
import com.apollographql.apollo3.ast.GQLDocument
import com.apollographql.apollo3.ast.GQLEnumTypeDefinition
import com.apollographql.apollo3.ast.GQLEnumValueDefinition
import com.apollographql.apollo3.ast.GQLFieldDefinition
import com.apollographql.apollo3.ast.GQLInputObjectTypeDefinition
import com.apollographql.apollo3.ast.GQLInputValueDefinition
import com.apollographql.apollo3.ast.GQLInterfaceTypeDefinition
import com.apollographql.apollo3.ast.GQLListType
import com.apollographql.apollo3.ast.GQLNamedType
import com.apollographql.apollo3.ast.GQLNonNullType
import com.apollographql.apollo3.ast.GQLObjectTypeDefinition
import com.apollographql.apollo3.ast.GQLOperationTypeDefinition
import com.apollographql.apollo3.ast.GQLScalarTypeDefinition
import com.apollographql.apollo3.ast.GQLSchemaDefinition
import com.apollographql.apollo3.ast.GQLStringValue
import com.apollographql.apollo3.ast.GQLType
import com.apollographql.apollo3.ast.GQLTypeDefinition
import com.apollographql.apollo3.ast.GQLUnionTypeDefinition
import com.apollographql.apollo3.ast.GQLValue
import com.apollographql.apollo3.ast.parseAsGQLValue
import com.apollographql.apollo3.ast.toSDL
import com.apollographql.execution.processor.sir.SirEnumDefinition
import com.apollographql.execution.processor.sir.SirEnumValueDefinition
import com.apollographql.execution.processor.sir.SirErrorType
import com.apollographql.execution.processor.sir.SirFieldDefinition
import com.apollographql.execution.processor.sir.SirGraphQLArgumentDefinition
import com.apollographql.execution.processor.sir.SirInputFieldDefinition
import com.apollographql.execution.processor.sir.SirInputObjectDefinition
import com.apollographql.execution.processor.sir.SirInterfaceDefinition
import com.apollographql.execution.processor.sir.SirListType
import com.apollographql.execution.processor.sir.SirNamedType
import com.apollographql.execution.processor.sir.SirNonNullType
import com.apollographql.execution.processor.sir.SirObjectDefinition
import com.apollographql.execution.processor.sir.SirScalarDefinition
import com.apollographql.execution.processor.sir.SirType
import com.apollographql.execution.processor.sir.SirTypeDefinition
import com.apollographql.execution.processor.sir.SirUnionDefinition
import okio.Buffer

internal fun schemaString(typeDefinitions: List<SirTypeDefinition>): String {
  val schemaDefinition = GQLSchemaDefinition(
      sourceLocation = null,
      description = null,
      directives = emptyList(),
      rootOperationTypeDefinitions = listOf("query", "mutation", "subscription").mapNotNull { operationType ->
        typeDefinitions.filterIsInstance<SirObjectDefinition>()
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
      definitions = listOf(schemaDefinition) + typeDefinitions.map { it.toGQL() },
      sourceLocation = null
  ).toSDL("  ")
}

private fun SirTypeDefinition.toGQL(): GQLTypeDefinition {
  return when (this) {
    is SirEnumDefinition -> GQLEnumTypeDefinition(
        sourceLocation = null,
        description = description,
        name = name,
        directives = emptyList(),
        enumValues = this.values.map {
          it.toGQL()
        }
    )

    is SirInputObjectDefinition -> GQLInputObjectTypeDefinition(
        sourceLocation = null,
        description = description,
        name = name,
        directives = emptyList(),
        inputFields = this.inputFields.map {
          it.toGQL()
        }
    )
    is SirInterfaceDefinition -> GQLInterfaceTypeDefinition(
        sourceLocation = null,
        description = description,
        name = name,
        directives = emptyList(),
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
        directives = emptyList(),
        fields = this.fields.map {
          it.toGQL()
        }
    )
    is SirScalarDefinition -> GQLScalarTypeDefinition(
        sourceLocation = null,
        description = description,
        name = name,
        directives = emptyList()
    )
    is SirUnionDefinition -> GQLUnionTypeDefinition(
        sourceLocation = null,
        description = description,
        name = name,
        directives = emptyList(),
        memberTypes = memberTypes.map { GQLNamedType(null, it) }
    )
  }
}

private fun SirFieldDefinition.toGQL(): GQLFieldDefinition {
  return GQLFieldDefinition(
      sourceLocation = null,
      description = description,
      directives = deprecationReason.toGQLDirectives(),
      name = name,
      arguments = arguments.filterIsInstance<SirGraphQLArgumentDefinition>().map { it.toGQL() },
      type = type.toGQL()
  )
}

private fun SirGraphQLArgumentDefinition.toGQL(): GQLInputValueDefinition {
  return GQLInputValueDefinition(
      sourceLocation = null,
      description = description,
      directives = deprecationReason.toGQLDirectives(),
      name = name,
      type = type.toGQL(),
      defaultValue = defaultValue.toGQLDefaultValue()
  )
}

private fun SirInputFieldDefinition.toGQL(): GQLInputValueDefinition {
  return GQLInputValueDefinition(
      sourceLocation = null,
      description = description,
      name = name,
      directives = deprecationReason.toGQLDirectives(),
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
  return when(this) {
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
      directives = deprecationReason.toGQLDirectives(),
      name = name
  )
}

private fun String?.toGQLDirectives(): List<GQLDirective> {
  if (this == null) {
    return emptyList()
  }
  return listOf(
      GQLDirective(
          sourceLocation = null,
          name = "deprecated",
          arguments = listOf(
              GQLArgument(
                  sourceLocation = null,
                  name = "reason",
                  value = GQLStringValue(null, this)
              )
          )
      )
  )
}
