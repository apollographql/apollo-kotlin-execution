package com.apollographql.execution.processor

import com.apollographql.execution.processor.codegen.KotlinSymbols
import com.apollographql.execution.processor.sir.Instantiation
import com.apollographql.execution.processor.sir.SirCoercing
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
    it.resolve().declaration.asClassName().asString() == KotlinSymbols.Coercing.canonicalName
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

/**
 * Those types are always hardwired and can't be remapped.
 *
 * This is because introspection uses `String` and `Double` and changing the coercing and kotlin types would confuse the execution engine.
 * We could technically allow users to remap the GraphQL "Int" and "Float" types if we really wanted to, but we enforce a hard wired
 * mapping for those scalars for consistency.
 * ID can (and should) be declared by the user because there is no native `ID` type in Kotlin.
 */
internal val graphqlScalars = mapOf(
  "kotlin.String" to "String",
  "kotlin.Boolean" to "Boolean",
  "kotlin.Double" to "Float",
  "kotlin.Int" to "Int",
)

