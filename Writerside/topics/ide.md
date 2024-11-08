# IDE

Apollo Kotlin Execution comes with a built-in IDE that makes it easy to explore your schema, write your operation with autocomplete and execute them.

You can get a ready-to-serve version of [Apollo Sandbox](https://studio.apollographql.com/sandbox/explorer/) using the `sandboxHtml()` function:

```kotlin
val pageTitle = "Welcome to my API"
val initialEndpoint = "http://localhost:8080/graphql"

val html = sandboxHtml(title = pageTitle, initialEndpoint = initialEndpoint)

// Expose this graphql to your route of choice, /sandbox for an example
get("/sandbox") {
  respondHtml(html) 
}
```

[![Apollo Sandbox](sandbox.png)](http://localhost:8080/sandbox/index.html)

The builtin integrations provide helper functions for this:

* [Spring](spring.md)
* [Ktor](ktor.md)
* [http4k](http4k.md)