package com.apollographql.execution.http4k

import com.apollographql.apollo.api.ExecutionContext
import com.apollographql.execution.ExecutableSchema
import com.apollographql.execution.parseGraphQLRequest
import com.apollographql.execution.parseUrlToGraphQLRequest
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
import okio.Buffer
import okio.buffer
import okio.source
import org.http4k.core.HttpHandler
import org.http4k.core.MemoryBody
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.websocket.WsMessage
import org.http4k.websocket.*


internal class GraphQLHttpHandler(private val executableSchema: ExecutableSchema, private val executionContext: ExecutionContext) : HttpHandler {
  override fun invoke(request: Request): Response {

    val graphQLRequestResult = when (request.method) {
      Method.GET -> request.uri.toString().parseUrlToGraphQLRequest()
      Method.POST -> request.body.stream.source().buffer().use { it.parseGraphQLRequest() }
      else -> error("")
    }

    if (graphQLRequestResult.isFailure) {
      return Response(BAD_REQUEST).body(graphQLRequestResult.exceptionOrNull()!!.message!!)
    }

    val response = executableSchema.execute(graphQLRequestResult.getOrThrow(), executionContext)

    val buffer = Buffer()
    response.serialize(buffer)
    val responseText = buffer.readUtf8()

    return Response(OK)
        .header("content-type", "application/json")
        .body(responseText)
  }
}


fun apolloWebSocketHandler(executableSchema: ExecutableSchema, executionContext: (Websocket) -> ExecutionContext): WsHandler {
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

          shouldReturn != null && shouldReturn.toString().startsWith("close") -> {
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
      WsMessage(MemoryBody(data))
    }

    is WebSocketTextMessage -> {
      WsMessage(data)
    }
  }
}

fun apolloHandler(
    executableSchema: ExecutableSchema,
    path: String = "/graphql",
    executionContext: ExecutionContext = ExecutionContext.Empty,
): RoutingHttpHandler {
  return routes(
      path bind Method.GET to GraphQLHttpHandler(executableSchema, executionContext),
      path bind Method.POST to GraphQLHttpHandler(executableSchema, executionContext)
  )
}

fun apolloSandboxHandler(): HttpHandler {
  return object : HttpHandler {
    override fun invoke(request: Request): Response {
      return Response(OK).body(javaClass.classLoader!!.getResourceAsStream("sandbox.html")!!)
    }
  }
}