package com.apollographql.execution.processor

import com.apollographql.execution.processor.sir.Instantiation
import com.apollographql.execution.processor.sir.SirClassName
import com.apollographql.execution.processor.sir.SirCoercing
import com.apollographql.execution.processor.sir.SirScalarDefinition
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType

internal fun KSAnnotated.getCoercing(logger: KSPLogger): SirCoercing? {
  val annotation = findAnnotation("GraphQLScalar")
  if (annotation == null) {
    logger.error("scalars must be annotated with GraphQLScalar")
    return null
  }
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

  val isCoercing = declaration.superTypes.any {
    it.resolve().declaration.asClassName().asString() == "com.apollographql.execution.Coercing"
  }
  if (!isCoercing) {
    logger.error("Coercing must implement 'com.apollographql.execution.Coercing'", declaration)
    return null
  }

  val instantiation = declaration.instantiation()
  if (instantiation == Instantiation.UNKNOWN) {
    logger.error("Coercing implementation must either be objects or have a no-arg constructor", declaration)
    return null
  }

  return SirCoercing(declaration.asClassName(), instantiation)
}

internal fun builtinScalarName(qualifiedName: String): String {
  return when (qualifiedName) {
    "kotlin.String" -> "String"
    "kotlin.Boolean" -> "Boolean"
    "kotlin.Double" -> "Float"
    "kotlin.Int" -> "Int"
    // This is only called on built-in scalars so should not happen
    else -> error("")
  }
}
internal fun builtinScalarDefinition(qualifiedName: String): SirScalarDefinition {
  val name = builtinScalarName(qualifiedName)
  return SirScalarDefinition(
    name,
    qualifiedName = qualifiedName,
    description = null,
    coercing = SirCoercing(
      className = SirClassName(
        "com.apollographql.execution",
        listOf("${name}Coercing")
      ),
      instantiation = Instantiation.OBJECT,
    ),
    directives = emptyList()
  )
}