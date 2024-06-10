package com.apollographql.apollo3.execution.websocket

sealed interface WebSocketMessage
class WebSocketTextMessage(val data: String): WebSocketMessage
class WebSocketBinaryMessage(val data: ByteArray): WebSocketMessage

interface WebSocketHandler {
  fun handleMessage(message: WebSocketMessage)
}