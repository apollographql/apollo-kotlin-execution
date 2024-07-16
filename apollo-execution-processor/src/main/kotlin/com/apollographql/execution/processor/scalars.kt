package com.apollographql.execution.processor

import com.apollographql.apollo.ast.GQLScalarTypeDefinition
import com.apollographql.apollo.ast.builtinDefinitions
import com.apollographql.execution.processor.sir.Instantiation
import com.apollographql.execution.processor.sir.SirClassName
import com.apollographql.execution.processor.sir.SirCoercing
import com.apollographql.execution.processor.sir.SirScalarDefinition
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias

/**
 * Returns a list of scalar. [SirScalarDefinition.name] is unique in the list. Duplicate scalar output an error
 */
internal fun getScalarDefinitions(
  logger: KSPLogger,
  declarations: List<KSAnnotated>,
): List<SirScalarDefinition> {
  /**
   * key is the scalar name
   */
  val typeDefinitions = mutableMapOf<String, SirScalarDefinition>()

  declarations.forEach { declaration ->
    val coercing = declaration.getCoercing(logger)
    if (coercing == null) {
      return@forEach
    }
    when (declaration) {
      is KSTypeAlias -> {
        val name = declaration.graphqlNameOrNull() ?: declaration.simpleName.asString()
        if (typeDefinitions.containsKey(name)) {
          logger.error("Scalar '$name' is defined multiple times", declaration)
          return@forEach
        }
        val qn = declaration.asClassName().asString()
        typeDefinitions.put(
          name, SirScalarDefinition(
            name = name,
            description = declaration.docString,
            qualifiedName = qn,
            coercing = coercing
          )
        )
      }

      is KSClassDeclaration -> {
        val name = declaration.graphqlNameOrNull() ?: declaration.simpleName.asString()
        if (typeDefinitions.containsKey(name)) {
          logger.error("Scalar '$name' is defined multiple times", declaration)
          return@forEach
        }
        val qn = declaration.asClassName().asString()

        typeDefinitions.put(
          name, SirScalarDefinition(
            name = name,
            description = declaration.docString,
            qualifiedName = qn,
            coercing = coercing
          )
        )
      }
    }
  }

  val builtInScalars = builtinDefinitions().filterIsInstance<GQLScalarTypeDefinition>().map { it.name }.toSet()

  builtInScalars.forEach {
    if (!typeDefinitions.containsKey(it)) {
      val qn = it.toQualifiedName()
      val coercingName = it
      typeDefinitions.put(
        it,
        SirScalarDefinition(
          it,
          qualifiedName = qn,
          description = null,
          coercing = SirCoercing(
            className = SirClassName(
              "com.apollographql.execution",
              listOf("${coercingName}Coercing")
            ),
            instantiation = Instantiation.OBJECT,
          ),
        )
      )
    }
  }

  return typeDefinitions.values.toList()
}

private fun KSAnnotated.getCoercing(logger: KSPLogger): SirCoercing? {
  val annotation = annotations.first { it.shortName.asString() == "GraphQLScalar" }
  val coercing = annotation.arguments.first().value
  if (coercing !is KSType) {
    logger.error("coercing must be a type")
    return null
  }

  val declaration = coercing.declaration
  if (declaration !is KSClassDeclaration) {
    logger.error("Coercing must be a class or object", declaration)
    return null
  }

  val instantiation = declaration.instantiation()
  if (instantiation == Instantiation.UNKNOWN) {
    logger.error("Coercing implementation must either be objects or have a no-arg constructor", declaration)
    return null
  }

  return SirCoercing(declaration.asClassName(), instantiation)
}

private fun String.toQualifiedName(): String {
  return when (this) {
    "Int" -> "kotlin.Int"
    "String" -> "kotlin.String"
    "Float" -> "kotlin.Double"
    "Boolean" -> "kotlin.Boolean"
    "ID" -> "Apollo Kotlin Execution doesn't come with a built-in ID type, please define your own"
    // This is only called on built-in scalars so should not happen
    else -> error("")
  }
}

