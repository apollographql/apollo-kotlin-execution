# Custom scalars

You can define a new custom scalar using `@GraphQLScalar`:

<table>
<tr><th>Kotlin</th><th>GraphQL</th></tr>
<tr>
<td>
<code-block lang="kotlin">
@GraphQLScalar(GeoPointCoercing::class)
class GeoPoint(
    val latitude: Double, 
    val longitude: Double
)
</code-block>
</td>
<td>
<code-block lang="graphql">
scalar GeoPoint
</code-block>
</td>
</tr>
</table>

`@GraphQLScalar` can also be added to typealiases for the times when you don't own the target class:

<table>
<tr><th>Kotlin</th><th>GraphQL</th></tr>
<tr>
<td>
<code-block lang="kotlin">
import kotlinx.datetime.LocalDateTime
@GraphQLScalar(DateTimeCoercing::class)
typealias DateTime = LocalDateTime
</code-block>
</td>
<td>
<code-block lang="graphql">
scalar DateTime
</code-block>
</td>
</tr>
</table>

### Implementing the Coercing interface

Each custom scalar requires a matching `Coercing` implementation:

```kotlin
interface Coercing<T> {
  /**
   * Serializes from an internal value (Kotlin) to an external value (typically JSON).
   *
   * For an example Date --> String
   */
  fun serialize(internalValue: T): ExternalValue

  /**
   * Deserializes from an external value (typically JSON) to an internal value (Kotlin).
   *
   * For an example String --> Date
   */
  fun deserialize(value: ExternalValue): T

  /**
   * Parses from a GraphQL value to an internal value (Kotlin)
   */
  fun parseLiteral(gqlValue: GQLValue): T
}
```

`serialize` is needed if your scalar is used in an output position:

```graphql
type User {
    # serialize() is called here to serialize a `GeoPoint` to JSON
    $position: GeoPoint
}
```

`deserialize` is needed if your scalar is used in a variable:

```graphql
query GetUser($position: GeoPoint) {
    # Get all users in a 100m range from the given position 
    usersByPosition(position: $position, radius: 100.0) {}
}
```

`parseLiteral` is needed if your scalar is used as a GraphQL literal:

```graphql
query GetUserInParis {
    # Get all users in a 1km range from the center of Paris 
    usersByPosition(position: { latitude: 48.8588475, longitude: 2.3058358}, radius: 1000.0) {}
}
```

> For an in-depth explanation of the different methods, see [https://www.graphql.de/blog/scalars-in-depth/](https://www.graphql.de/blog/scalars-in-depth/)


With the above, a possible implementation for `GeoPointCoercing` is:

```kotlin
@GraphQLCoercing
class GeoPointCoercing: Coercing<GeoPoint> {
  override fun serialize(internalValue: GeoPoint): ExternalValue {
    return mapOf(
      "latitude" to internalValue.latitude,
      "longitude" to internalValue.longitude
    )
  }

  override fun deserialize(value: ExternalValue): GeoPoint {
    check(value is Map<*,*>) {
      "'$value' cannot be deserialized to a GeoPoint, expected a Map."
    }

    /*
     * Delegate to FloatCoercing to handle the case where the incoming value
     * is an Int
     */
    val latitude = value["latitude"]?.let { FloatCoercing.deserialize(it) }
    val longitude = value["longitude"]?.let { FloatCoercing.deserialize(it) }

    check(latitude != null && longitude != null) {
      "'$value' cannot be deserialized to a GeoPoint, latitude or longitude cannot be coerced to a Double."
    }
    return GeoPoint(
      latitude,
      longitude
    )
  }

  override fun parseLiteral(value: GQLValue): GeoPoint {
    check(value is GQLObjectValue) {
      "'$value' cannot be parsed to a GeoPoint, expected an object."
    }

    /*
     * Delegate to FloatCoercing to handle the case where the incoming value
     * is a GQLIntValue
     */
    val latitude = value.fields.firstOrNull { it.name == "latitude" }?.value?.let { FloatCoercing.parseLiteral(it) }
    val longitude = value.fields.firstOrNull { it.name == "longitude" }?.value?.let { FloatCoercing.parseLiteral(it) }

    check(latitude != null && longitude != null) {
      "'$value' cannot be deserialized to a GeoPoint, latitude or longitude cannot be coerced to a Double."
    }
    return GeoPoint(
      latitude,
      longitude
    )
  }
}
```

### Built-in Coercings

Apollo Kotlin Execution comes with built-in `Coercing` for built-in types that you can reuse:

* `StringCoercing`
* `IntCoercing`
* `FloatCoercing`
* `BooleanCoercing`

### The ID scalar

Despite being a built-in GraphQL scalar, there is no `ID` type in Kotlin. If you want to use `ID` in your schema, you need to define the mapping manually.

Typically, `ID` is mapped either to `Int` or `String`.

Mapping `ID` to `String`:

```kotlin
@GraphQLScalar(StringCoercing)
typealias ID = String
```

Mapping `ID` to `Int`:

```kotlin
@GraphQLScalar(IntCoercing)
typealias ID = Int
```