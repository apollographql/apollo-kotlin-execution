package com.apollographql.execution.ktor

import com.apollographql.apollo.api.ExecutionContext
import com.apollographql.execution.*
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.path
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.httpMethod
import io.ktor.server.request.queryString
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.util.url
import io.ktor.utils.io.ByteReadChannel
import okio.Buffer

suspend fun ApplicationCall.respondGraphQL(executableSchema: ExecutableSchema, executionContext: ExecutionContext = ExecutionContext.Empty, configure: OutgoingContent.(GraphQLResponse?) -> Unit = {}) {
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
    executionContext: ExecutionContext = ExecutionContext.Empty
) {
  routing {
    post(path) {
      call.respondGraphQL(executableSchema, executionContext)
    }
    get(path) {
      call.respondGraphQL(executableSchema, executionContext)
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
      val initialEndpoint = call.url { path(graphqlPath) }
      call.respondText(sandboxHtml(title, initialEndpoint), ContentType.parse("text/html"))
    }
  }
}