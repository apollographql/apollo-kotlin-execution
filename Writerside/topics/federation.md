# Apollo Federation

Apollo Kotlin Execution supports [Apollo Federation](https://www.apollographql.com/federation).

To use federation, add the `apollo-execution-subgraph` artifact to your project:

```kotlin
dependencies {
  // Add the federation dependency
  implementation("com.apollographql.execution:apollo-execution-subgraph:%latest_version%")
}
```

The `apollo-execution-subgraph` artifact contains the `@GraphQLKey` annotation allowing you to define [entities](https://www.apollographql.com/docs/graphos/schema-design/federated-schemas/entities/intro).

## Defining entities

You can define an [entity](https://www.apollographql.com/docs/graphos/schema-design/federated-schemas/entities/intro) key using the `@GraphQLKey` annotation:

```kotlin
class Product(
  @GraphQLKey
  val id: String,
  val name: String
) 
```

The `@GraphQLKey` annotation is translated at build time into a matching federation `@key` directive:

```graphql
@key(fields: "id")
type Product {
    id: String!,
    name: String!
}
```

> By adding the annotation on the field definition instead of the type definition, Apollo Kotlin Execution gives you more type safety.
{style="note"}

## Auto-generated meta fields

Whenever a type containing a `@GraphQLKey` field is present, Apollo Kotlin Execution adds the [federation subgraph fields](https://www.apollographql.com/docs/graphos/reference/federation/subgraph-specific-fields), `_service` and `_entities`:

```graphql
# an union containing all the federated types in the schema, constructed at build time 
union _Entity = Product | ...
# coerced as a JSON object containing '__typename' and all the key fields.
scalar _Any

type _Service {
  sdl: String!
}

extend type Query {
  _entities(representations: [_Any!]!): [_Entity]!
  _service: _Service!
}
```

## Defining entity resolvers

In order to support the `_entities` field, federation requires a resolver that can resolve an entity from its key fields.

You can add one by defining a `resolve` function on the companion object:

```kotlin
class Product(
  @GraphQLKey
  val id: String,
  val name: String
) {
  companion object {
    fun resolve(id: String): Product {
      return products.first { it.id == id }
    }
  }
}

val products = listOf(
  Product("1", "foo"),
  Product("2", "bar")
)
```

Just like regular resolvers, the entity resolvers can be suspend and/or have an `ExecutionContext` parameter:

```kotlin
class Product(
  @GraphQLKey
  val id: String,
  val name: String
) {
  companion object {
    suspend fun resolve(executionContext: ExecutionContext, id: String): Product {
      return executionContext.loader.getProdut(id)
    }
  }
}
```

## Tracing (ftv1)

Apollo Kotlin Execution supports [federated tracing](https://www.apollographql.com/docs/federation/v1/metrics) (ftv1).

Ftv1 records timing information for each field and reports that information to the router through the `"ftv1"` extension.

> If you have a monograph, see [usage reporting](usage-reporting.md) instead for how to send tracing information to the Apollo usage reporting endpoint.
{style=note}

To enable federated tracing, configure your `ExecutableSchema` with a `Ftv1Instrumentation` and matching `Ftv1Context`:

```kotlin
// Install the Ftv1Instrumentation in the executable schema
val schema = ServiceExecutableSchemaBuilder()
    .addInstrumentation(Ftv1Instrumentation())
    .build()

// Create a new Ftv1Context() for each operation and use it through execution
val ftv1Context = Ftv1Context()
val response = schema.execute(request, ftv1Context)

// The information is a Base64 encoded protobuf message used by the router
val ftv1 = response.extensions.get("ftv1")
```

Sending the `"ftv1"` extension has some overhead and in real life scenarios, the router uses sampling to save network bandwidth.

This is done using the `"apollo-federation-include-trace"` HTTP header:

```kotlin
val ftv1Context = if (httpHeaders.get("apollo-federation-include-trace") == "ftv1") {
  // The router required tracing information for this request
  Ftv1Context()
} else {
  // No tracing information is required, skip processing
  ExecutionContext.Empty
}
val response = schema.execute(request, ftv1Context)

```

```kotlin

```

