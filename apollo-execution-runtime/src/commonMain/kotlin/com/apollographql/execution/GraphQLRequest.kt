@file:Suppress("UNCHECKED_CAST")

package com.apollographql.execution

import com.apollographql.apollo.annotations.ApolloInternal
import com.apollographql.apollo.api.Error
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.api.http.internal.urlDecode
import com.apollographql.apollo.api.json.BufferedSinkJsonWriter
import com.apollographql.apollo.api.json.JsonWriter
import com.apollographql.apollo.api.json.jsonReader
import com.apollographql.apollo.api.json.readAny
import com.apollographql.apollo.api.json.writeAny
import com.apollographql.apollo.api.json.writeObject
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.Sink
import okio.buffer
import okio.use

class GraphQLRequest internal constructor(
    val document: String?,
    val operationName: String?,
    val variables: Map<String, Any?>,
    val extensions: Map<String, Any?>,
) : GraphQLRequestResult {
  class Builder {
    var document: String? = null
    var operationName: String? = null
    var variables: Map<String, Any?>? = null
    var extensions: Map<String, Any?>? = null

    fun document(document: String?): Builder = apply {
      this.document = document
    }

    fun operationName(operationName: String?): Builder = apply {
      this.operationName = operationName
    }

    fun variables(variables: Map<String, Any?>?): Builder = apply {
      this.variables = variables
    }

    fun extensions(extensions: Map<String, Any?>?): Builder = apply {
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


sealed interface GraphQLRequestResult

class GraphQLRequestError internal constructor(
    val message: String,
) : GraphQLRequestResult

fun Map<String, Any?>.toGraphQLRequest(): Result<GraphQLRequest> {
  val map = this

  val document = map.get("query")
  if (document !is String) {
    return Result.failure(Exception("Expected 'query' to be a string"))
  }

  val variables = map.get("variables")
  if (variables !is Map<*, *>?) {
    return Result.failure(Exception("Expected 'variables' to be an object"))
  }

  val extensions = map.get("extensions")
  if (extensions !is Map<*, *>?) {
    return Result.failure(Exception("Expected 'extensions' to be an object"))
  }

  val operationName = map.get("operationName")
  if (operationName !is String?) {
    return Result.failure(Exception("Expected 'operationName' to be a string"))
  }
  return GraphQLRequest.Builder()
      .document(document)
      .variables(variables as Map<String, Any?>?)
      .extensions(extensions as Map<String, Any?>?)
      .operationName(operationName)
      .build().let {
        Result.success(it)
      }
}

@OptIn(ApolloInternal::class)
fun BufferedSource.parsePostGraphQLRequest(): Result<GraphQLRequest> {
  val map = try {
    jsonReader().use {
      it.readAny()
    }
  } catch (e: Exception) {
    return Result.failure(e)
  }

  if (map !is Map<*, *>) {
    return Result.failure(Exception("The received JSON is not an object"))
  }

  map as Map<String, Any?>

  return map.toGraphQLRequest()
}

/**
 * Parses a query string to a GraphQL request
 */
@OptIn(ApolloInternal::class)
fun String.parseGetGraphQLRequest(): Result<GraphQLRequest> {
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
  // go back to after '?' (or beginning if no '?')
  queryStart++

  val query = substring(queryStart, fragmentStart)
  val pairs = query.split("&")

  val builder = GraphQLRequest.Builder()

  pairs.forEach {
    it.split("=").apply {
      if (size != 2) {
        return@forEach
      }

      when (get(0).urlDecode()) {
        "query" -> builder.document(get(1).urlDecode())
        "variables" -> {
          val variablesJson = try {
            get(1).urlDecode()
          } catch (e: Exception) {
            return Result.failure(Exception("Cannot decode 'variables' ('${get(1)}')", e))
          }
          val map = try {
            Buffer().writeUtf8(variablesJson).jsonReader().readAny()
          } catch (e: Exception) {
            return Result.failure(Exception("'variables' is not a valid JSON ('${variablesJson}')", e))
          }
          if (map !is Map<*, *>?) {
            return Result.failure(Exception("Expected 'variables' to be an object"))
          }
          builder.variables(map as Map<String, Any>?)
        }

        "extensions" -> {
          val extensions = try {
            get(1).urlDecode()
          } catch (e: Exception) {
            return Result.failure(Exception("Cannot decode 'extensions' ('${get(1)}')", e))
          }
          val map = try {
            Buffer().writeUtf8(extensions).jsonReader().readAny()
          } catch (e: Exception) {
            return Result.failure(Exception("'extensions' is not a valid JSON ('${extensions}')", e))
          }
          if (map !is Map<*, *>?) {
            return Result.failure(Exception("Expected 'extensions' to be an object"))
          }
          builder.extensions(map as Map<String, Any>?)
        }

        "operationName" -> builder.operationName(get(1).urlDecode())
      }
    }
  }

  return Result.success(builder.build())
}
