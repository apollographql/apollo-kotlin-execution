package com.apollographql.execution.processor

import com.apollographql.apollo.ast.*
import com.apollographql.execution.processor.sir.*
import com.apollographql.execution.processor.sir.Instantiation
import com.apollographql.execution.processor.sir.SirArgumentDefinition
import com.apollographql.execution.processor.sir.SirEnumDefinition
import com.apollographql.execution.processor.sir.SirEnumValueDefinition
import com.apollographql.execution.processor.sir.SirErrorType
import com.apollographql.execution.processor.sir.SirExecutionContextArgumentDefinition
import com.apollographql.execution.processor.sir.SirFieldDefinition
import com.apollographql.execution.processor.sir.SirInputObjectDefinition
import com.apollographql.execution.processor.sir.SirInputValueDefinition
import com.apollographql.execution.processor.sir.SirInterfaceDefinition
import com.apollographql.execution.processor.sir.SirListType
import com.apollographql.execution.processor.sir.SirNamedType
import com.apollographql.execution.processor.sir.SirNonNullType
import com.apollographql.execution.processor.sir.SirObjectDefinition
import com.apollographql.execution.processor.sir.SirType
import com.apollographql.execution.processor.sir.SirTypeDefinition
import com.apollographql.execution.processor.sir.SirUnionDefinition
import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.*

internal class TraversalResults(
  val definitions: List<SirDefinition>,
  val analyzedFiles: List<KSFile>
)

internal fun doTraversal(
  logger: KSPLogger,
  query: KSClassDeclaration,
  mutation: KSClassDeclaration?,
  subscription: KSClassDeclaration?,
): TraversalResults {
  return TypeDefinitionContext(logger).walk(query, mutation, subscription)
}

