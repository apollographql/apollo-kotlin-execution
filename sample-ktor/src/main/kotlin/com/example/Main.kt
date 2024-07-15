package com.example

import com.apollographql.execution.ktor.apolloModule
import com.apollographql.execution.ktor.apolloSandboxModule
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    embeddedServer(Netty, port = 8080) {
        // configures /graphql route
        apolloModule(ServiceExecutableSchemaBuilder().build())
        // configures /sandbox/* route
        apolloSandboxModule()
    }.start(wait = true)
}