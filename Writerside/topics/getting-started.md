# Getting started

> This project is experimental and provided as-is. Use at your own risk.
{style=warning}

Apollo Kotlin Execution is an [implementation-first](https://jordaneldredge.com/implementation-first/) GraphQL execution library.

Apollo Kotlin Execution:

* Generates a GraphQL schema from your Kotlin code: write Kotlin, get a typesafe API.
* Doesn't use reflection. Use it on the JVM and enjoy ultra-fast start times. Or use it with Kotlin native. Apollo Kotlin Execution is KMP-ready!
* Supports custom scalars, subscriptions, persisted queries and everything in the current [GraphQL draft](https://spec.graphql.org/draft/).
* Integrates with [Ktor](ktor.md), [http4k](http4k.md) and [Spring](spring.md).
* Supports [Apollo Federation](federation.md).

Under the hood, Apollo Kotlin Execution uses [KSP](https://kotlinlang.org/docs/ksp-overview.html) to generate GraphQL resolvers and types from your Kotlin code.

## Gradle configuration

Apollo Kotlin Execution comes with a Gradle plugin that:

* Configures KSP:
  * Adds `apollo-execution-processor` to the KSP configuration.
  * Configure `service` & `packageName` KSP arguments.
* Configures dependency resolution to align Apollo Kotlin Execution versions if none is specified.
* Adds `apolloCheckSchema` and `apolloDumpSchema` tasks (see [Monitoring the generated schema](schema-dump.md))

Configure your Gradle build:

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
  implementation("com.apollographql.execution:apollo-execution-runtime")
}

// Configure codegen
apolloExecution {
  service("service") {
    packageName = "com.example"
  }
}
```

> Apollo Kotlin Execution requires Java 17
{style=note}

Disable KSP for tests ([doc](https://kotlinlang.org/docs/ksp-multiplatform.html#avoid-the-ksp-configuration-on-ksp-1-0-1)):

```
ksp.allow.all.target.configuration=false
```

## Define your root query

Write your root query class in a `Query.kt` file:

```kotlin
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
# or if multiplatform
./gradlew kspCommonMainKotlinMetadata
```

## Execute your query

The codegen generates a `com.example.ServiceExecutableSchemaBuilder` class that is the entry point to execute GraphQL requests:

```kotlin
val executableSchema = ServiceExecutableSchemaBuilder()
  .build()
```

Create a GraphQL request:
```kotlin
val request = GraphQLRequest.Builder()
  .document("{ hello(name: \"sample\") }")
  .build()
```

Execute the GraphQL request:
```kotlin
val response = executableSchema.execute(
  request,
  ExecutionContext.Empty
)

println(response.data)
// {hello=Hello sample}
```

Apollo Kotlin Execution supports objects, interfaces, unions, enum, input objects, deprecation, customizing names and descriptions and more. 

See the [Generating a schema](schema.md) page for more details.

## Integrate with your favorite server library

Apollo Kotlin Execution comes with integrations for popular server libraries:

* [Ktor integration](ktor.md)
* [http4k integration](http4k.md)
* [Spring integration](spring.md)

See the respective documentation for how to configure a server with an `ExecutableSchema`.
