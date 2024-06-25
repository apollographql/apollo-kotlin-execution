package com.apollographql.execution.internal

import com.apollographql.apollo3.api.Error
import com.apollographql.apollo3.api.ExecutionContext
import com.apollographql.apollo3.ast.GQLBooleanValue
import com.apollographql.apollo3.ast.GQLDirective
import com.apollographql.apollo3.ast.GQLEnumTypeDefinition
import com.apollographql.apollo3.ast.GQLField
import com.apollographql.apollo3.ast.GQLFragmentDefinition
import com.apollographql.apollo3.ast.GQLFragmentSpread
import com.apollographql.apollo3.ast.GQLInlineFragment
import com.apollographql.apollo3.ast.GQLInputObjectTypeDefinition
import com.apollographql.apollo3.ast.GQLInterfaceTypeDefinition
import com.apollographql.apollo3.ast.GQLListType
import com.apollographql.apollo3.ast.GQLNamedType
import com.apollographql.apollo3.ast.GQLNonNullType
import com.apollographql.apollo3.ast.GQLObjectTypeDefinition
import com.apollographql.apollo3.ast.GQLOperationDefinition
import com.apollographql.apollo3.ast.GQLScalarTypeDefinition
import com.apollographql.apollo3.ast.GQLSelection
import com.apollographql.apollo3.ast.GQLType
import com.apollographql.apollo3.ast.GQLUnionTypeDefinition
import com.apollographql.apollo3.ast.GQLVariableValue
import com.apollographql.apollo3.ast.Schema
import com.apollographql.apollo3.ast.definitionFromScope
import com.apollographql.apollo3.ast.responseName
import com.apollographql.execution.Coercing
import com.apollographql.execution.GraphQLResponse
import com.apollographql.execution.Instrumentation
import com.apollographql.execution.ResolveInfo
import com.apollographql.execution.ResolveTypeInfo
import com.apollographql.execution.Resolver
import com.apollographql.execution.Roots
import com.apollographql.execution.SubscriptionError
import com.apollographql.execution.SubscriptionEvent
import com.apollographql.execution.SubscriptionResponse
import com.apollographql.execution.leafCoercingSerialize
import com.apollographql.execution.errorResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Returns the typename of the given `obj`
 *
 * This is used for polymorphic types to return the correct __typename depending on the runtime type of `obj`.
 * This function must return a non-null String for any Kotlin instance that represents a GraphQL type that implements an interface or is part of a union.
 *
 * Example:
 * ```
 * when (it) {
 *   is Product -> "Product"
 * }
 * ```
 *
 * Returns the name of the GraphQL type for this runtime instance or null if this runtime instance is not found, which will trigger a GraphQL error.
 */
typealias ResolveType = (obj: Any, resolveTypeInfo: ResolveTypeInfo) -> String?

/**
 * Returns true if `obj` is a runtime instance of the GraphQL type for which this [TypeChecker] was added.
 */
typealias TypeChecker = (obj: Any) -> Boolean

