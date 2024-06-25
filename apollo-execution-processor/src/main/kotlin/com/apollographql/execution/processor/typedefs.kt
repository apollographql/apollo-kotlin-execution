package com.apollographql.execution.processor

import com.apollographql.apollo3.ast.parseAsGQLValue
import com.apollographql.execution.processor.sir.Instantiation
import com.apollographql.execution.processor.sir.SirArgumentDefinition
import com.apollographql.execution.processor.sir.SirEnumDefinition
import com.apollographql.execution.processor.sir.SirEnumValueDefinition
import com.apollographql.execution.processor.sir.SirErrorType
import com.apollographql.execution.processor.sir.SirExecutionContextArgumentDefinition
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
import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier

internal fun getTypeDefinitions(
    logger: KSPLogger,
    scalarDefinitions: List<SirScalarDefinition>,
    query: KSClassDeclaration,
    mutation: KSClassDeclaration?,
    subscription: KSClassDeclaration?,
): List<SirTypeDefinition> {
  return TypeDefinitionContext(logger, scalarDefinitions).walk(query, mutation, subscription)
}

private class TypeDefinitionContext(val logger: KSPLogger, val scalarDefinitions: List<SirScalarDefinition>) {
  /**
   * key is qualifiedName
   */
  val scalars: Map<String, SirScalarDefinition?>

  /**
   * key is qualifiedName
   * null is a sentinel for a declaration that failed analysis and that we shouldn't try analysing again.
   */
  val typeDefinitions = mutableMapOf<String, SirTypeDefinition?>()

  val declarationsToVisit = mutableListOf<DeclarationToVisit>()

  init {
    /**
     * Build the mapping from qualifiedName -> scalar definition
     *
     * If the same aliased type is used by 2 different scalars it won't be possible to reference it in code
     */
    scalars = scalarDefinitions.associateBy { it.qualifiedName }
  }

  fun walk(query: KSClassDeclaration, mutation: KSClassDeclaration?, subscription: KSClassDeclaration?): List<SirTypeDefinition> {
    declarationsToVisit.add(DeclarationToVisit(query, VisitContext.OUTPUT, "query"))
    if (mutation != null) {
      declarationsToVisit.add(DeclarationToVisit(mutation, VisitContext.OUTPUT, "mutation"))
    }
    if (subscription != null) {
      declarationsToVisit.add(DeclarationToVisit(subscription, VisitContext.OUTPUT, "subscription"))
    }

    while (declarationsToVisit.isNotEmpty()) {
      val declarationToVisit = declarationsToVisit.removeFirst()
      val declaration = declarationToVisit.declaration
      val context = declarationToVisit.context

      val qualifiedName = declaration.asClassName().asString()
      if (typeDefinitions.containsKey(qualifiedName)) {
        // Already handled
        continue
      }

      val sirTypeDefinition = when {
        declaration.classKind == ClassKind.ENUM_CLASS -> {
          declaration.toSirEnumDefinition()
        }

        context == VisitContext.INPUT -> {
          declaration.toSirInputObject()
        }

        context == VisitContext.OUTPUT -> {
          declaration.toSirComposite(declarationToVisit.isoperationType)
        }

        else -> error("not reachable")
      }
      typeDefinitions.put(qualifiedName, sirTypeDefinition)
    }
    return this.typeDefinitions.values.filterNotNull().toList()
  }

  private fun KSClassDeclaration.toSirEnumDefinition(): SirEnumDefinition {
    val enumValueDefinitions = this.declarations.filterIsInstance<KSClassDeclaration>().filter {
      it.classKind == ClassKind.ENUM_ENTRY
    }.map {
      SirEnumValueDefinition(
          name = it.graphqlName(),
          description = it.docString,
          deprecationReason = it.deprecationReason(),
          className = it.asClassName()
      )
    }.toList()

    return SirEnumDefinition(
        name = graphqlName(),
        description = docString,
        qualifiedName = asClassName().asString(),
        targetClassName = asClassName(),
        values = enumValueDefinitions
    )
  }

  private fun KSClassDeclaration.toSirComposite(operationType: String?): SirTypeDefinition? {
    val propertyFields = getDeclaredProperties().filter {
      it.isPublic()
    }.mapNotNull {
      it.toSirFieldDefinition(operationType)
    }

    val functionFields = getDeclaredFunctions().filter {
      it.isPublic() && !it.isConstructor()
    }.mapNotNull {
      it.toSirFieldDefinition(operationType)
    }

    val allFields = propertyFields.toList() + functionFields.toList()

    val name = graphqlName()
    val description = docString
    val qualifiedName = asClassName().asString()

    return when {
      classKind == ClassKind.CLASS || classKind == ClassKind.OBJECT -> {
        if (modifiers.contains(Modifier.ABSTRACT)) {
          logger.error("Abstract classes are not supported", this)
          return null
        }
        SirObjectDefinition(
            name = name,
            description = description,
            qualifiedName = qualifiedName,
            interfaces = interfaces(),
            targetClassName = asClassName(),
            instantiation = instantiation(),
            operationType = operationType,
            fields = allFields
        )
      }

      classKind == ClassKind.INTERFACE -> {
        if (!modifiers.contains(Modifier.SEALED)) {
          logger.error("Interfaces and unions must be sealed interfaces", this)
          return null
        }

        val subclasses = getSealedSubclasses().map {
          // Look into subclasses
          declarationsToVisit.add(DeclarationToVisit(it, VisitContext.OUTPUT, null))
          it.graphqlName()
        }.toList()

        if (allFields.isEmpty()) {
          SirUnionDefinition(
              name = name,
              description = description,
              qualifiedName = qualifiedName,
              memberTypes = subclasses
          )
        } else {
          SirInterfaceDefinition(
              name = name,
              description = description,
              qualifiedName = qualifiedName,
              interfaces = interfaces(),
              fields = allFields
          )
        }
      }

      else -> {
        logger.error("Not a valid declaration", this)
        null
      }
    }
  }

