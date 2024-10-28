package com.apollographql.execution.ktor

import com.apollographql.apollo.api.ExecutionContext
import com.apollographql.execution.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.ktor.utils.io.*
import okio.Buffer

suspend fun ApplicationCall.respondGraphQL(
  executableSchema: ExecutableSchema,
  executionContext: ExecutionContext = ExecutionContext.Empty,
  configure: OutgoingContent.(GraphQLResponse?) -> Unit = {}
) {
  val request = request.parseAsGraphQLRequest()
  val contentType = ContentType.parse("application/graphql-response+json")
  if (request.isFailure) {
    respondText(
      contentType = contentType,
      status = HttpStatusCode.BadRequest,
      text = request.exceptionOrNull()?.message ?: "",
      configure = { configure(null) }
    )
  } else {
    val response = executableSchema.execute(request.getOrThrow(), executionContext)
    respondBytes(
      contentType = contentType,
      status = HttpStatusCode.OK,
      bytes = response.toByteArray(),
      configure = { configure(response) }
    )
  }
}

/**
 * Probably a lot of copy operation going on down there, but we need to go in blocking land
 */
private suspend fun ByteReadChannel.buffer(): Buffer {
  val buffer = Buffer()
  val byteArray = ByteArray(8192)
  while (true) {
    if (availableForRead == 0) {
      awaitContent()
      if (availableForRead == 0) {
        break
      }
    }
    val toRead = this.availableForRead.coerceAtMost(byteArray.size)
    val read = readAvailable(byteArray, 0, toRead)
    buffer.write(byteArray, 0, read)
  }
  return buffer
}

private fun GraphQLResponse.toByteArray(): ByteArray {
  val buffer = Buffer()
  serialize(buffer)
  return buffer.readByteArray()
}

suspend fun ApplicationRequest.parseAsGraphQLRequest(): Result<GraphQLRequest> {
  return when (httpMethod) {
    HttpMethod.Post -> receiveChannel().buffer().parseAsGraphQLRequest()
    HttpMethod.Get -> queryString().parseAsGraphQLRequest()
    else -> Result.failure(Exception("Unhandled method: $httpMethod"))
  }
}

fun Application.apolloModule(
  executableSchema: ExecutableSchema,
  path: String = "/graphql",
  executionContext: (RoutingRequest) -> ExecutionContext = { ExecutionContext.Empty }
) {
  routing {
    post(path) {
      call.respondGraphQL(executableSchema, executionContext(call.request))
    }
    get(path) {
      call.respondGraphQL(executableSchema, executionContext(call.request))
    }
  }
}

fun Application.apolloSandboxModule(
  title: String = "API sandbox",
  sandboxPath: String = "/sandbox",
  graphqlPath: String = "/graphql",
) {
  routing {
    get(Regex("/sandbox/?")) {
      call.respondRedirect(call.url { path("/sandbox/index.html") }, permanent = true)
    }
    get("$sandboxPath/index.html") {
      val initialEndpoint = call.url {
        /**
         * Trying to guess if the client connected through HTTPS
         */
        val proto = call.request.header("x-forwarded-proto")
        when (proto) {
          "http" -> protocol = URLProtocol.HTTP
          "https" -> protocol = URLProtocol.HTTPS
        }
        path(graphqlPath)
      }
      call.respondText(sandboxHtml(title, initialEndpoint), ContentType.parse("text/html"))
    }
  }
}