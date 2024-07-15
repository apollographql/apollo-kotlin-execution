package com.example

import com.apollographql.execution.http4k.apolloHandler
import com.apollographql.execution.http4k.apolloSandboxHandler
import org.http4k.routing.routes
import org.http4k.server.Netty
import org.http4k.server.asServer


fun main() {
  val executableSchema = ServiceExecutableSchemaBuilder().build()
  apolloHandler(executableSchema)
    .asServer(Netty(8000))
    .start()
    .block()
}