  private fun KSClassDeclaration.interfaces(): List<String> {
    return getAllSuperTypes().mapNotNull {
      val declaration = it.declaration
      if (it.arguments.isNotEmpty()) {
        logger.error("Generic interfaces are not supported", this)
        null
      } else if (declaration is KSClassDeclaration) {
        if (declaration.asClassName().asString() == "kotlin.Any") {
          null
        } else if (declaration.containingFile == null) {
          logger.error("Class '${simpleName.asString()}' has a super class without a containing file that probably comes from a dependency.", this)
          null
        } else {
          declarationsToVisit.add(DeclarationToVisit(declaration, VisitContext.OUTPUT, null))
          declaration.graphqlName()
        }
      } else {
        logger.error("Unrecognized super class", this)
        null
      }
    }.toList()
  }

  private fun KSFunctionDeclaration.toSirFieldDefinition(operationType: String?): SirFieldDefinition? {
    if (returnType == null) {
      logger.error("No return type?", this)
      return null
    }
    val name = graphqlNameOrNull() ?: simpleName.asString()
    return SirFieldDefinition(
        name = name,
        description = docString,
        deprecationReason = deprecationReason(),
        targetName = simpleName.asString(),
        isFunction = true,
        type = returnType!!.resolve().toSirType(SirDebugContext(this), VisitContext.OUTPUT, operationType, false),
        arguments = parameters.mapNotNull {
          it.toSirArgument()
        }
    )
  }

  private fun KSAnnotated.defaultValue(): String? {
    val defaultValue = findAnnotation("GraphQLDefault")?.getArgumentValueAsString("value")
    if (defaultValue != null) {
      val result = defaultValue.parseAsGQLValue()
      if (result.issues.isNotEmpty()) {
        logger.error("@GraphQLDefault value is not a valid GraphQL literal: ${result.issues.first().message}", this)
        return null
      }
    }
    return defaultValue
  }
  private fun KSValueParameter.toSirArgument(): SirArgumentDefinition? {
    if (this.type.resolve().declaration.asClassName() == executionContextClassName) {
      return SirExecutionContextArgumentDefinition(name?.asString() ?: error(""))
    }
    val targetName = this.name!!.asString()
    val name = this.graphqlNameOrNull() ?: targetName

    if (this.hasDefault) {
      logger.error("Default arguments are not supported, use '@GraphQLDefault' instead.", this)
      return null
    }
    val defaultValue = defaultValue()
    val type = type.resolve()
    val sirType = type.toSirType(SirDebugContext(this), VisitContext.INPUT, operationType = null, defaultValue != null)

    return SirGraphQLArgumentDefinition(
        name = name,
        description = null,
        deprecationReason = deprecationReason(),
        targetName = targetName,
        type = sirType,
        defaultValue = defaultValue
    )
  }

  private fun KSPropertyDeclaration.toSirFieldDefinition(operationType: String?): SirFieldDefinition? {
    return SirFieldDefinition(
        name = graphqlName(),
        description = docString,
        deprecationReason = deprecationReason(),
        targetName = simpleName.asString(),
        isFunction = false,
        type = type.resolve().toSirType(SirDebugContext(this), VisitContext.OUTPUT, operationType, false),
        arguments = emptyList()
    )
  }

  private fun KSClassDeclaration.toSirInputObject(): SirInputObjectDefinition? {
    if (classKind != ClassKind.CLASS) {
      logger.error("Input objects must be classes", this)
      return null
    }

    val propertyNames = getDeclaredProperties().map { it.simpleName.asString() }
    val inputFields = primaryConstructor!!.parameters.map {
      val kotlinName =  it.name?.asString() ?: error("No name for constructor parameter")
      val name = it.graphqlNameOrNull() ?: kotlinName
      if (name.isReserved()) {
        logger.error("Name '$name' is reserved", it)
        return null
      }
      if (it.hasDefault) {
        logger.error("Default arguments are not supported, use '@GraphQLDefault' instead.", this)
        return null
      }
      if (!propertyNames.contains(it.name!!.asString())) {
        logger.error("Constructor parameter '$kotlinName' must also be declared as a `val` property.", this)
        return null
      }

      val declaration = it.type.resolve()
      val defaultValue = it.defaultValue()
      SirInputFieldDefinition(
          name = name,
          description = docString,
          deprecationReason = it.deprecationReason(),
          type = declaration.toSirType(SirDebugContext(it), VisitContext.INPUT, null, defaultValue != null),
          defaultValue = defaultValue
      )
    }

    return SirInputObjectDefinition(
        name = graphqlName(),
        description = docString,
        qualifiedName = asClassName().asString(),
        targetClassName = asClassName(),
        inputFields
    )
  }

