package com.example

import com.apollographql.execution.http4k.apolloHandler
import org.http4k.server.Netty
import org.http4k.server.asServer


fun main() {
  val executableSchema = ServiceExecutableSchemaBuilder().build()
  apolloHandler(executableSchema)
    .asServer(Netty(8000))
    .start()
    .block()
}