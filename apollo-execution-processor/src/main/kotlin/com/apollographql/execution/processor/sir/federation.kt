package com.apollographql.execution.processor.sir

import com.apollographql.apollo.ast.GQLDirectiveLocation
import com.apollographql.execution.processor.codegen.codeBlock

/**
 * Adds federation types to the IR
 * The resulting IR cannot be used for types codegen. Only for schema generation.
 */
internal fun List<SirDefinition>.maybeFederate(): List<SirDefinition> {
  val entities = filterIsInstance<SirObjectDefinition>().filter { it.isEntity }
  if (entities.isEmpty()) {
    return this
  }

  return map { if (it is SirObjectDefinition && it.operationType == "query") { it.addEntitiesAndService() } else it } + federationDefinitions(entities.map { it.name })
}

internal fun SirObjectDefinition.addEntitiesAndService(): SirObjectDefinition {
  return SirObjectDefinition(
    name,
    description,
    qualifiedName,
    interfaces,
    targetClassName,
    instantiation,
    operationType,
    fields = listOf(
      SirFieldDefinition(
        name = "_entities",
        description = null,
        directives = emptyList(),
        targetName = "unused",
        isFunction = false,
        type = SirNonNullType(SirListType(SirNonNullType(SirNamedType("_Entity")))),
        arguments = listOf(
          SirInputValueDefinition(
            "representations",
            null,
            emptyList(),
            "unused",
            SirNonNullType(SirListType(SirNonNullType(SirNamedType("_Any")))),
            null
          )
        ),
        false
      ),
      SirFieldDefinition(
        name = "_service",
        description = null,
        directives = emptyList(),
        targetName = "unused",
        isFunction = false,
        type = SirNonNullType(SirNamedType("_Service")),
        arguments = emptyList(),
        false
      )
    ) + fields,
    directives,
    resolve,
  )
}

private fun federationDefinitions(entityNames: List<String>): List<SirDefinition> {
  return listOf(
    SirUnionDefinition(
      "_Entity",
      null,
      "unused",
      entityNames,
      emptyList()
    ),
    SirObjectDefinition(
      "_Service",
      null,
      "unused",
      emptyList(),
      SirClassName("unused", emptyList()),
      Instantiation.UNKNOWN,
      null,
      listOf(
        SirFieldDefinition(
          "sdl",
          null,
          emptyList(),
          "unused",
          false,
          SirNonNullType(SirNamedType("String")),
          emptyList(),
          false
        )
      ),
      emptyList(),
      null
    ),
    SirScalarDefinition(
      "_Any",
      "unused",
      null,
      SirCoercing(SirClassName("unused", emptyList()), Instantiation.UNKNOWN),
      emptyList()
    ),
    SirScalarDefinition(
      "_FieldSet",
      "unused",
      null,
      SirCoercing(SirClassName("unused", emptyList()), Instantiation.UNKNOWN),
      emptyList()
    ),
    SirDirectiveDefinition(
      "key",
      null,
      false,
      listOf(
        SirInputValueDefinition(
          "fields",
          null,
          emptyList(),
          "unused",
          SirNonNullType(SirNamedType("_FieldSet")),
          null,
        )
      ),
      listOf(GQLDirectiveLocation.OBJECT, GQLDirectiveLocation.INTERFACE)
    )
  )
}