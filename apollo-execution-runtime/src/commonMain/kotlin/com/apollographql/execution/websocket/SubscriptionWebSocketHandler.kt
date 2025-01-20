package com.apollographql.execution.websocket

import com.apollographql.apollo.annotations.ApolloInternal
import com.apollographql.apollo.api.Error
import com.apollographql.apollo.api.ExecutionContext
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.api.json.BufferedSinkJsonWriter
import com.apollographql.apollo.api.json.JsonWriter
import com.apollographql.apollo.api.json.jsonReader
import com.apollographql.apollo.api.json.readAny
import com.apollographql.apollo.api.json.writeAny
import com.apollographql.apollo.api.json.writeArray
import com.apollographql.apollo.api.json.writeObject
import com.apollographql.apollo.execution.ExecutableSchema
import com.apollographql.apollo.execution.GraphQLRequest
import com.apollographql.apollo.execution.GraphQLResponse
import com.apollographql.apollo.execution.SubscriptionError
import com.apollographql.apollo.execution.SubscriptionResponse
import com.apollographql.apollo.execution.parseAsGraphQLRequest
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okio.Buffer
import okio.BufferedSink
import okio.Sink
import okio.buffer

/**
 * A [WebSocketHandler] that implements https://github.com/apollographql/subscriptions-transport-ws/blob/master/PROTOCOL.md
 */
