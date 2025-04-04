# Ktor


> See [sample-ktor](https://github.com/apollographql/apollo-kotlin-execution/tree/main/sample-ktor) for a project using the Ktor integration. 

## Add apollo-execution-ktor to your project

> Make sure to follow the steps in ["Getting started"](getting-started.md) first to generate your `ServiceExecutableSchemaBuilder`.

To use the Ktor integration, add `apollo-execution-ktor` to your dependencies and a Ktor engine:

```kotlin
dependencies {
  // Add the runtime dependency
  implementation("com.apollographql.execution:apollo-execution-ktor:%latest_version%")
  // This sample uses netty as an engine.
  // See https://ktor.io/ for other choices.
  implementation("io.ktor:ktor-server-netty:3.0.0")
}
```

`apollo-execution-ktor` provides 3 modules:

- `apolloModule(ExecutableSchema)` adds the main `/graphql` route for queries/mutations.
- `apolloSubscriptionModule(ExecutableSchema)` adds the `/subscription` route for subscriptions.
- `apolloSandboxModule(ExecutableSchema)` adds the `/sandbox/index.html` for the online IDE

```kotlin
embeddedServer(Netty, port = 8080) {
  val executableSchema = ServiceExecutableSchemaBuilder().build()
  // /graphql route
  apolloModule(executableSchema)
  // /subscription route
  apolloSubscriptionModule(executableSchema)
  // /sandbox/index.html route
  apolloSandboxModule()
}.start(wait = true)
```
