package com.apollographql.apollo3.execution

import com.apollographql.apollo3.annotations.ApolloExperimental
import com.apollographql.apollo3.api.Error
import com.apollographql.apollo3.api.ExecutionContext
import com.apollographql.apollo3.ast.GQLDocument
import com.apollographql.apollo3.ast.GQLFragmentDefinition
import com.apollographql.apollo3.ast.GQLOperationDefinition
import com.apollographql.apollo3.ast.GraphQLIssue
import com.apollographql.apollo3.ast.Issue
import com.apollographql.apollo3.ast.Schema
import com.apollographql.apollo3.ast.builtinDefinitions
import com.apollographql.apollo3.ast.parseAsGQLDocument
import com.apollographql.apollo3.ast.toGQLDocument
import com.apollographql.apollo3.ast.toSchema
import com.apollographql.apollo3.ast.validateAsExecutable
import com.apollographql.apollo3.execution.internal.OperationExecutor
import com.apollographql.apollo3.execution.internal.ResolveType
import com.apollographql.apollo3.execution.internal.TypeChecker
import com.apollographql.apollo3.execution.internal.introspectionCoercings
import com.apollographql.apollo3.execution.internal.introspectionResolvers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Suppress("UNCHECKED_CAST")
@OptIn(ApolloExperimental::class)
class ExecutableSchema internal constructor(
    private val schema: Schema,
    private val persistedDocumentCache: PersistedDocumentCache?,
    private val instrumentations: List<Instrumentation>,
    private val resolvers: Map<String, Resolver>,
    private val coercings: Map<String, Coercing<*>>,
    private val defaultResolver: Resolver,
    private val resolveType: ResolveType,
    private val roots: Roots,
) {

  class Builder {
    private var persistedDocumentCache: PersistedDocumentCache? = null
    private var instrumentations = mutableListOf<Instrumentation>()
    private var coercings = mutableMapOf<String, Coercing<*>>()
    private var schema: GQLDocument? = null
    private var queryRoot: (() -> Any)? = null
    private var mutationRoot: (() -> Any)? = null
    private var subscriptionRoot: (() -> Any)? = null
    private val resolvers = mutableMapOf<String, Resolver>()
    private var defaultResolver: Resolver? = null
    private val typeCheckers = mutableMapOf<String, TypeChecker>()
    private var resolveType: ResolveType? = null

    fun persistedDocumentCache(persistedDocumentCache: PersistedDocumentCache?): Builder = apply {
      this.persistedDocumentCache = persistedDocumentCache
    }

    fun addInstrumentation(instrumentation: Instrumentation): Builder = apply {
      this.instrumentations.add(instrumentation)
    }

    fun addCoercing(type: String, coercing: Coercing<*>): Builder = apply {
      this.coercings.put(type, coercing)
    }

    fun schema(schema: GQLDocument): Builder = apply {
      this.schema = schema
    }

    fun schema(schema: String): Builder = apply {
      schema(schema.toGQLDocument())
    }

    fun addResolver(type: String, field: String, resolver: Resolver): Builder = apply {
      addResolver("$type.$field", resolver)
    }

    /**
     * Adds the given resolver for field with coordinates [coordinates].
     *
     * [addResolver] replace any existing resolver for those coordinates.
     *
     * @param coordinates GraphQL coordinates such as "Query.hello"
     */
    fun addResolver(coordinates: String, resolver: Resolver): Builder = apply {
      resolvers[coordinates] = resolver
    }

    fun defaultResolver(defaultResolver: Resolver): Builder = apply {
      this.defaultResolver = defaultResolver
    }

    fun resolveType(resolveType: ResolveType): Builder = apply {
      this.resolveType = resolveType
    }

    fun addTypeChecker(type: String, typeChecker: TypeChecker): Builder = apply {
      this.typeCheckers.put(type, typeChecker)
    }

    fun build(): ExecutableSchema {
      val definitions = builtinDefinitions() + (schema?.definitions ?: error("A schema is required to build an ExecutableSchema"))
      val schema = GQLDocument(definitions, null).toSchema()

      val resolvers = buildMap {
        putAll(introspectionResolvers(schema))
        putAll(resolvers)
      }
      val coercings = buildMap {
        putAll(introspectionCoercings)
        putAll(coercings)
      }
      val resolveType = if (resolveType != null) {
        check(typeCheckers.isEmpty()) {
          "Setting both 'resolveType' and 'typeCheckers' is an error. 'typeCheckers' are not used if 'resolveType' is set"
        }
        resolveType!!
      } else {
        resolveType(typeCheckers)
      }

      return ExecutableSchema(
          schema,
          persistedDocumentCache,
          instrumentations,
          resolvers,
          coercings,
          defaultResolver ?: ThrowingResolver,
          resolveType,
          Roots.create(
              queryRoot = queryRoot,
              mutationRoot = mutationRoot,
              subscriptionRoot = subscriptionRoot
          )
      )
    }

    fun queryRoot(queryRoot: () -> Any) = apply {
      this.queryRoot = queryRoot
    }

    fun mutationRoot(mutationRoot: () -> Any) = apply {
      this.mutationRoot = mutationRoot
    }

    fun subscriptionRoot(subscriptionRoot: () -> Any) = apply {
      this.subscriptionRoot = subscriptionRoot
    }
  }

  private fun validateDocument(document: String): PersistedDocument {
    val parseResult = document.parseAsGQLDocument()
    var issues = parseResult.issues.filter { it is GraphQLIssue }
    if (issues.isNotEmpty()) {
      return PersistedDocument(null, issues)
    }

    val gqlDocument = parseResult.getOrThrow()
    val validationResult = gqlDocument.validateAsExecutable(schema)
    issues = validationResult.issues.filter { it is GraphQLIssue }
    if (issues.isNotEmpty()) {
      return PersistedDocument(null, issues)
    }

    return PersistedDocument(gqlDocument, emptyList())
  }

  internal sealed interface DocumentResult
  internal class DocumentError(val errors: List<Error>) : DocumentResult  {
    constructor(message: String): this(listOf(Error.Builder(message).build()))
  }

  internal class DocumentSuccess(val document: GQLDocument) : DocumentResult

  private fun getValidatedDocument(request: GraphQLRequest): DocumentResult {
    val persistedQuery = request.extensions.get("persistedQuery")
    var persistedDocument: PersistedDocument?
    if (persistedQuery != null) {
      if (persistedDocumentCache == null) {
        return DocumentError("PersistedQueryNotSupported")
      }

      if (persistedQuery !is Map<*, *>) {
        return DocumentError("Expected 'persistedQuery' to be an object.")
      }

      persistedQuery as Map<String, Any?>

      val id = persistedQuery.get("sha256Hash") as? String

      if (id == null) {
        return DocumentError("'persistedQuery.sha256Hash' not found or not a string.")
      }

      persistedDocument = persistedDocumentCache.get(id)
      if (persistedDocument == null) {
        if (request.document == null) {
          return DocumentError("PersistedQueryNotFound")
        }

        persistedDocument = validateDocument(request.document)

        /**
         * Note this code trusts the client for the id. Given that APQs are not a security
         * feature, I'm assuming this is OKAY. If not, change this
         */
        persistedDocumentCache.put(id, persistedDocument)
      }
    } else {
      if (request.document == null) {
        return DocumentError("no GraphQL document found")
      }
      persistedDocument = validateDocument(request.document)
    }

    if (persistedDocument.issues.isNotEmpty()) {
      return DocumentError(persistedDocument.issues.toErrors())
    }

    val gqlDocument = persistedDocument.document
    if (gqlDocument == null) {
      return DocumentError("no GraphQL document found (this is mostly an internal bug)")
    }

    return DocumentSuccess(gqlDocument)
  }

  internal sealed interface OperationExecutorResult
  internal class OperationExecutorError(val errors: List<Error>) : OperationExecutorResult {
    constructor(message: String): this(listOf(Error.Builder(message).build()))
  }
  internal class OperationExecutorSuccess(val operationExecutor: OperationExecutor) : OperationExecutorResult

  private fun getOperationExecutor(request: GraphQLRequest, context: ExecutionContext): OperationExecutorResult {
    val documentResult = getValidatedDocument(request)

    if (documentResult is DocumentError) {
      return OperationExecutorError(documentResult.errors)
    }

    val document = (documentResult as DocumentSuccess).document

    val operations = document.definitions.filterIsInstance<GQLOperationDefinition>()
    val operation = when {
      operations.isEmpty() -> {
        return OperationExecutorError("The document does not contain any operation.")
      }

      operations.size == 1 -> {
        operations.first()
      }

      else -> {
        if (request.operationName == null) {
          return OperationExecutorError("The document contains multiple operations. Use 'operationName' to indicate which one to execute.")
        }
        val ret = operations.firstOrNull { it.name == request.operationName }
        if (ret == null) {
          return OperationExecutorError("No operation named '${request.operationName}' found. Double check operationName.")
        }
        ret
      }
    }
    val fragments = document.definitions.filterIsInstance<GQLFragmentDefinition>().associateBy { it.name }

    return OperationExecutorSuccess(
        OperationExecutor(
            operation = operation,
            fragments = fragments,
            executionContext = context,
            variables = request.variables,
            schema = schema,
            resolvers = resolvers,
            defaultResolver = defaultResolver,
            resolveType = resolveType,
            coercings = coercings,
            instrumentations = instrumentations,
            roots = roots
        )
    )
  }

  fun execute(request: GraphQLRequest, context: ExecutionContext): GraphQLResponse {
    return when (val result = getOperationExecutor(request, context)) {
      is OperationExecutorError -> GraphQLResponse(null, result.errors, null)
      is OperationExecutorSuccess -> result.operationExecutor.execute()
    }
  }

  fun executeSubscription(request: GraphQLRequest, context: ExecutionContext): Flow<SubscriptionEvent> {
    return when (val result = getOperationExecutor(request, context)) {
      is OperationExecutorError -> flowOf(SubscriptionError(result.errors))
      is OperationExecutorSuccess -> result.operationExecutor.executeSubscription()
    }
  }

  private fun errorResponse(errors: List<Error>): GraphQLResponse {
    return GraphQLResponse(null, errors, null)
  }

  private fun List<Issue>.toErrors(): List<Error> {
    return map {
      Error.Builder(
          message = it.message,
      ).locations(
          listOf(Error.Location(it.sourceLocation!!.line, it.sourceLocation!!.column))
      ).build()
    }
  }
}

private fun resolveType(typeCheckers: Map<String, TypeChecker>) : ResolveType {
  return { obj, resolveTypeInfo ->
    resolveTypeInfo.schema.possibleTypes(resolveTypeInfo.type).first {
      typeCheckers.get(it)?.invoke(obj) == true
    }
  }
}

internal fun errorResponse(message: String): GraphQLResponse {
  return GraphQLResponse.Builder()
      .errors(listOf(Error.Builder(message).build()))
      .build()
}

/**
 *
 */
sealed interface SubscriptionEvent

/**
 * A response from the stream. May contain field errors.
 */
class SubscriptionResponse(val response: GraphQLResponse): SubscriptionEvent

/**
 * This subscription failed.
 *
 * This event is terminal and the client can decide whether to retry or give up.
 * For convenience, [SubscriptionError] uses the same error type as the GraphQL errors but these are not in the same domain. Another server
 * implementation could decide to use something else.
 */
class SubscriptionError(val errors: List<Error>): SubscriptionEvent