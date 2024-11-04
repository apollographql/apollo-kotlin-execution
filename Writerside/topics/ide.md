# GraphQL IDE

One of GraphQL strong points is tooling. 

Apollo Kotlin Execution comes with a built-in IDE that makes it easy to explore your schema and debug your operations.

You can get a ready-to-serve version of [Apollo Sandbox](https://studio.apollographql.com/sandbox/explorer/) using the `sandboxHtml()` function:

```kotlin
val pageTitle = "Welcome to my API"
val initialEndpoint = "http://localhost:8080/graphql"

sandboxHtml(title = pageTitle, initialEndpoint = initialEndpoint)
```

[![Apollo Sandbox](sandbox.png)](http://localhost:8080/sandbox/index.html)

You can then serve it using your favorite server or use any of the existing integrations: 

* [Spring](spring.md)
* [Ktor](ktor.md)
* [http4k](http4k.md)