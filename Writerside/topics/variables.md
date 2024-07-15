# Passing variables to resolver functions

By default, Kotlin parameters are exposed as GraphQL arguments:

```kotlin
@GraphQLQuery
class Query {
  fun hello(name: String): String {
    return "Hello $name"
  }
}
```

Becomes this in GraphQL:

```graphql
type Query {
    hello(name: String): String
}
```

