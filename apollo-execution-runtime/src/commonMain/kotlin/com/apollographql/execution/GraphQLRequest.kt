@file:Suppress("UNCHECKED_CAST")

package com.apollographql.execution

import com.apollographql.apollo.annotations.ApolloInternal
import com.apollographql.apollo.api.http.internal.urlDecode
import com.apollographql.apollo.api.json.jsonReader
import com.apollographql.apollo.api.json.readAny
import com.apollographql.execution.internal.ExternalValue
import okio.Buffer
import okio.BufferedSource
import okio.use

/**
 * @property document the document, may be null if persisted queries are used.
 * @property operationName the name of the operation to execute (optional). Useful if [document] contains several operations.
 * @property variables the variables, may be empty
 * @property extensions the extensions, may be empty
 */
class GraphQLRequest internal constructor(
  val document: String?,
  val operationName: String?,
  val variables: Map<String, ExternalValue>,
  val extensions: Map<String, ExternalValue>,
) {
  class Builder {
    var document: String? = null
    var operationName: String? = null
    var variables: Map<String, ExternalValue>? = null
    var extensions: Map<String, ExternalValue>? = null

    fun document(document: String?): Builder = apply {
      this.document = document
    }

    fun operationName(operationName: String?): Builder = apply {
      this.operationName = operationName
    }

    fun variables(variables: Map<String, ExternalValue>?): Builder = apply {
      this.variables = variables
    }

    fun extensions(extensions: Map<String, ExternalValue>?): Builder = apply {
      this.extensions = extensions
    }

    fun build(): GraphQLRequest {
      return GraphQLRequest(
        document,
        operationName,
        variables.orEmpty(),
        extensions.orEmpty()
      )
    }
  }
}

/**
 * Parses a map of external values to a [GraphQLRequest].
 *
 * Note: this is typically used by subscriptions and/or post requests. GET request encode "variables" and "extensions" as JSON and
 * need to be preprocessed first. See [toExternalValueMap].
 */
fun Map<String, ExternalValue>.parseGraphQLRequest(): GraphQLResult<GraphQLRequest> {
  val map = this

  val document = map.get("query")
  if (document !is String?) {
    return GraphQLError("Expected 'query' to be a string")
  }

  val variables = map.get("variables")
  if (variables !is Map<*, *>?) {
    return GraphQLError("Expected 'variables' to be an object")
  }

  val extensions = map.get("extensions")
  if (extensions !is Map<*, *>?) {
    return GraphQLError("Expected 'extensions' to be an object")
  }

  val operationName = map.get("operationName")
  if (operationName !is String?) {
    return GraphQLError("Expected 'operationName' to be a string")
  }
  return GraphQLRequest.Builder()
    .document(document)
    .variables(variables as Map<String, Any?>?)
    .extensions(extensions as Map<String, Any?>?)
    .operationName(operationName)
    .build().let {
      GraphQLSuccess(it)
    }
}

@OptIn(ApolloInternal::class)
fun BufferedSource.parseGraphQLRequest(): GraphQLResult<GraphQLRequest> {
  val map = try {
    jsonReader().use {
      it.readAny()
    }
  } catch (e: Exception) {
    return GraphQLError(e)
  }

  if (map !is Map<*, *>) {
    return GraphQLError("The received JSON is not an object")
  }

  map as Map<String, ExternalValue>

  return map.parseGraphQLRequest()
}

/**
 * Parses an url to a GraphQL request
 */
fun String.parseUrlToGraphQLRequest(): GraphQLResult<GraphQLRequest> {
  var fragmentStart = indexOfLast { it == '#' }
  if (fragmentStart < 0) {
    fragmentStart = length
  }
  var queryStart = fragmentStart - 1
  while (queryStart > 0) {
    if (get(queryStart) == '?') {
      break
    }
    queryStart--
  }

  if (queryStart == 0) {
    return GraphQLError("No GraphQL query parameters found in '$this'")
  }

  // go back to after '?'
  queryStart++

  val query = substring(queryStart, fragmentStart)

  return query.parseQueryToGraphQLRequest()
}

fun String.parseQueryToGraphQLRequest(): GraphQLResult<GraphQLRequest> {
  val pairs = split("&")

  return pairs.filter { it.isNotBlank() }.map { pair ->
    pair.split("=").let {
      if (it.size != 2) {
        return GraphQLError("Invalid query parameter '$pair'")
      }
      it.get(0).urlDecode() to it.get(1).urlDecode()
    }
  }.groupBy { it.first }
    .mapValues {
      it.value.map { it.second }
    }
    .toExternalValueMap()
    .flatMap {
      it.parseGraphQLRequest()
    }
}

/**
 * Converts a multi-map such as one from parsed query params to a map of external values.
 */
fun Map<String, List<String>>.toExternalValueMap(): GraphQLResult<Map<String, ExternalValue>> {
  val map = mapValues {
    when (it.key) {
      "query" -> {
        if (it.value.size != 1) {
          return GraphQLError("multiple 'query' parameter found")
        }

        it.value.first()
      }

      "variables" -> {
        if (it.value.size != 1) {
          return GraphQLError("multiple 'variables' parameter found")
        }

        try {
          it.value.first().readAny()
        } catch (e: Exception) {
          return GraphQLError(e)
        }
      }

      "extensions" -> {
        if (it.value.size != 1) {
          return GraphQLError("multiple 'extensions' parameter found")
        }

        try {
          it.value.first().readAny()
        } catch (e: Exception) {
          return GraphQLError(e)
        }
      }

      "operationName" -> {
        if (it.value.size != 1) {
          return GraphQLError("multiple 'operationName' parameter found")
        }

        it.value.first()
      }

      else -> it.value
    }
  }

  return GraphQLSuccess(map)
}

@OptIn(ApolloInternal::class)
private fun String.readAny(): Any? = Buffer().writeUtf8(this).jsonReader().readAny()