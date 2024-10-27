# Apollo Federation

Apollo Kotlin Execution supports [Apollo Federation](https://www.apollographql.com/federation).

To use federation, add the `apollo-execution-federation` artifact to your project:

```kotlin
dependencies {
  // Add the federation dependency
  implementation("com.apollographql.execution:apollo-execution-federation:%latest_version%")
}
```

The `apollo-execution-federation` artifact contains the `@GraphQLKey` annotation allowing you to define [entities](https://www.apollographql.com/docs/graphos/schema-design/federated-schemas/entities/intro).

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