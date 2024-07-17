# http4k

> See [sample-http4k](https://github.com/apollographql/apollo-kotlin-execution/tree/main/sample-http4k) for a project using the http4k integration.

To use the http4k integration, add `apollo-execution-http4k` to your dependencies and the http4k bom:

```kotlin
dependencies {
  // Add the runtime dependency
  implementation("com.apollographql.execution:apollo-execution-http4k:%latest_version%")

  implementation(platform("org.http4k:http4k-bom:5.8.0.0"))
  implementation("org.http4k:http4k-core")
  // This sample uses netty but you can use any other supported backend
  // See https://www.http4k.org/guide/reference/servers/
  implementation("org.http4k:http4k-server-netty")
}
```

`apollo-execution-ktor` provides an `apolloHandler(ExecutableSchema)` function that handles the `/graphql` route:

```kotlin
  val executableSchema = ServiceExecutableSchemaBuilder().build()
apolloHandler(executableSchema)
  .asServer(Netty(8000))
  .start()
  .block()
```

