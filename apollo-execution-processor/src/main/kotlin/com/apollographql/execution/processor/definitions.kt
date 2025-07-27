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

  val entities = mutableSetOf<String>()
  val usedDirectiveNames = mutableSetOf<String>()

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

  val usedTypeNames = mutableSetOf<String>()
  val unions = mutableMapOf<String, Set<String>>()

  /**
   * Walk the Kotlin type graph. It goes:
   * - depth first for supertypes so we can filter out union markers
   * - breadth first for subtypes/fields so we don't loop on circular field/interfaces references
   */
  fun walk(
    query: KSClassDeclaration,
    mutation: KSClassDeclaration?,
    subscription: KSClassDeclaration?,
  ): TraversalResults {
    declarationsToVisit.add(
      DeclarationToVisit(
        query,
        DeclarationContext(operationType = "query", direction = Direction.Ouput)
      )
    )
    if (mutation != null) {
      declarationsToVisit.add(
        DeclarationToVisit(
          mutation,
          DeclarationContext(operationType = "mutation", direction = Direction.Ouput)
        )
      )
    }
    if (subscription != null) {
      declarationsToVisit.add(
        DeclarationToVisit(
          subscription,
          DeclarationContext(operationType = "subscription", direction = Direction.Ouput)
        )
      )
    }

    while (declarationsToVisit.isNotEmpty()) {
      val declarationToVisit = declarationsToVisit.removeFirst()
      getOrResolve(declarationToVisit)
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
      /**
       * Not 100% sure what order to use for the types.
       * Fields in source order make sense but for classes that may be defined in different files, it's a lot less clear
       */
      definitions = typeDefinitions.patchUnions(unions)
        .sortedBy { it.type() + it.name } + finalizedDirectiveDefinitions.sortedBy { it.name },
      analyzedFiles = ksFiles.filterNotNull()
    )
  }

  private fun getOrResolve(declarationToVisit: DeclarationToVisit): SirTypeDefinition? {
    val qualifiedName = declarationToVisit.declaration.asClassName().asString()
    if (typeDefinitions.containsKey(qualifiedName)) {
      // Already visited (maybe error)
      return typeDefinitions.get(qualifiedName)
    }

    val typeDefinition = resolveType(qualifiedName, declarationToVisit)
    typeDefinitions.put(qualifiedName, typeDefinition)
    return typeDefinition
  }

  /**
   * If returning null, this function also logs an error to fail the processor.
   *
   * @return the definition or null if there was an error
   */
  private fun resolveType(qualifiedName: String, declarationToVisit: DeclarationToVisit): SirTypeDefinition? {
    val declaration = declarationToVisit.declaration
    val context = declarationToVisit.context

    if (builtinTypes.contains(qualifiedName)) {
      return builtinScalarDefinition(qualifiedName)
    }
    if (unsupportedTypes.contains(qualifiedName)) {
      logger.error(
        "'$qualifiedName' is not a supported built-in type. Either use one of the built-in types (Boolean, String, Int, Double) or use a custom scalar.",
        declaration
      )
      return null
    }
    if (declaration.containingFile == null) {
      if (qualifiedName == "kotlin.Unit") {
        logger.error(
          "'kotlin.Unit' is not support as an output type as there is no void scalar in GraphQL. Either add a custom scalar or return another built in type such as 'Boolean' or 'Int'.",
          context.origin
        )

      } else {
        logger.error(
          "Symbol '$qualifiedName' isn't part of your project. Did you forget to map a custom scalar?",
          context.origin
        )
      }
      return null
    }

    /**
     * Track the files
     */
    ksFiles.add(declaration.containingFile)

    val name = declaration.graphqlName()
    if (usedTypeNames.contains(name)) {
      logger.error("Duplicate type '$name'. Either rename the declaration or use @GraphQLName.", declaration)
      return null
    }
    usedTypeNames.add(name)

    if (declaration.typeParameters.isNotEmpty()) {
      logger.error("Generic classes are not supported")
      return null
    }

    if (declaration is KSTypeAlias) {
      return declaration.toSirScalarDefinition(qualifiedName)
    }
    if (declaration !is KSClassDeclaration) {
      logger.error("Unsupported type", declaration)
      return null
    }
    if (declaration.classKind == ClassKind.ENUM_CLASS) {
      return declaration.toSirEnumDefinition()
    }
    if (declaration.findAnnotation("GraphQLScalar") != null) {
      return declaration.toSirScalarDefinition(qualifiedName)
    }

    return when (context.direction) {
      Direction.Ouput -> declaration.toSirComposite(context.operationType)
      Direction.Input -> declaration.toSirInputObject()
    }
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
          val arguments = it.getArgumentValueAsString("message")?.let {
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

        val name = declaration.graphqlName()
        if (usedDirectiveNames.contains(name)) {
          logger.error("Duplicate directive '$name'. Either rename the declaration or use @GraphQLName.", it)
          return@forEach
        }
        usedDirectiveNames.add(name)

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
      arguments = arguments.mapNotNull { it.sirInputValueDefinition(directiveDefinition) }
    )
  }

  private fun KSValueArgument.sirInputValueDefinition(directiveDefinition: SirDirectiveDefinition): SirArgument? {
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
      value = value.toGQLValue(this)
    )
  }

  private fun Any?.toGQLValue(argument: KSValueArgument): GQLValue {
    return when (this) {
      null -> GQLNullValue(null)
      is String -> GQLStringValue(null, this)
      is Boolean -> GQLBooleanValue(null, this)
      is Int -> GQLIntValue(null, this.toString())
      is Double -> GQLFloatValue(null, this.toString())
      is Array<*> -> GQLListValue(null, this.map { it.toGQLValue(argument) })
      is KSAnnotation -> {
        GQLObjectValue(
          null,
          arguments.map { GQLObjectField(null, it.name!!.asString(), it.value.toGQLValue(argument)) }
        )
      }

      is KSType -> {
        /**
         * Not sure if this is still used. This might be a leftover of KSP1
         */
        GQLEnumValue(null, declaration.simpleName.asString())
      }

      is KSClassDeclaration -> {
        if (this.classKind != ClassKind.ENUM_ENTRY) {
          logger.error("Cannot convert class $this to a GQLValue", argument)
          GQLNullValue(null) // not correct but compilation should fail anyway
        } else {
          GQLEnumValue(null, simpleName.asString())
        }
      }

      else -> {
        logger.error("Cannot convert $this to a GQLValue", argument)
        GQLNullValue(null) // not correct but compilation should fail anyway
      }
    }
  }

  private fun KSDeclaration.toSirComposite(operationType: String?): SirTypeDefinition? {
    if (this !is KSClassDeclaration) {
      logger.error("Cannot map to a GraphQL output type", this)
      return null
    }

    val usedNames = mutableSetOf<String>()
    val allFields = declarations.filter { it.isPublic() }.mapNotNull {
      if (it.origin == Origin.SYNTHETIC) {
        /**
         * This happens for data classes functions, which we do not want to track
         */
        return@mapNotNull null
      }
      val name = it.graphqlName()
      if (usedNames.contains(name)) {
        logger.error("Duplicate field '$name'. Either rename the declaration or use @GraphQLName.", it)
        return@mapNotNull null
      }
      usedNames.add(name)

      when (it) {
        is KSPropertyDeclaration -> {
          it.toSirFieldDefinition(operationType == "subscription")
        }

        is KSFunctionDeclaration -> {
          if (it.isConstructor()) {
            null
          } else {
            it.toSirFieldDefinition(operationType == "subscription")
          }
        }

        else -> null
      }
    }.toList()

    val name = graphqlName()
    val description = docString
    val qualifiedName = asClassName().asString()

    return when (classKind) {
      ClassKind.CLASS, ClassKind.OBJECT -> {
        if (modifiers.contains(Modifier.ABSTRACT)) {
          logger.error("Abstract classes are not supported", this)
          return null
        }
        val federationDirectives = allFields.keyDirective()?.let { listOf(it) }.orEmpty()
        if (federationDirectives.isNotEmpty()) {
          entities.add(name)
        }
        SirObjectDefinition(
          name = name,
          description = description,
          qualifiedName = qualifiedName,
          interfaces = interfaces(name),
          targetClassName = asClassName(),
          instantiation = instantiation(),
          operationType = operationType,
          fields = allFields,
          directives = directives(GQLDirectiveLocation.OBJECT) + federationDirectives,
          resolve = entityResolver(logger, allFields.filter { it.isKey }.map { it.name }.toSet())
        )
      }

      ClassKind.INTERFACE -> {
        if (!modifiers.contains(Modifier.SEALED)) {
          logger.error("Interfaces and unions must be sealed interfaces", this)
          return null
        }

        getSealedSubclasses().forEach {
          /**
           * We go depth first on the superclasses but need to escape the callstack and
           * remember to also go the other direction to not miss anything from the graph.
           *
           * If we were to go depth first only, we would miss all the concrete animal types
           * below:
           *
           * ```graphql
           * type Query {
           *   animal: Animal
           * }
           *
           * union Animal = Cat | Dog | Lion ...
           * ```
           *
           * Note that technically the subscription root might implement an interface, but it was
           * visited already when we reach this point so it's fine to hardcode `isSubscriptionRoot = false` here.
           */
          declarationsToVisit.add(
            DeclarationToVisit(
              it,
              DeclarationContext(
                direction = Direction.Ouput,
                // The subclasses are always part of the project so we should not need some origin information here.
                origin = null
              )
            )
          )
        }

        if (allFields.isEmpty()) {
          SirUnionDefinition(
            name = name,
            description = description,
            qualifiedName = qualifiedName,
            memberTypes = emptyList(), // we'll patch that later
            directives = directives(GQLDirectiveLocation.UNION),
          )
        } else {
          if (allFields.any { it.isKey }) {
            logger.error("@GraphQLKey is not supported on interface fields")
          }
          SirInterfaceDefinition(
            name = name,
            description = description,
            qualifiedName = qualifiedName,
            interfaces = interfaces(null),
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

  private fun List<SirFieldDefinition>.keyDirective(): SirDirective? {
    val fields = filter { it.isKey }.map { it.name }.joinToString(" ")
    if (fields.isBlank()) {
      return null
    }

    return SirDirective(
      name = "key",
      arguments = listOf(SirArgument(name = "fields", value = GQLStringValue(null, fields)))
    )
  }

  private fun KSClassDeclaration.interfaces(objectName: String?): List<String> {
    return getAllSuperTypes().mapNotNull {
      val declaration = it.declaration
      if (it.arguments.isNotEmpty()) {
        logger.error("Generic interfaces are not supported", this)
        null
      } else if (declaration is KSClassDeclaration) {
        if (declaration.asClassName().asString() == "kotlin.Any") {
          // kotlin.Any is a super type of everything, just ignore it
          null
        } else {
          val supertype = getOrResolve(
            DeclarationToVisit(
              declaration,
              DeclarationContext(
                direction = Direction.Ouput,
                // TODO: use the exact KSTypeReference in superTypes() as origin for nicer error messages instead of just the class.
                origin = this
              )
            )
          )
          when (supertype) {
            is SirInterfaceDefinition -> {
              supertype.name
            }

            is SirUnionDefinition -> {
              if (objectName == null) {
                logger.error("Interfaces are not allowed to extend union markers. Only classes can")
              } else {
                unions.compute(supertype.name) { _, oldValue ->
                  oldValue.orEmpty() + objectName
                }
              }
              null
            }

            else -> {
              // error
              null
            }
          }
        }
      } else {
        logger.error("Unrecognized super class", this)
        null
      }
    }.toList()
  }

  private fun KSFunctionDeclaration.toSirFieldDefinition(isSubscriptionRoot: Boolean): SirFieldDefinition? {
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
      type = returnType!!.resolve().toSirType(
        SirContext(
          direction = Direction.Ouput,
          origin = this,
          isSubscriptionRoot = isSubscriptionRoot,
          hasDefaultValue = false
        )
      ),
      arguments = parameters.mapNotNull {
        it.toSirArgument()
      },
      isKey = findAnnotation("GraphQLKey") != null
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
      logger.error(
        "Default parameter values are not supported, annotate your parameter with '@GraphQLDefault' instead.",
        this
      )
      return null
    }
    val defaultValue = defaultValue()
    val type = type.resolve()
    val sirType = type.toSirType(
      SirContext(
        direction = Direction.Input,
        origin = this,
        isSubscriptionRoot = false,
        hasDefaultValue = defaultValue != null
      )
    )

    return SirInputValueDefinition(
      name = name,
      description = null,
      directives = directives(GQLDirectiveLocation.ARGUMENT_DEFINITION),
      kotlinName = targetName,
      type = sirType,
      defaultValue = defaultValue
    )
  }

  private fun KSPropertyDeclaration.toSirFieldDefinition(isSubscriptionRoot: Boolean): SirFieldDefinition {
    return SirFieldDefinition(
      name = graphqlName(),
      description = docString,
      directives = directives(GQLDirectiveLocation.FIELD_DEFINITION),
      targetName = simpleName.asString(),
      isFunction = false,
      type = type.resolve().toSirType(
        SirContext(
          direction = Direction.Ouput,
          origin = this,
          isSubscriptionRoot = isSubscriptionRoot,
          hasDefaultValue = false
        )
      ),
      arguments = emptyList(),
      isKey = findAnnotation("GraphQLKey") != null
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
        type = declaration.toSirType(
          SirContext(
            direction = Direction.Input,
            origin = it,
            isSubscriptionRoot = false,
            hasDefaultValue = defaultValue != null
          )
        ),
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
    context: SirContext,
  ): SirType {
    var type: KSType = this
    if (context.direction == Direction.Ouput && context.isSubscriptionRoot) {
      if (!declaration.isFlow()) {
        logger.error("Subscription root fields must be of Flow<T> type", this.declaration)
        return SirErrorType
      }
      type = arguments.single().type!!.resolve()
    } else if (context.direction == Direction.Input) {
      if (declaration.isApolloOptional()) {
        val argumentType = type.arguments.first().type!!.resolve()

        if (context.hasDefaultValue) {
          logger.error("Input value has a default value and cannot be optional", context.origin)
          return SirErrorType
        }

        if (!argumentType.isMarkedNullable) {
          /*
           * Note: it's still possible to have a missing variable at runtime in a non-null position.
           * Those cases trigger request error before reaching the resolver and the argument cannot
           * be of Optional type.
           */
          logger.error("Input value is not nullable and cannot be optional", context.origin)
          return SirErrorType
        }

        type = argumentType
      } else {
        if (!context.hasDefaultValue && isMarkedNullable) {
          logger.error(
            "Input value is nullable and doesn't have a default value: it must also be optional.",
            context.origin
          )
          return SirErrorType
        }
      }
    }

    return type.toSirType2(context, type.isMarkedNullable)
  }

  private fun KSType.toSirType2(context: SirContext, isNullable: Boolean): SirType {
    if (!isNullable) {
      return SirNonNullType(toSirType2(context, true))
    }

    val qualifiedName = declaration.asClassName().asString()
    if (qualifiedName == "kotlin.collections.List") {
      return SirListType(
        arguments.single().type!!.resolve().toSirType(context.copy(hasDefaultValue = false, isSubscriptionRoot = false))
      )
    }

    return when {
      builtinTypes.contains(qualifiedName) -> {
        val name = builtinScalarName(qualifiedName)
        declarationsToVisit.add(
          DeclarationToVisit(
            declaration,
            DeclarationContext(direction = context.direction, origin = context.origin)
          )
        )
        SirNamedType(name)
      }

      else -> {
        val name = declaration.graphqlName()
        declarationsToVisit.add(
          DeclarationToVisit(
            declaration,
            DeclarationContext(direction = context.direction, origin = context.origin)
          )
        )
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

  fun KSClassDeclaration.entityResolver(logger: KSPLogger, keyFields: Set<String>): SirEntityResolver? {
    if (keyFields.isEmpty()) {
      return null
    }

    val companionObject = declarations.firstOrNull { it is KSClassDeclaration && it.isCompanionObject }
    if (companionObject == null) {
      logger.error("Federated entities must have a companion object.", this)
      return null
    }

    companionObject as KSClassDeclaration
    val candidates = companionObject.declarations.filterIsInstance<KSFunctionDeclaration>().filter {
      it.isPublic() && it.simpleName.asString() == "resolve"
    }
    if (candidates.toList().isEmpty()) {
      logger.error("Federated entities companion object must contain a resolve() function.", companionObject)
      return null
    }

    candidates.forEach {
      val returnType = it.returnType?.resolve()?.declaration
      if (returnType == null || returnType.asClassName().asString() != asClassName().asString()) {
        return@forEach
      }
      val arguments = it.parameters.mapNotNull {
        it.toSirArgument()
      }
      if (arguments.filterIsInstance<SirInputValueDefinition>().map { it.name }.toSet() != keyFields) {
        return@forEach
      }

      return SirEntityResolver(arguments)
    }

    logger.error("A resolve() function was found but either the return types or parameters are unexpected")

    return null
  }
}

/**
 * Sorting helper function. Not 100% sure of the order here
 */
private fun SirTypeDefinition.type(): String {
  return when (this) {
    is SirScalarDefinition -> "0"
    is SirEnumDefinition -> "1"
    is SirObjectDefinition -> "2"
    is SirInterfaceDefinition -> "3"
    is SirUnionDefinition -> "4"
    is SirInputObjectDefinition -> "5"
  }
}

private fun Map<String, SirTypeDefinition?>.patchUnions(unions: Map<String, Set<String>>): List<SirTypeDefinition> {
  return values.filterNotNull().map {
    if (it is SirUnionDefinition) {
      SirUnionDefinition(
        it.name,
        it.description,
        it.qualifiedName,
        unions.get(it.name)!!.toList(),
        it.directives
      )
    } else {
      it
    }
  }
}


private fun KSDeclaration.isApolloOptional(): Boolean {
  return asClassName().asString() == "com.apollographql.apollo.api.Optional"
}

private fun KSDeclaration.isFlow(): Boolean {
  return asClassName().asString() == "kotlinx.coroutines.flow.Flow"
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
  val context: DeclarationContext,
)

private class DeclarationContext(
  val direction: Direction,
  /**
   * If this declaration is a root type, the type of the operation, null else.
   */
  val operationType: String? = null,
  /**
   * Where this declaration is coming from.
   *
   * Only used for error messages when the declaration is not part of the current project for an example.
   */
  val origin: KSNode? = null,
)

private data class SirContext(
  val direction: Direction,
  /**
   * The node that contains the type. One of
   * - KSValueParameter
   * - KSPropertyDeclaration
   * - KSFunctionDeclaration
   *
   */
  val origin: KSNode,
  /**
   * Only for outputs.
   */
  val isSubscriptionRoot: Boolean,
  /**
   * Only for inputs.
   */
  val hasDefaultValue: Boolean,
)

private enum class Direction {
  Input,
  Ouput
}

internal fun KSClassDeclaration.instantiation(): Instantiation {
  return when {
    classKind == ClassKind.OBJECT -> Instantiation.OBJECT
    hasNoArgsConstructor() -> Instantiation.NO_ARG_CONSTRUCTOR
    else -> Instantiation.UNKNOWN
  }
}