private class TypeDefinitionContext(
  val logger: KSPLogger,
) {
  /**
   * key is qualifiedName
   * null is a sentinel for a declaration that failed analysis and that we shouldn't try analysing again.
   */
  val typeDefinitions = mutableMapOf<String, SirTypeDefinition?>()

  /**
   * key is qualifiedName
   * null is a sentinel for a declaration that failed analysis and that we shouldn't try analysing again.
   */
  val directiveDefinitions = mutableMapOf<String, SirDirectiveDefinition?>()

  val ksFiles = mutableListOf<KSFile?>()

  /**
   * key is qualifiedName
   */
  val directiveLocations = mutableMapOf<String, List<GQLDirectiveLocation>>()

  /**
   * key is qualifiedName
   */
  val directiveRepeatable = mutableMapOf<String, Boolean>()

  val declarationsToVisit = mutableListOf<DeclarationToVisit>()


  fun walk(
    query: KSClassDeclaration,
    mutation: KSClassDeclaration?,
    subscription: KSClassDeclaration?,
  ): TraversalResults {
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
        // Already visited
        continue
      }
      if (builtinTypes.contains(qualifiedName)) {
        typeDefinitions.put(qualifiedName, builtinScalarDefinition(qualifiedName))
        continue
      }

      /**
       * Track the files
       */
      ksFiles.add(declaration.containingFile)

      if (declaration.typeParameters.isNotEmpty()) {
        logger.error("Generic classes are not supported")
        continue
      }
      if (unsupportedTypes.contains(qualifiedName)) {
        logger.error(
          "'$qualifiedName' is not a supported built-in type. Either use one of the built-in types (Boolean, String, Int, Double) or use a custom scalar.",
          declaration
        )
        typeDefinitions.put(qualifiedName, null)
        continue
      }
      if (declaration.isExternal()) {
        logger.error(
          "'$qualifiedName' doesn't have a containing file and probably comes from a dependency.",
          declaration
        )
        typeDefinitions.put(qualifiedName, null)
        continue
      }
      if (declaration is KSTypeAlias) {
        typeDefinitions.put(qualifiedName, declaration.toSirScalarDefinition(qualifiedName))
        continue
      }
      if (declaration !is KSClassDeclaration) {
        logger.error("Unsupported type", declaration)
        continue
      }
      if (declaration.classKind == ClassKind.ENUM_CLASS) {
        typeDefinitions.put(qualifiedName, declaration.toSirEnumDefinition())
        continue
      }
      if (declaration.findAnnotation("GraphQLScalar") != null) {
        typeDefinitions.put(qualifiedName, declaration.toSirScalarDefinition(qualifiedName))
        continue
      }
      if (context == VisitContext.INPUT) {
        typeDefinitions.put(qualifiedName, declaration.toSirInputObject())
        continue
      }
      if (context == VisitContext.OUTPUT) {
        typeDefinitions.put(qualifiedName, declaration.toSirComposite(declarationToVisit.isoperationType))
        continue
      }
    }

    val finalizedDirectiveDefinitions = directiveDefinitions.mapNotNull {
      if (it.value == null) {
        return@mapNotNull null
      }
      SirDirectiveDefinition(
        name = it.value!!.name,
        description = it.value!!.description,
        repeatable = directiveRepeatable.get(it.key) == true,
        locations = directiveLocations.get(it.key)!!,
        argumentDefinitions = it.value!!.argumentDefinitions
      )
    }

    return TraversalResults(
      definitions = finalizedDirectiveDefinitions + typeDefinitions.values.filterNotNull().toList(),
      analyzedFiles = ksFiles.filterNotNull()
    )
  }

  /**
   * Same code for both type aliases and classes
   */
  private fun KSDeclaration.toSirScalarDefinition(qualifiedName: String): SirScalarDefinition? {
    if (findAnnotation("GraphQLScalar") == null) {
      logger.error("Custom scalar type aliases must be annotated with @GraphQLScalar", this)
      return null
    }
    val name = graphqlName()

    val coercing = getCoercing(logger)
    if (coercing == null) {
      // Theoretically not possible
      logger.error("Custom scalar type aliases must define a coercing", this)
      return null
    }
    return SirScalarDefinition(
      name = name,
      description = docString,
      qualifiedName = qualifiedName,
      coercing = coercing,
      directives = directives(GQLDirectiveLocation.SCALAR)
    )
  }

  private fun KSClassDeclaration.toSirEnumDefinition(): SirEnumDefinition {
    val enumValueDefinitions = this.declarations.filterIsInstance<KSClassDeclaration>().filter {
      it.classKind == ClassKind.ENUM_ENTRY
    }.map {
      SirEnumValueDefinition(
        name = it.graphqlName(),
        description = it.docString,
        directives = directives(GQLDirectiveLocation.ENUM_VALUE),
        className = it.asClassName()
      )
    }.toList()

    return SirEnumDefinition(
      name = graphqlName(),
      description = docString,
      qualifiedName = asClassName().asString(),
      targetClassName = asClassName(),
      values = enumValueDefinitions,
      directives = directives(GQLDirectiveLocation.ENUM)
    )
  }

  private fun KSAnnotated.directives(location: GQLDirectiveLocation): List<SirDirective> {
    val sirDirectives = mutableListOf<SirDirective>()
    val qns = mutableListOf<String>()
    annotations.toList().forEach {
      // can we remove this resolve() somehow?
      val qn = it.annotationType.resolve().declaration.asClassName().asString()

      if (qn == "kotlin.Deprecated") {
        if (location in setOf(
            GQLDirectiveLocation.FIELD_DEFINITION,
            GQLDirectiveLocation.INPUT_FIELD_DEFINITION,
            GQLDirectiveLocation.ENUM_VALUE,
            GQLDirectiveLocation.ARGUMENT_DEFINITION
          )
        ) {
          val arguments = it.getArgumentValueAsString("reason")?.let {
            listOf(SirArgument("reason", GQLStringValue(null, it)))
          } ?: emptyList()

          sirDirectives.add(SirDirective("deprecated", arguments))
        }
        return@forEach
      }

      val directiveDefinition = if (directiveDefinitions.containsKey(qn)) {
        directiveDefinitions.get(qn)
      } else {
        // Eagerly build the directive definitions as we need the kotlin -> graphql argument mapping
        val declaration = it.annotationType.resolve().declaration
        ksFiles.add(declaration.containingFile)
        declaration.toSirDirectiveDefinition().also {
          directiveDefinitions.put(qn, it)
        }
      }
      if (directiveDefinition == null) {
        // Either the directive definition failed parsing or this annotation is not annotated with @GraphQLDirective
        return@forEach
      }

      qns.add(qn)
      sirDirectives.add(it.toSirDirective(directiveDefinition))
      directiveLocations.compute(qn) { _, v ->
        v.orEmpty() + location
      }
    }

    qns.groupBy { it }
      .filter { it.value.size > 1 }
      .forEach {
        directiveRepeatable.put(it.key, true)
      }

    return sirDirectives
  }

  private fun KSAnnotation.toSirDirective(directiveDefinition: SirDirectiveDefinition): SirDirective {
    return SirDirective(
      name = directiveDefinition.name,
      arguments = arguments.mapNotNull { it.toSirArgument(directiveDefinition) }
    )
  }

  private fun KSValueArgument.toSirArgument(directiveDefinition: SirDirectiveDefinition): SirArgument? {
    val kotlinName = name?.asString()
    if (kotlinName == null) {
      logger.error("Arguments must be named", this)
      return null
    }
    val graphQLName = directiveDefinition.argumentDefinitions.firstOrNull {
      it.kotlinName == kotlinName
    }?.name

    if (graphQLName == null) {
      logger.error("Cannot find a GraphQL argument for Kotlin parameter '$kotlinName'", this)
      return null
    }

    return SirArgument(
      name = graphQLName,
      value = value.toGQLValue()
    )
  }

  private fun Any?.toGQLValue(): GQLValue {
    return when (this) {
      null -> GQLNullValue(null)
      is String -> GQLStringValue(null, this)
      is Boolean -> GQLBooleanValue(null, this)
      is Int -> GQLIntValue(null, this.toString())
      is Double -> GQLFloatValue(null, this.toString())
      is Array<*> -> GQLListValue(null, this.map { it.toGQLValue() })
      is KSAnnotation -> {
        GQLObjectValue(
          null,
          arguments.map { GQLObjectField(null, it.name!!.asString(), it.value.toGQLValue()) }
        )
      }
      is KSType -> {
        GQLEnumValue(null, declaration.simpleName.asString())
      }
      else -> {
        logger.error("Cannot convert $this to a GQLValue")
        GQLNullValue(null) // not correct but compilation should fail anyway
      }
    }
  }

  private fun KSDeclaration.toSirComposite(operationType: String?): SirTypeDefinition? {
    if (this !is KSClassDeclaration) {
      logger.error("Cannot map to a GraphQL output type", this)
      return null
    }
    val propertyFields = getDeclaredProperties().filter {
      it.isPublic()
    }.map {
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

    return when (classKind) {
      ClassKind.CLASS, ClassKind.OBJECT -> {
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
          fields = allFields,
          directives = directives(GQLDirectiveLocation.OBJECT)
        )
      }

      ClassKind.INTERFACE -> {
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
            memberTypes = subclasses,
            directives = directives(GQLDirectiveLocation.UNION),
          )
        } else {
          SirInterfaceDefinition(
            name = name,
            description = description,
            qualifiedName = qualifiedName,
            interfaces = interfaces(),
            fields = allFields,
            directives = directives(GQLDirectiveLocation.INTERFACE),
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
          logger.error(
            "Class '${simpleName.asString()}' has a super class without a containing file that probably comes from a dependency.",
            this
          )
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
      directives = directives(GQLDirectiveLocation.FIELD_DEFINITION),
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
    val sirType =
      type.toSirType(SirDebugContext(this), VisitContext.INPUT, operationType = null, defaultValue != null)

    return SirInputValueDefinition(
      name = name,
      description = null,
      directives = directives(GQLDirectiveLocation.ARGUMENT_DEFINITION),
      kotlinName = targetName,
      type = sirType,
      defaultValue = defaultValue
    )
  }

  private fun KSPropertyDeclaration.toSirFieldDefinition(operationType: String?): SirFieldDefinition {
    return SirFieldDefinition(
      name = graphqlName(),
      description = docString,
      directives = directives(GQLDirectiveLocation.FIELD_DEFINITION),
      targetName = simpleName.asString(),
      isFunction = false,
      type = type.resolve().toSirType(SirDebugContext(this), VisitContext.OUTPUT, operationType, false),
      arguments = emptyList()
    )
  }

  /**
   * For directives and input objects input values
   *
   * @param directive for directives, we do not allow default values because using `@GraphQLDefault` only without a default value would not compile:
   *
   * ```kotlin
   * annotation class requiresOptIn(@GraphQLDefault("experimental") val feature: String = "this is both required and unused and super weird")
   * ```
   */
  private fun KSClassDeclaration.toSirInputValueDefinitions(directive: Boolean): List<SirInputValueDefinition> {
    val propertyNames = getDeclaredProperties().map { it.simpleName.asString() }
    return primaryConstructor!!.parameters.mapNotNull {
      val kotlinName = it.name?.asString() ?: error("No name for constructor parameter")
      val name = it.graphqlNameOrNull() ?: kotlinName
      if (name.isReserved()) {
        logger.error("Name '$name' is reserved", it)
        return@mapNotNull null
      }
      if (it.hasDefault) {
        logger.error("KSP cannot read default arguments, use '@GraphQLDefault' instead.", it)
        return@mapNotNull null
      }
      if (!propertyNames.contains(it.name!!.asString())) {
        logger.error("Constructor parameter '$kotlinName' must also be declared as a `val` property.", this)
        return@mapNotNull null
      }

      val declaration = it.type.resolve()
      val defaultValue = it.defaultValue()
      if (directive && defaultValue != null) {
        logger.error("Default values are not allowed in directive parameters.", this)
        return@mapNotNull null
      }
      SirInputValueDefinition(
        name = name,
        kotlinName = kotlinName,
        description = docString,
        directives = directives(GQLDirectiveLocation.ARGUMENT_DEFINITION),
        type = declaration.toSirType(SirDebugContext(it), VisitContext.INPUT, null, defaultValue != null),
        defaultValue = defaultValue
      )
    }
  }

  private fun KSDeclaration.toSirInputObject(): SirInputObjectDefinition? {
    if (this !is KSClassDeclaration) {
      logger.error("Input objects must be classes", this)
      return null
    }
    if (classKind != ClassKind.CLASS && classKind != ClassKind.ANNOTATION_CLASS) {
      logger.error("Input objects must be classes", this)
      return null
    }

    return SirInputObjectDefinition(
      name = graphqlName(),
      description = docString,
      qualifiedName = asClassName().asString(),
      targetClassName = asClassName(),
      inputFields = toSirInputValueDefinitions(false),
      directives = directives(GQLDirectiveLocation.INPUT_OBJECT)
    )
  }

  private fun KSType.toSirType(
    debugContext: SirDebugContext,
    context: VisitContext,
    operationType: String?,
    hasDefaultValue: Boolean
  ): SirType {
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
          logger.error(
            "Input value is nullable and doesn't have a default value: it must also be optional",
            debugContext.node
          )
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

    val qualifiedName = declaration.asClassName().asString()
    if (qualifiedName == "kotlin.collections.List") {
      return SirListType(
        arguments.single().type!!.resolve().toSirType(debugContext, context, null, false)
      )
    }

    return when {
      builtinTypes.contains(qualifiedName) -> {
        val name = builtinScalarName(qualifiedName)
        declarationsToVisit.add(DeclarationToVisit(declaration, context))
        SirNamedType(name)
      }

      else -> {
        val name = declaration.graphqlName()
        declarationsToVisit.add(DeclarationToVisit(declaration, context))
        SirNamedType(name)
      }
    }
  }

  private fun KSDeclaration.toSirDirectiveDefinition(): SirDirectiveDefinition? {
    if (containingFile == null) {
      // External annotation, ignore silently
      return null
    }
    if (findAnnotation("GraphQLDirective") == null) {
      // non-GraphQL directive, ignore silently
      return null
    }
    if (this !is KSClassDeclaration) {
      logger.error("GraphQLDirective can only be declared on annotation classes", this)
      return null
    }
    if (!this.modifiers.contains(Modifier.ANNOTATION)) {
      logger.error("GraphQLDirective can only be declared on annotation classes", this)
      return null

    }
    val name = this.graphqlName()

    return SirDirectiveDefinition(
      name = name,
      description = this.docString,
      repeatable = false,
      argumentDefinitions = toSirInputValueDefinitions(true),
      locations = emptyList()
    )
  }
}


private fun KSDeclaration.isApolloOptional(): Boolean {
  return asClassName().asString() == "com.apollographql.apollo.api.Optional"
}

private fun KSDeclaration.isFlow(): Boolean {
  return asClassName().asString() == "kotlinx.coroutines.flow.Flow"
}


private fun KSDeclaration.isExternal(): Boolean {
  return (containingFile == null && !isApolloOptional())
}

private fun String.isReserved(): Boolean = this.startsWith("__")

private val unsupportedTypes = listOf("Float", "Byte", "Short", "Long", "UByte", "UShort", "ULong", "Char").map {
  "kotlin.$it"
}.toSet()

private val builtinTypes = listOf("Double", "String", "Boolean", "Int").map {
  "kotlin.$it"
}.toSet()

private class DeclarationToVisit(
  val declaration: KSDeclaration,
  val context: VisitContext,
  val isoperationType: String? = null
)

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