package com.apollographql.execution.websocket

import CurrentSubscription
import com.apollographql.apollo.annotations.ApolloInternal
import com.apollographql.apollo.api.Error
import com.apollographql.apollo.api.Error.*
import com.apollographql.apollo.api.ExecutionContext
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.api.json.*
import com.apollographql.apollo.execution.*
import com.apollographql.execution.jsonWriter
import com.apollographql.execution.writeError
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okio.Buffer
import okio.Sink

/**
 * A [WebSocketHandler] that implements https://github.com/enisdenjo/graphql-ws/blob/0c0eb499c3a0278c6d9cc799064f22c5d24d2f60/PROTOCOL.md
 */
class GraphQLWsWebSocketHandler(
    private val executableSchema: ExecutableSchema,
    private val scope: CoroutineScope,
    private val executionContext: ExecutionContext,
    private val sendMessage: suspend (WebSocketMessage) -> Unit,
    private val connectionInitHandler: WsConnectionInitHandler = { WsConnectionInitAck },
) : WebSocketHandler {
  private val lock = reentrantLock()
  private val activeSubscriptions = mutableMapOf<String, Job>()
  private var isClosed: Boolean = false
  private var initJob: Job? = null

  override fun handleMessage(message: WebSocketMessage) {
    val clientMessage = when (message) {
      is WebSocketBinaryMessage -> message.data.decodeToString()
      is WebSocketTextMessage -> message.data
    }.parseApolloWebsocketClientMessage()

    when (clientMessage) {
      is Init -> {
        initJob = lock.withLock {
          scope.launch {
            when(val result = connectionInitHandler.invoke(clientMessage.connectionParams)) {
              is WsConnectionInitAck -> {
                sendMessage(ConnectionAck.toWsMessage())
              }
              is WsConnectionInitError -> {
                sendMessage(ConnectionError(result.payload).toWsMessage())
              }
            }
          }
        }
      }

      is Subscribe -> {
        val isActive = lock.withLock {
          activeSubscriptions.containsKey(clientMessage.id)
        }
        if (isActive) {
          scope.launch {
            sendMessage(Error(id = clientMessage.id, error = Builder("Subscription ${clientMessage.id} is already active").build()).toWsMessage())
          }
          return
        }

        val flow = executableSchema.subscribe(clientMessage.request, executionContext + CurrentSubscription(clientMessage.id))

        val job = scope.launch {
          flow.collect {
            when (it) {
              is SubscriptionResponse -> {
                sendMessage(Data(id = clientMessage.id, response = it.response).toWsMessage())
              }

              is SubscriptionError -> {
                sendMessage(Error(id = clientMessage.id, error = it.errors.first()).toWsMessage())
              }
            }
            sendMessage(Complete(id = clientMessage.id).toWsMessage())
          }
          sendMessage(Complete(id = clientMessage.id).toWsMessage())
          lock.withLock {
            activeSubscriptions.remove(clientMessage.id)?.cancel()
          }
        }

        lock.withLock {
          activeSubscriptions.put(clientMessage.id, job)
        }
      }

      is Complete -> {
        lock.withLock {
          activeSubscriptions.remove(clientMessage.id)?.cancel()
        }
      }

      is ParseError -> {
        scope.launch {
          sendMessage(Error(null, Builder("Cannot handle message (${clientMessage.message})").build()).toWsMessage())
        }
      }

      Ping -> {
        scope.launch {
          sendMessage(Pong.toWsMessage())
        }
      }
      Pong -> {

      }
    }
  }

  fun close() {
    lock.withLock {
      if (isClosed) {
        return
      }

      activeSubscriptions.forEach {
        it.value.cancel()
      }
      activeSubscriptions.clear()

      initJob?.cancel()
      isClosed = true
    }
  }
}

private sealed interface MessageResult

private class ParseError(
    val message: String,
) : MessageResult

private sealed interface ClientMessage : MessageResult

private class Init(
    val connectionParams: Any?,
) : ClientMessage

