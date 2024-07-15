# Ktor bindings

For a fully functional Ktor server, 

```kotlin
dependencies {
  // Add the runtime dependency
  implementation("com.apollographql.execution:apollo-execution-runtime:%latest_version%")
  // This sample uses netty as an engine.
  // See https://ktor.io/ for other choices.
  implementation("io.ktor:ktor-server-netty:2.3.11")
}
```


In addition to `schema.graphqls`, Apollo Execution generates an `ExecutableSchemaBuilder` preconfigured with hardwired resolvers that you can use to configure your server:

```kotlin
fun main() {
    embeddedServer(Netty, port = 8080) {
        // /graphql route
        apolloModule(ServiceExecutableSchemaBuilder().build())
        // /sandbox/index.html route
        apolloSandboxModule()
    }.start(wait = true)
}
```

Your server is ready!

Open [`http://localhost:8080/sandbox/index.html`](http://localhost:8080/sandbox/index.html) and try out your API in the [Apollo sandbox](https://www.apollographql.com/docs/graphos/explorer/sandbox/)

[![Apollo Sandbox](sandbox.png)](http://localhost:8080/sandbox/index.html)