  private fun KSType.toSirType(debugContext: SirDebugContext, context: VisitContext, operationType: String?, hasDefaultValue: Boolean): SirType {
    var type: KSType = this
    if (operationType == "subscription") {
      if (!declaration.isFlow()) {
        logger.error("Subscription root fields must be of Flow<T> type", this.declaration)
        return SirErrorType
      }
      type = arguments.single().type!!.resolve()
    } else if (context == VisitContext.INPUT) {
      if (declaration.isApolloOptional()) {
        val argumentType = type.arguments.first().type!!.resolve()

        if (hasDefaultValue) {
          logger.error("Input value has a default value and cannot be optional", debugContext.node)
          return SirErrorType
        }

        if (!argumentType.isMarkedNullable) {
          logger.error("Input value is not nullable and cannot be optional", debugContext.node)
          return SirErrorType
        }

        type = argumentType
      } else {
        if (!hasDefaultValue && isMarkedNullable) {
          /**
           * Inputs that are nullable and don't have a default value may be absent and must be modeled as such in Kotlin.
           *
           * Note that with variables, that value may be absent at runtime but this will be caught during coercion before it reaches the user code.
           */
          logger.error("Input value is nullable and doesn't have a default value: it must also be optional", debugContext.node)
          return SirErrorType
        }
      }
    }

    return type.toSirType2(debugContext, context, type.isMarkedNullable)
  }

  private fun KSType.toSirType2(debugContext: SirDebugContext, context: VisitContext, isNullable: Boolean): SirType {
    if (!isNullable) {
      return SirNonNullType(toSirType2(debugContext, context, true))
    }

    return when (val qualifiedName = declaration.asClassName().asString()) {
      "kotlin.collections.List" -> SirListType(arguments.single().type!!.resolve().toSirType(debugContext, context, null, false))
      else -> {
        if (scalars.containsKey(qualifiedName)) {
          val definition = scalars.get(qualifiedName)
          SirNamedType(definition!!.name)
        } else {
          val declaration = declaration
          if (declaration.isExternal()) {
            logger.error("'$qualifiedName' doesn't have a containing file and probably comes from a dependency (when analyzing '${debugContext.name}').", debugContext.node)
            SirErrorType
          } else if (unsupportedBasicTypes.contains(qualifiedName)) {
            logger.error("'$qualifiedName' is not a supported built-in type. Either use one of the built-in types (Boolean, String, Int, Double) or use a custom scalar.", declaration)
            SirErrorType
          } else if (declaration is KSClassDeclaration) {
            if (declaration.typeParameters.isNotEmpty()) {
              logger.error("Generic classes are not supported")
              SirErrorType
            } else {
              // Not a scalar, add to the list of types to visit
              declarationsToVisit.add(DeclarationToVisit(declaration, context))
              SirNamedType(declaration.graphqlName())
            }
          } else {
            logger.error("Unsupported type", declaration)
            SirErrorType
          }
        }
      }
    }
  }
}

private fun KSDeclaration.isApolloOptional(): Boolean {
  return asClassName().asString() == "com.apollographql.apollo3.api.Optional"
}

private fun KSDeclaration.isFlow(): Boolean {
  return asClassName().asString() == "kotlinx.coroutines.flow.Flow"
}


private fun KSDeclaration.isExternal(): Boolean {
  return (containingFile == null && !isApolloOptional())
}

private fun String.isReserved(): Boolean = this.startsWith("__")

private val unsupportedBasicTypes = listOf("Float", "Byte", "Short", "Long", "UByte", "UShort", "ULong", "Char").map {
  "kotlin.$it"
}.toSet()

private class DeclarationToVisit(val declaration: KSClassDeclaration, val context: VisitContext, val isoperationType: String? = null)
private enum class VisitContext {
  OUTPUT,
  INPUT,
}


private class SirDebugContext(
    val node: KSNode?,
    val name: String?,
) {
  constructor(parameter: KSValueParameter) : this(parameter.parent, parameter.name?.asString())
  constructor(property: KSPropertyDeclaration) : this(property.parentDeclaration, property.simpleName.asString())
  constructor(function: KSFunctionDeclaration) : this(function.parentDeclaration, function.simpleName.asString())
}

internal fun KSClassDeclaration.instantiation(): Instantiation {
  return when {
    classKind == ClassKind.OBJECT -> Instantiation.OBJECT
    hasNoArgsConstructor() -> Instantiation.NO_ARG_CONSTRUCTOR
    else -> Instantiation.UNKNOWN
  }
}