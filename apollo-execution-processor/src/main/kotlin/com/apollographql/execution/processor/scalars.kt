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
    coercingDefinitions: List<SirCoercing>,
): List<SirScalarDefinition> {
  /**
   * key is the scalar name
   */
  val typeDefinitions = mutableMapOf<String, SirScalarDefinition>()

  declarations.forEach { declaration ->
    when (declaration) {
      is KSTypeAlias -> {
        val name = declaration.graphqlNameOrNull() ?: declaration.simpleName.asString()
        if (typeDefinitions.containsKey(name)) {
          logger.error("Scalar '$name' is defined multiple times", declaration)
          return@forEach
        }
        val qn = declaration.asClassName().asString()
        val coercing = coercingDefinitions.singleOrNull { it.scalarQualifiedName == qn }
        if (coercing == null) {
          logger.error("Cannot find a coercing for scalar '$name'", declaration)
          return@forEach
        }
        typeDefinitions.put(name, SirScalarDefinition(
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
        val coercing = coercingDefinitions.singleOrNull { it.scalarQualifiedName == qn }
        if (coercing == null) {
          logger.error("Cannot find a coercing for scalar '$name'", declaration)
          return@forEach
        }

        typeDefinitions.put(name, SirScalarDefinition(
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
                qn
              ),
          )
      )
    }
  }

  return typeDefinitions.values.toList()
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

private fun KSClassDeclaration.hasSuperType(qualifiedName: String): Boolean {
  return superTypes.any {
    it.resolve().declaration.asClassName().asString() == qualifiedName
  }
}

internal fun KSType.scalarQualifiedName(): String? {
  return when (this.declaration) {
    is KSClassDeclaration -> declaration.asClassName().asString()
    is KSTypeAlias -> declaration.asClassName().asString()
    else -> {
      null
    }
  }
}

internal fun getCoercingDefinitions(logger: KSPLogger, declarations: List<KSAnnotated>): List<SirCoercing> {
  val definitions = mutableMapOf<String, SirCoercing>()

  declarations.forEach { declaration ->
    if (declaration !is KSClassDeclaration) {
      logger.error("Coercing must be a class or object", declaration)
      return@forEach
    }

    val instantiation = declaration.instantiation()
    if (instantiation == Instantiation.UNKNOWN) {
      logger.error("Coercing implementation must either be objects or have a no-arg constructor", declaration)
      return@forEach
    }

    var scalarQualifiedName: String? = null
    var hasCoercingSuperType = false
    declaration.superTypes.forEach {
      val superDeclaration = it.resolve().declaration
      if (superDeclaration.asClassName().asString() == "com.apollographql.execution.Coercing") {
        val scalarType = it.element?.typeArguments?.singleOrNull()?.type?.resolve()
        scalarQualifiedName = scalarType?.scalarQualifiedName()
        if (scalarQualifiedName == null) {
          logger.error("Coercing must have a single type parameter", declaration)
        }
        hasCoercingSuperType = true
        return@forEach
      }
    }
    if (!hasCoercingSuperType) {
      logger.error("Coercing implementations must implement the com.apollographql.execution.Coercing interface", declaration)
      return@forEach
    }
    if (definitions.containsKey(scalarQualifiedName)) {
      logger.error("There is already a Coercing for type '$scalarQualifiedName'", declaration)
      return@forEach
    }
    definitions.put(scalarQualifiedName!!, SirCoercing(declaration.asClassName(), instantiation, scalarQualifiedName!!))
  }

  return definitions.values.toList()
}
