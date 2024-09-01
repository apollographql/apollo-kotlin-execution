package com.apollographql.execution.spring

import com.apollographql.apollo.api.ExecutionContext
import com.apollographql.execution.*
import okio.Buffer
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.server.*

suspend fun ServerRequest.toGraphQLRequest(): GraphQLResult<GraphQLRequest> {
  return when (this.method()) {
    HttpMethod.GET -> this.queryParams().parseGraphQLRequest()
    HttpMethod.POST -> {
      awaitBody<String>().let {
        Buffer().writeUtf8(it).parseGraphQLRequest()
      }
    }
    else -> GraphQLError(Exception("Unhandled method: ${method()}"))
  }
}

fun CoRouterFunctionDsl.apolloGraphQLRoutes(
  executableSchema: ExecutableSchema,
  path: String = "/graphql",
  executionContext: ExecutionContext = ExecutionContext.Empty
) {
  (POST(path) or GET(path)).invoke { serverRequest ->
    val graphqlRequestResult = serverRequest.toGraphQLRequest()
    if (!graphqlRequestResult.isSuccess) {
      return@invoke badRequest().buildAndAwait()
    }
    val graphQLResponse = executableSchema.execute(graphqlRequestResult.getOrThrow(), executionContext)

    return@invoke ok().contentType(MediaType.parseMediaType("application/graphql-response+json"))
      .bodyValueAndAwait(graphQLResponse.toByteArray())
  }
}

fun CoRouterFunctionDsl.apolloSandboxRoutes(
  title: String = "API sandbox",
  graphqlPath: String = "/graphql",
) {
  GET("/sandbox/index.html") {
    ok().contentType(MediaType.TEXT_HTML).bodyValueAndAwait(sandboxHtml(title, it.uriBuilder().replacePath(graphqlPath).build().toString()))
  }
  GET("/sandbox/**") {
    permanentRedirect(it.uriBuilder().replacePath("/sandbox/index.html").build()).buildAndAwait()
  }
}

private fun GraphQLResponse.toByteArray(): ByteArray {
  val buffer = Buffer()
  serialize(buffer)
  return buffer.readByteArray()
}