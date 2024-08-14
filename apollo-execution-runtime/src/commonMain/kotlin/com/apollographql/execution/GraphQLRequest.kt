@file:Suppress("UNCHECKED_CAST")

package com.apollographql.execution

import com.apollographql.apollo.annotations.ApolloInternal
import com.apollographql.apollo.api.http.internal.urlDecode
import com.apollographql.apollo.api.json.jsonReader
import com.apollographql.apollo.api.json.readAny
import okio.Buffer
import okio.BufferedSource
import okio.use
import kotlin.jvm.JvmName

class GraphQLRequest internal constructor(
    val document: String?,
    val operationName: String?,
    val variables: Map<String, Any?>,
    val extensions: Map<String, Any?>,
) {
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

fun Map<String, Any?>.parseGraphQLRequest(): Result<GraphQLRequest> {
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
fun BufferedSource.parseGraphQLRequest(): Result<GraphQLRequest> {
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

  return map.parseGraphQLRequest()
}

/**
 * Parses an url to a GraphQL request
 */
fun String.parseGraphQLRequest(): Result<GraphQLRequest> {
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

  return pairs.map {
    it.split("=").let {
      it.get(0).urlDecode() to it.get(1).urlDecode()
    }
  }.groupBy { it.first }
    .mapValues {
      it.value.map { it.second }
    }
    .parseGraphQLRequest()
}

@JvmName("parseGraphQLRequestFromMultiMap")
fun Map<String, List<String>>.parseGraphQLRequest(): Result<GraphQLRequest> {
  return mapValues {
    when (it.key) {
      "query" -> {
        if (it.value.size != 1) {
          return Result.failure(Exception("multiple 'query' parameter found"))
        }

        it.value.first()
      }
      "variables" -> {
        if (it.value.size != 1) {
          return Result.failure(Exception("multiple 'variables' parameter found"))
        }

        try {
          it.value.first().readAny()
        } catch (e: Exception) {
          return Result.failure(e)
        }
      }
      "extensions" -> {
        if (it.value.size != 1) {
          return Result.failure(Exception("multiple 'extensions' parameter found"))
        }

        try {
          it.value.first().readAny()
        } catch (e: Exception) {
          return Result.failure(e)
        }
      }
      "operationName" -> {
        if (it.value.size != 1) {
          return Result.failure(Exception("multiple 'operationName' parameter found"))
        }

        it.value.first()
      }
      else -> it.value
    }
  }.parseGraphQLRequest()
}

@OptIn(ApolloInternal::class)
private fun String.readAny(): Any? = Buffer().writeUtf8(this).jsonReader().readAny()