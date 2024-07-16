# Welcome

Apollo Kotlin Execution is a code-first GraphQL execution library.

Features:

* Generates a GraphQL schema from your Kotlin code: write Kotlin, get a typesafe API.
* Doesn't use reflection. Use it on the JVM and enjoy ultra-fast start times. Or use it with Kotlin native. Apollo Kotlin Execution is KMP-ready!
* Supports custom scalars, subscriptions, persisted queries and everything in the current [GraphQL draft](https://spec.graphql.org/draft/).
* Integration with Ktor and http4k.

Under the hood, Apollo Kotlin Execution uses [KSP](https://kotlinlang.org/docs/ksp-overview.html) to generate GraphQL resolvers and types from your Kotlin code.

## Getting started

### Gradle configuration

Add the `com.apollographql.execution` Gradle plugin and dependencies to your build script:

```kotlin
// build.gradle.kts
plugins {
  // Kotlin and KSP are required
  id("org.jetbrains.kotlin.jvm").version(kotlinVersion)
  id("com.google.devtools.ksp").version(kspVersion)
  // Add the Apollo Kotlin Execution plugin
  id("com.apollographql.execution").version("%latest_version%")
}

dependencies {
  // Add the runtime dependency
  implementation("com.apollographql.execution:apollo-execution-runtime:%latest_version%")
}

// Configure codegen
apolloExecution {
  service("service") {
    packageName = "com.example"
  }
}
```

### Define your root query

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


### Execute your query

The codegen generates a `com.example.ServiceExecutableSchemaBuilder` class that is the entry point to execute GraphQL requests:

```kotlin
val executableSchema = ServiceExecutableSchemaBuilder()
  .build()
```

You can then create a GraphQL request:
```kotlin
val request = GraphQLRequest.Builder()
  .document("{ hello(name: \"sample\") }")
  .build()
```

And execute it:
```kotlin
val response = executableSchema.execute(
  request,
  ExecutionContext.Empty
)

println(response.data)
// {hello=Hello sample}
```

Apollo Kotlin Execution supports objects, interfaces, unions, enum, input objects, deprecation, customizing names and descriptions and more. 

For details, see the [Generating a schema](schema.md) page.

## 3rd party bindings

The Apollo Kotlin Execution runtime is network agnostic but for convenience, it comes with [bindings](bindings.md) for:

* Ktor ([documentation](ktor.md))
* http4k ([documentation](http4k.md))

See the respective documentation for how to configure a server with an `ExecutableSchema`