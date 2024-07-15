# Execution context

Kotlin function may define an additional `ExecutionContext` parameter. This parameter is not exposed in GraphQL and can be used to pass additional context to your graph: 

```kotlin
@GraphQLQuery
class Query {
  fun hello(context: ExecutionContext): String {
    // Do something with context
    return "Hello world"
  }
}
```

Becomes this in GraphQL:

```graphql
type Query {
    # this field has no arguments
    hello: String
}
```

