# Ktor


> See [sample-ktor](https://github.com/apollographql/apollo-kotlin-execution/tree/main/sample-ktor) for a project using the Ktor integration. 

To use the Ktor integration, add `apollo-execution-ktor` to your dependencies and a Ktor engine:

```kotlin
dependencies {
  // Add the runtime dependency
  implementation("com.apollographql.execution:apollo-execution-ktor:%latest_version%")
  // This sample uses netty as an engine.
  // See https://ktor.io/ for other choices.
  implementation("io.ktor:ktor-server-netty:2.3.11")
}
```

`apollo-execution-ktor` provides an `apolloModule(ExecutableSchema)` function that adds a `/graphql` route to your application:

```kotlin
embeddedServer(Netty, port = 8080) {
  // /graphql route
  apolloModule(ServiceExecutableSchemaBuilder().build())
}.start(wait = true)
```

You can also opt in the Apollo Sandbox route by using `apolloSandboxModule()`

```kotlin
embeddedServer(Netty, port = 8080) {
  // /sandbox/index.html route
  apolloSandboxModule()
}
```

`apolloSandboxModule()` adds a `sandbox/index.html` route to your application.

Open [`http://localhost:8080/sandbox/index.html`](http://localhost:8080/sandbox/index.html) and try out your API in the [Apollo sandbox](https://www.apollographql.com/docs/graphos/explorer/sandbox/)

[![Apollo Sandbox](sandbox.png)](http://localhost:8080/sandbox/index.html)