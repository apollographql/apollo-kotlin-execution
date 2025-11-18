@file:Suppress("RemoveSingleExpressionStringTemplate")

package com.apollographql.execution.http4k

import com.apollographql.apollo.api.ExecutionContext
import com.apollographql.apollo.execution.ExecutableSchema
import com.apollographql.apollo.execution.parseAsGraphQLRequest
import com.apollographql.execution.sandboxHtml
import com.apollographql.execution.websocket.ConnectionInitAck
import com.apollographql.execution.websocket.ConnectionInitError
import com.apollographql.execution.websocket.ConnectionInitHandler
import com.apollographql.execution.websocket.SubscriptionWebSocketHandler
import com.apollographql.execution.websocket.WebSocketBinaryMessage
import com.apollographql.execution.websocket.WebSocketMessage
import com.apollographql.execution.websocket.WebSocketTextMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.buffer
import okio.source
import org.http4k.core.*
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.PERMANENT_REDIRECT
import org.http4k.lens.Header
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.websocket.WsMessage
import org.http4k.websocket.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

fun apolloHandler(
  executableSchema: ExecutableSchema,
  coroutineContext: (Request) -> CoroutineContext = { EmptyCoroutineContext },
  executionContext: (Request) -> ExecutionContext = { ExecutionContext.Empty }
) : HttpHandler {
  return { request ->
    val graphQLRequestResult = when (request.method) {
      Method.GET -> request.uri.query.parseAsGraphQLRequest()
      Method.POST -> request.body.stream.source().buffer().use { it.parseAsGraphQLRequest() }
      else -> error("")
    }

    if (graphQLRequestResult.isFailure) {
      Response(BAD_REQUEST).body(graphQLRequestResult.exceptionOrNull()!!.message!!)
    } else {
      val response = runBlocking(coroutineContext(request)) {
        executableSchema.execute(graphQLRequestResult.getOrThrow(), executionContext(request))
      }

      val buffer = Buffer()
      response.serialize(buffer)
      val responseText = buffer.readUtf8()

      Response(OK)
        .header("content-type", "application/json")
        .body(responseText)
    }
  }
}


fun apolloWebSocketHandler(executableSchema: ExecutableSchema, executionContext: (Websocket) -> ExecutionContext = { ExecutionContext.Empty }): WsHandler {
  val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
  return { _: Request ->
    WsResponse { ws: Websocket ->

      val connectionInitHandler: ConnectionInitHandler = { connectionParams: Any? ->
        @Suppress("UNCHECKED_CAST")
        val shouldReturn = (connectionParams as? Map<String, Any?>)?.get("return")?.toString()

        when {
          shouldReturn == "error" -> {
            ConnectionInitError()
          }

          shouldReturn != null && shouldReturn.startsWith("close") -> {
            val code = Regex("close\\(([0-9]*)\\)").matchEntire(shouldReturn)
                ?.let { it.groupValues[1].toIntOrNull() }
                ?: 1001

            ws.close(WsStatus(code, "closed"))

            ConnectionInitError()
          }

          else -> {
            ConnectionInitAck
          }
        }
      }

      val sendMessage = { webSocketMessage: WebSocketMessage ->
        ws.send(webSocketMessage.toWsMessage())
      }

      val handler = SubscriptionWebSocketHandler(
          executableSchema = executableSchema,
          scope = scope,
          executionContext = executionContext(ws),
          sendMessage = sendMessage,
          connectionInitHandler = connectionInitHandler
      )

      ws.onMessage {
        handler.handleMessage(WebSocketTextMessage(it.body.payload.array().decodeToString()))
      }
      ws.onClose {
        handler.close()
      }
      ws.onError {
        handler.close()
      }
    }
  }
}

private fun WebSocketMessage.toWsMessage(): WsMessage {
  return when (this) {
    is WebSocketBinaryMessage -> {
      WsMessage(data)
    }

    is WebSocketTextMessage -> {
      WsMessage(data)
    }
  }
}

fun apolloRoutingHttpHandler(
    executableSchema: ExecutableSchema,
    path: String = "/graphql",
    coroutineContext: (Request) -> CoroutineContext = { EmptyCoroutineContext },
    executionContext: (Request) -> ExecutionContext = { ExecutionContext.Empty },
): RoutingHttpHandler {
  return path bind apolloHandler(executableSchema, coroutineContext, executionContext)
}

fun apolloSandboxRoutingHttpHandler(
  title: String = "API sandbox",
  sandboxPath: String = "/sandbox",
  graphqlPath: String = "/graphql",
): RoutingHttpHandler {
  val sanboxHandler: HttpHandler = { request ->
    Response(OK).body(sandboxHtml(title, request.uri.copy(path = graphqlPath).toString()))
  }
  val redirectHandler: HttpHandler = { request ->
    Response(PERMANENT_REDIRECT).with(Header.LOCATION of Uri.of(request.uri.copy(path = "$sandboxPath/index.html").toString()))
  }
  return routes(
    "$sandboxPath/index.html" bind Method.GET to sanboxHandler,
    "$sandboxPath" bind Method.GET to redirectHandler,
    "$sandboxPath/" bind Method.GET to redirectHandler,
    "$sandboxPath/{unused:.*}" bind Method.GET to redirectHandler,
  )
}