class SubscriptionWebSocketHandler(
    private val executableSchema: ExecutableSchema,
    private val scope: CoroutineScope,
    private val executionContext: ExecutionContext,
    private val sendMessage: suspend (WebSocketMessage) -> Unit,
    private val connectionInitHandler: ConnectionInitHandler = { ConnectionInitAck },
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
      is SubscriptionWebsocketInit -> {
        initJob = lock.withLock {
          scope.launch {
            when(val result = connectionInitHandler.invoke(clientMessage.connectionParams)) {
              is ConnectionInitAck -> {
                sendMessage(SubscriptionWebsocketConnectionAck.toWsMessage())
              }
              is ConnectionInitError -> {
                sendMessage(SubscriptionWebsocketConnectionError(result.payload).toWsMessage())
              }
            }
          }
        }
      }

      is SubscriptionWebsocketStart -> {
        val isActive = lock.withLock {
          activeSubscriptions.containsKey(clientMessage.id)
        }
        if (isActive) {
          scope.launch {
            sendMessage(SubscriptionWebsocketError(id = clientMessage.id, error = Error.Builder("Subscription ${clientMessage.id} is already active").build()).toWsMessage())
          }
          return
        }

        val flow = executableSchema.subscribe(clientMessage.request, executionContext + CurrentSubscription(clientMessage.id))

        val job = scope.launch {
          flow.collect {
            when (it) {
              is SubscriptionResponse -> {
                sendMessage(SubscriptionWebsocketData(id = clientMessage.id, response = it.response).toWsMessage())
              }

              is SubscriptionError -> {
                sendMessage(SubscriptionWebsocketError(id = clientMessage.id, error = it.errors.first()).toWsMessage())
              }
            }
          }
          sendMessage(SubscriptionWebsocketComplete(id = clientMessage.id).toWsMessage())
          lock.withLock {
            activeSubscriptions.remove(clientMessage.id)?.cancel()
          }
        }

        lock.withLock {
          activeSubscriptions.put(clientMessage.id, job)
        }
      }

      is SubscriptionWebsocketStop -> {
        lock.withLock {
          activeSubscriptions.remove(clientMessage.id)?.cancel()
        }
      }

      SubscriptionWebsocketTerminate -> {
        // nothing to do
      }

      is SubscriptionWebsocketClientMessageParseError -> {
        scope.launch {
          sendMessage(SubscriptionWebsocketError(null, Error.Builder("Cannot handle message (${clientMessage.message})").build()).toWsMessage())
        }
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



internal sealed interface SubscriptionWebsocketClientMessageResult

internal class SubscriptionWebsocketClientMessageParseError internal constructor(
    val message: String,
) : SubscriptionWebsocketClientMessageResult

internal sealed interface SubscriptionWebsocketClientMessage : SubscriptionWebsocketClientMessageResult

internal class SubscriptionWebsocketInit(
    val connectionParams: Any?,
) : SubscriptionWebsocketClientMessage

internal class SubscriptionWebsocketStart(
    val id: String,
    val request: GraphQLRequest,
) : SubscriptionWebsocketClientMessage

internal class SubscriptionWebsocketStop(
    val id: String,
) : SubscriptionWebsocketClientMessage


internal object SubscriptionWebsocketTerminate : SubscriptionWebsocketClientMessage

internal sealed interface SubscriptionWebsocketServerMessage {
  fun serialize(sink: Sink)
}

internal fun Sink.jsonWriter(): JsonWriter = BufferedSinkJsonWriter(if (this is BufferedSink) this else this.buffer())

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

internal data object SubscriptionWebsocketConnectionAck : SubscriptionWebsocketServerMessage {
  override fun serialize(sink: Sink) {
    sink.writeMessage("connection_ack")
  }
}

internal class SubscriptionWebsocketConnectionError(private val payload: Optional<Any?>) : SubscriptionWebsocketServerMessage {
  override fun serialize(sink: Sink) {
    sink.writeMessage("connection_error") {
      if (payload is Optional.Present<*>) {
        name("payload")
        writeAny(payload.value)
      }
    }
  }
}

internal class SubscriptionWebsocketData(
    val id: String,
    val response: GraphQLResponse,
) : SubscriptionWebsocketServerMessage {
  override fun serialize(sink: Sink) {
    sink.writeMessage("data") {
      name("id")
      value(id)
      name("payload")
      response.serialize(this)
    }
  }
}

internal class SubscriptionWebsocketError(
    val id: String?,
    val error: Error,
) : SubscriptionWebsocketServerMessage {

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

internal class SubscriptionWebsocketComplete(
    val id: String,
) : SubscriptionWebsocketServerMessage {
  override fun serialize(sink: Sink) {
    sink.writeMessage("complete") {
      name("id")
      value(id)
    }
  }
}

@OptIn(ApolloInternal::class)
internal fun String.parseApolloWebsocketClientMessage(): SubscriptionWebsocketClientMessageResult {
  @Suppress("UNCHECKED_CAST")
  val map = try {
    Buffer().writeUtf8(this).jsonReader().readAny() as Map<String, Any?>
  } catch (e: Exception) {
    return SubscriptionWebsocketClientMessageParseError("Malformed Json: ${e.message}")
  }

  val type = map["type"]
  if (type == null) {
    return SubscriptionWebsocketClientMessageParseError("No 'type' found in $this")
  }
  if (type !is String) {
    return SubscriptionWebsocketClientMessageParseError("'type' must be a String in $this")
  }

  when (type) {
    "start", "stop" -> {
      val id = map["id"]
      if (id == null) {
        return SubscriptionWebsocketClientMessageParseError("No 'id' found in $this")
      }

      if (id !is String) {
        return SubscriptionWebsocketClientMessageParseError("'id' must be a String in $this")
      }

      if (type == "start") {
        val payload = map["payload"]
        if (payload == null) {
          return SubscriptionWebsocketClientMessageParseError("No 'payload' found in $this")
        }
        if (payload !is Map<*, *>) {
          return SubscriptionWebsocketClientMessageParseError("'payload' must be an Object in $this")
        }

        @Suppress("UNCHECKED_CAST")
        val request = (payload as Map<String, Any?>).parseAsGraphQLRequest()
        return request.fold(
            onFailure = { SubscriptionWebsocketClientMessageParseError("Cannot parse start payload: '${it.message}'") },
            onSuccess = { SubscriptionWebsocketStart(id, request = it) }
        )
      } else {
        return SubscriptionWebsocketStop(id)
      }
    }

    "connection_init" -> {
      return SubscriptionWebsocketInit(map["payload"])
    }

    "connection_terminate" -> {
      return SubscriptionWebsocketTerminate
    }

    else -> return SubscriptionWebsocketClientMessageParseError("Unknown message type '$type'")
  }
}

private fun SubscriptionWebsocketServerMessage.toWsMessage(): WebSocketMessage {
  return WebSocketTextMessage(Buffer().apply { serialize(this) }.readUtf8())
}

sealed interface ConnectionInitResult
data object ConnectionInitAck : ConnectionInitResult
class ConnectionInitError(val payload: Optional<Any?> = Optional.absent()): ConnectionInitResult

typealias ConnectionInitHandler = suspend (Any?) -> ConnectionInitResult

private class CurrentSubscription(val id: String) : ExecutionContext.Element {

  override val key: ExecutionContext.Key<CurrentSubscription> = Key

  companion object Key : ExecutionContext.Key<CurrentSubscription>
}

fun ExecutionContext.subscriptionId(): String = get(CurrentSubscription)?.id ?: error("Apollo: not executing a subscription")

internal fun JsonWriter.writeError(error: Error) {
  writeObject {
    name("message")
    value(error.message)
    if (error.locations != null) {
      name("locations")
      writeArray {
        error.locations!!.forEach {
          writeObject {
            name("line")
            value(it.line)
            name("column")
            value(it.column)
          }
        }
      }
    }
    if (error.path != null) {
      name("path")
      writeArray {
        error.path!!.forEach {
          when (it) {
            is Int -> value(it)
            is String -> value(it)
            else -> error("path can only contain Int and Double (found '${it::class.simpleName}')")
          }
        }
      }
    }
    if (error.extensions != null) {
      name("extensions")
      writeObject {
        error.extensions!!.entries.forEach {
          name(it.key)
          writeAny(it.value)
        }
      }
    }
  }
}