internal class OperationExecutor(
    val operation: GQLOperationDefinition,
    val fragments: Map<String, GQLFragmentDefinition>,
    val executionContext: ExecutionContext,
    val variables: Map<String, ExternalValue>,
    val schema: Schema,
    val resolvers: Map<String, Resolver>,
    val coercings: Map<String, Coercing<*>>,
    val defaultResolver: Resolver,
    val resolveType: ResolveType,
    val instrumentations: List<Instrumentation>,
    val roots: Roots,
) {
  private var errors = mutableListOf<Error>()

  private val coercedVariables by lazy {
    coerceVariablesValues(schema, operation.variableDefinitions, variables, coercings)
  }

  fun execute(): GraphQLResponse {
    val operationDefinition = operation
    val rootTypename = schema.rootTypeNameOrNullFor(operationDefinition.operationType)
    if (rootTypename == null) {
      return errorResponse("'${operationDefinition.operationType}' is not supported")
    }
    val rootObject = when (operationDefinition.operationType) {
      "query" -> roots.query()
      "mutation" -> roots.mutation()
      "subscription" -> return errorResponse("Use executeSubscription() to execute subscriptions")
      else -> errorResponse("Unknown operation type '${operationDefinition.operationType}")
    }
    val typeDefinition = schema.typeDefinition(schema.rootTypeNameFor(operation.operationType))
    val data = try {
      executeSelectionSet(operation.selections, typeDefinition as GQLObjectTypeDefinition, rootObject, emptyList())
    } catch (e: BubbleNullException) {
      null
    } catch (e: Exception) {
      /**
       * This happens when variable coercion fails. Maybe other cases?
       */
      errors.add(Error.Builder(e.message ?: "Error executing selection set").build())
      null
    }

    return GraphQLResponse(data, errors.orNullIfEmpty(), null)
  }

  private fun subscriptionError(message: String): Flow<SubscriptionError> {
    return flowOf(SubscriptionError(listOf(Error.Builder(message).build())))
  }

  private fun resolveFieldEventStream(subscriptionType: GQLObjectTypeDefinition, rootValue: ResolverValue, fields: List<GQLField>, coercedArgumentValues: Map<String, InternalValue>, responseName: String): Flow<PendingField> {
    val flow = resolveFieldValue(subscriptionType, rootValue, fields, arguments = coercedArgumentValues)
    if (flow == null) {
      error("root subscription field returned null")
    }

    if (flow !is Flow<*>) {
      error("Subscription resolvers must return a Flow<> for root fields")
    }

    return flow.map {
      PendingFieldItem(
          parentType = subscriptionType.name,
          objectValue = it,
          fields = fields,
          responseName = responseName
      )
    }
  }

  sealed interface PendingField
  private class PendingFieldItem(
      val parentType: String,
      val objectValue: InternalValue,
      val fields: List<GQLField>,
      val responseName: String,
  ) : PendingField

  private class PendingFieldError(
      val message: String,
  ) : PendingField

  private fun createSourceEventStream(initialValue: ResolverValue): Flow<PendingField> {
    val rootTypename = schema.rootTypeNameOrNullFor(operation.operationType)
    if (rootTypename == null) {
      return flowOf(PendingFieldError("'${operation.operationType}' is not supported"))
    }

    val typeDefinition = schema.typeDefinition(rootTypename)
    check(typeDefinition is GQLObjectTypeDefinition) {
      "Root typename '${typeDefinition.name} must be of object type"
    }
    val selections = operation.selections
    val groupedFieldsSet = collectFields(typeDefinition.name, selections, coercedVariables)
    check(groupedFieldsSet.size == 1) {
      return flowOf(PendingFieldError("Subscriptions must have a single root field"))
    }
    val fields = groupedFieldsSet.values.first()
    return resolveFieldEventStream(typeDefinition, initialValue, fields, coerceArgumentValues(schema, typeDefinition.name, fields.first(), coercings, coercedVariables), responseName = fields.first().responseName())
  }

  fun executeSubscription(): Flow<SubscriptionEvent> {
    val rootObject = when (operation.operationType) {
      "subscription" -> roots.subscription()
      else -> return subscriptionError("Unknown operation type '${operation.operationType}.")
    }

    val eventStream = try {
      createSourceEventStream(rootObject)
    } catch (e: Exception) {
      return subscriptionError(e.message ?: "cannot create source event stream")
    }
    return mapSourceToResponseEvent(eventStream).catch {
      emit(SubscriptionError(listOf(Error.Builder(it.message ?: "error collecting the source event stream").build())))
    }
  }

  private fun mapSourceToResponseEvent(sourceStream: Flow<PendingField>): Flow<SubscriptionEvent> {
    return sourceStream.map {
      // TODO: allow implementers to terminate the stream with an exception
      SubscriptionResponse(executeSubscriptionEvent(it))
    }
  }

  private fun executeSubscriptionEvent(event: PendingField): GraphQLResponse {
    return when (event) {
      is PendingFieldError -> GraphQLResponse.Builder().errors(listOf(Error.Builder(event.message).build())).build()
      is PendingFieldItem -> {
        val data = try {
          mapOf(
              event.responseName to completeValue(
                  event.fields.first().definitionFromScope(schema, event.parentType)!!.type,
                  fields = event.fields,
                  event.objectValue,
                  variables,
                  listOf(event.responseName)
              )
          )
        } catch (e: Exception) {
          e.printStackTrace()
          null
        }
        GraphQLResponse(data, errors.orNullIfEmpty(), null)
      }
    }
  }


  private fun executeField(objectType: GQLObjectTypeDefinition, objectValue: ResolverValue, fieldType: GQLType, fields: List<GQLField>, coercedVariables: Map<String, InternalValue>, path: List<Any>): ExternalValue {
    val field = fields.first()
    val arguments = coerceArgumentValues(schema, objectType.name, field, coercings, coercedVariables)

    val resolvedValue = resolveFieldValue(objectType, objectValue, fields, arguments)
    return completeValue(fieldType, fields, resolvedValue, coercedVariables, path)
  }

  private fun completeValue(fieldType: GQLType, fields: List<GQLField>, result: ResolverValue, coercedVariables: Map<String, InternalValue>, path: List<Any>): ExternalValue {
    if (fieldType is GQLNonNullType) {
      val completedResult = completeValue(fieldType.type, fields, result, coercedVariables, path)
      if (completedResult == null) {
        error("A resolver returned null in a non-null position")
      }
      return completedResult
    }

    if (result == null) {
      return null
    }

    if (fieldType is GQLListType) {
      if (result !is List<*>) {
        error("A resolver returned non-list in a list position")
      }
      return result.map { completeValue(fieldType.type, fields, it, coercedVariables, path) }
    }

    fieldType as GQLNamedType
    val typeDefinition = schema.typeDefinition(fieldType.name)
    return when (typeDefinition) {
      is GQLEnumTypeDefinition,
      is GQLScalarTypeDefinition,
      -> {
        // leaf type
        leafCoercingSerialize(result, coercings, typeDefinition)
      }

      is GQLInterfaceTypeDefinition,
      is GQLObjectTypeDefinition,
      is GQLUnionTypeDefinition,
      -> {
        val typename = if (typeDefinition is GQLObjectTypeDefinition) {
          typeDefinition.name
        } else {
          resolveType(result, ResolveTypeInfo(typeDefinition.name, schema))
        }
        if (typename == null) {
          error("Cannot resolve object __typename for instance '$result' of abstract type '${typeDefinition.name}'.\nConfigure `resolveType` or `typeCheckers`.")
        }

        val selections = fields.flatMap { it.selections }
        return executeSelectionSet(selections, schema.typeDefinition(typename) as GQLObjectTypeDefinition, result, path)
      }

      is GQLInputObjectTypeDefinition -> {
        error("Input type in output position")
      }
    }
  }

  private fun resolveFieldValue(typeDefinition: GQLObjectTypeDefinition, objectValue: ResolverValue, fields: List<GQLField>, arguments: Map<String, InternalValue>): ResolverValue {
    val resolveInfo = ResolveInfo(
        parentObject = objectValue,
        executionContext = executionContext,
        fields = fields,
        schema = schema,
        variables = coercedVariables,
        arguments = arguments,
        parentType = typeDefinition.name
    )

    instrumentations.forEach { it.beforeResolve(resolveInfo) }

    return when {
      resolveInfo.fieldName == "__typename" -> typeDefinition.name
      else -> {
        (resolvers.get(resolveInfo.coordinates()) ?: defaultResolver).resolve(resolveInfo)
      }
    }
  }

  private fun executeSelectionSet(selections: List<GQLSelection>, typeDefinition: GQLObjectTypeDefinition, objectValue: ResolverValue, path: List<Any>): Map<String, ExternalValue> {
    val typename = typeDefinition.name
    val groupedFieldSet = collectFields(typename, selections, coercedVariables)
    return groupedFieldSet.entries.associate { entry ->
      val field = entry.value.first()
      val fieldDefinition = field.definitionFromScope(schema, typename)!!
      val fieldPath = path + field.responseName()

      val fieldData = try {
        executeField(typeDefinition, objectValue, fieldDefinition.type, entry.value, coercedVariables, fieldPath)
      } catch (e: Exception) {
        if (e !is BubbleNullException) {
          /**
           * Only add if it was not already added downstream
           */
          errors.add(
              Error.Builder("Cannot resolve '${field.name}': ${e.message}")
                  .path(path)
                  .build()
          )
          e.printStackTrace()
        }
        if (fieldDefinition.type is GQLNonNullType) {
          throw BubbleNullException
        }
        null
      }
      entry.key to fieldData
    }
  }

  object BubbleNullException : Exception()

  /**
   * Assumes validation and or variable coercion caught errors, crashes else.
   */
  private fun GQLDirective.singleRequiredBooleanArgumentValue(coercedVariables: Map<String, InternalValue>): Boolean {
    val value = arguments.single().value
    return when (value) {
      is GQLBooleanValue -> value.value
      is GQLVariableValue -> {
        // If the variable is absent or not a boolean, it should have failed during coercion
        coercedVariables.get(value.name) as Boolean
      }

      else -> error("Cannot get argument value for directive '$name'")
    }
  }

  private fun List<GQLDirective>.shouldSkip(coercedVariables: Map<String, InternalValue>): Boolean {
    forEach {
      if (it.name == "skip") {
        if (it.singleRequiredBooleanArgumentValue(coercedVariables)) {
          return true
        }
      }
    }
    forEach {
      if (it.name == "include") {
        if (!it.singleRequiredBooleanArgumentValue(coercedVariables)) {
          return true
        }
      }
    }
    return false
  }

  private fun <K, V> MutableMap<K, V>.update(key: K, update: (prev: V?) -> V) {
    val newValue = if (this.containsKey(key)) {
      update(this.get(key))
    } else {
      update(null)
    }
    put(key, newValue)
  }

  private fun collectFields(
      objectType: String,
      selections: List<GQLSelection>,
      coercedVariables: Map<String, InternalValue>,
  ): Map<String, List<GQLField>> {
    val groupedFields = mutableMapOf<String, List<GQLField>>()
    collectFields(objectType, selections, coercedVariables, mutableSetOf(), groupedFields)
    return groupedFields
  }

  private fun collectFields(
      objectType: String,
      selections: List<GQLSelection>,
      coercedVariables: Map<String, InternalValue>,
      visitedFragments: MutableSet<String>,
      groupedFields: MutableMap<String, List<GQLField>>,
  ) {
    selections.forEach { selection ->
      if (selection.directives.shouldSkip(coercedVariables)) {
        return@forEach
      }
      when (selection) {
        is GQLField -> {
          groupedFields.update(selection.responseName()) {
            (it.orEmpty()).plus(selection)
          }
        }

        is GQLFragmentSpread -> {
          if (visitedFragments.contains(selection.name)) {
            return@forEach
          }
          visitedFragments.add(selection.name)

          val fragmentDefinition = fragments.get(selection.name)!!

          if (schema.possibleTypes(fragmentDefinition.typeCondition.name).contains(objectType)) {
            collectFields(objectType, fragmentDefinition.selections, coercedVariables, visitedFragments, groupedFields)
          }
        }

        is GQLInlineFragment -> {
          val typeCondition = selection.typeCondition?.name
          if (typeCondition == null || schema.possibleTypes(typeCondition).contains(objectType)) {
            collectFields(objectType, selection.selections, coercedVariables, visitedFragments, groupedFields)
          }
        }
      }
    }
  }
}

private fun <E> List<E>.orNullIfEmpty(): List<E>? {
  return this.ifEmpty {
    null
  }
}


private val GQLSelection.directives: List<GQLDirective> get() = when(this) {
  is GQLField -> directives
  is GQLFragmentSpread -> directives
  is GQLInlineFragment -> directives
}