private class Subscribe(
    val id: String,
    val request: GraphQLRequest,
) : ClientMessage

private class Complete(
    val id: String,
) : ClientMessage, ServerMessage {
  override fun serialize(sink: Sink) {
    sink.writeMessage("complete") {
      name("id")
      value(id)
    }
  }
}

private data object Ping : ClientMessage, ServerMessage {
  override fun serialize(sink: Sink) {
    sink.writeMessage("ping")
  }
}

private data object Pong : ClientMessage, ServerMessage {
  override fun serialize(sink: Sink) {
    sink.writeMessage("pong")
  }
}

private sealed interface ServerMessage {
  fun serialize(sink: Sink)
}

private fun Sink.writeMessage(type: String, block: (JsonWriter.() -> Unit)? = null) {
  jsonWriter().apply {
    writeObject {
      name("type")
      value(type)
      block?.invoke(this)
    }
    flush()
  }
}

private data object ConnectionAck : ServerMessage {
  override fun serialize(sink: Sink) {
    sink.writeMessage("connection_ack")
  }
}

private class ConnectionError(private val payload: Optional<Any?>) : ServerMessage {
  override fun serialize(sink: Sink) {
    sink.writeMessage("connection_error") {
      if (payload is Optional.Present<*>) {
        name("payload")
        writeAny(payload.value)
      }
    }
  }
}

private class Data(
    val id: String,
    val response: GraphQLResponse,
) : ServerMessage {
  override fun serialize(sink: Sink) {
    sink.writeMessage("data") {
      name("id")
      value(id)
      name("payload")
      response.serialize(this)
    }
  }
}

private class Error(
    val id: String?,
    val error: Error,
) : ServerMessage {

  override fun serialize(sink: Sink) {
    sink.writeMessage("error") {
      if (id != null) {
        name("id")
        value(id)
      }
      name("payload")
      writeError(error)
    }
  }
}

@OptIn(ApolloInternal::class)
private fun String.parseApolloWebsocketClientMessage(): MessageResult {
  @Suppress("UNCHECKED_CAST")
  val map = try {
    Buffer().writeUtf8(this).jsonReader().readAny() as Map<String, Any?>
  } catch (e: Exception) {
    return ParseError("Malformed Json: ${e.message}")
  }

  val type = map["type"]
  if (type == null) {
    return ParseError("No 'type' found in $this")
  }
  if (type !is String) {
    return ParseError("'type' must be a String in $this")
  }

  when (type) {
    "subscribe", "complete" -> {
      val id = map["id"]
      if (id == null) {
        return ParseError("No 'id' found in $this")
      }

      if (id !is String) {
        return ParseError("'id' must be a String in $this")
      }

      if (type == "subscribe") {
        val payload = map["payload"]
        if (payload == null) {
          return ParseError("No 'payload' found in $this")
        }
        if (payload !is Map<*, *>) {
          return ParseError("'payload' must be an Object in $this")
        }

        @Suppress("UNCHECKED_CAST")
        val request = (payload as Map<String, Any?>).parseAsGraphQLRequest()
        return request.fold(
            onFailure = { ParseError("Cannot parse subscribe payload: '${it.message}'") },
            onSuccess = { Subscribe(id, request = it) }
        )
      } else {
        return Complete(id)
      }
    }
    "ping" -> {
      return Ping
    }
    "pong" -> {
      return Pong
    }
    "connection_init" -> {
      return Init(map["payload"])
    }

    else -> return ParseError("Unknown message type '$type'")
  }
}

private fun ServerMessage.toWsMessage(): WebSocketMessage {
  return WebSocketTextMessage(Buffer().apply { serialize(this) }.readUtf8())
}

sealed interface WsConnectionInitResult
data object WsConnectionInitAck : WsConnectionInitResult
class WsConnectionInitError(val payload: Optional<Any?> = Optional.absent()): WsConnectionInitResult

typealias WsConnectionInitHandler = suspend (Any?) -> WsConnectionInitResult
