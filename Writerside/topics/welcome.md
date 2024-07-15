# Welcome

Apollo Execution is a code-first GraphQL execution library.

Features:

* Generates a GraphQL schema from your Kotlin code: Write Kotlin, get a typesafe API.
* Doesn't use any reflection. Use it on the JVM and enjoy ultra fast start times. Or use it on Kotlin native. Apollo Execution is KMP-ready!
* Supports custom scalars, subscriptions, persisted queries and aims to support the current [GraphQL draft](https://spec.graphql.org/draft/).

Under the hood, Apollo Execution uses [KSP](https://kotlinlang.org/docs/ksp-overview.html) to generate resolvers and schema information from your Kotlin code.


Use `runtime-ktor` for high level integration with Ktor or just `runtime` for just the codegen.

## Getting started

### Gradle configuration

Add the `com.apollographql.execution` Gradle plugin and dependencies to your build script:

```kotlin
// build.gradle.kts
plugins {
  // Kotlin and KSP are required
  id("org.jetbrains.kotlin.jvm").version("2.0.0")
  id("com.google.devtools.ksp").version("2.0.0-1.0.21")
  // Add the Apollo Execution plugin
  id("com.apollographql.execution").version("%latest_version%")
}

dependencies {
  // Add the runtime dependency
  implementation("com.apollographql.execution:runtime-ktor%latest_version%")
  // This sample uses netty as an engine.
  // See https://ktor.io/ for other choices.
  implementation("io.ktor:ktor-server-netty:2.3.11")
}

// Configure codegen
apolloExecution {
  service("service") {
    packageName = "com.example"
  }
}
```

### Defining your root query

Then write your root query in a `Query.kt` file:

```kotlin
// Define your root query class 
// @GraphQLQuery is the entry point for KSP processing
@GraphQLQuery
class Query {
  // Public functions become GraphQL fields 
  // Kdoc comments become GraphQL descriptions
  // Kotlin parameters become GraphQL arguments
  /**
   * Greeting for name
   */
  fun hello(name: String): String {
    return "Hello $name"
  }
}
```

Run the codegen:

```shell
./gradlew kspKotlin
```

Apollo Execution generates a schema in `graphql/schema.graphqls`:

```graphql
type Query {
  """
   Greeting for name
  """
  hello(name: ID!): ID!
}
```

It is recommended to commit this file in source control so you can track changes made to your schema.

### Writing your server

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


