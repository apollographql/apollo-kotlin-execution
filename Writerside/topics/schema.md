# Generating a schema

Apollo Kotlin Execution uses KSP to parse your Kotlin code and generate a matching GraphQL schema. 

To identify a graph, the Kotlin code must contain exactly one `@GraphQLQuery` class and at most one optional  `@GraphQLMutation` and `@GraphQLSubscription` classes:

```kotlin
// Mandatory top-level Query
@GraphQLQuery
class Query {
  fun userById(id: String): User { }
  // ...
}

// Optional top-level Mutation
@GraphQLMutation
class Mutation { }

// Optional top-level Subscription
@GraphQLSubscription
class Subscription { }
```

From those root classes, the Apollo Kotlin Execution processor traverses the Kotlin class graph and builds the matching GraphQL schema:
* Classes used in output positions are mapped to objects, unions and interfaces.
* Classes used in input positions are mapped to input objects.
* Scalars and Enum can happen in both input and output position.

Whenever possible, the order of fields in the generated GraphQL schema is the same as the declaration order in Kotlin.

> Non-root classes do not need to be annotated and are added to the GraphQL schema automatically.

## Objects and fields

Apollo Kotlin Execution maps public Kotlin classes to GraphQL types and public functions and properties to GraphQL fields with the same name:

<table>
<tr><th>Kotlin</th><th>GraphQL</th></tr>
<tr>
<td>
<code-block lang="kotlin">
class User(val id: String) {
    fun email(): String
}
</code-block>
</td>
<td>
<code-block lang="graphql">
type User {
    id: String!
    email: String!
}
</code-block>
</td>
</tr>
</table>

Private/internal fields and functions are not exposed in GraphQL:

<table>
<tr><th>Kotlin</th><th>GraphQL</th></tr>
<tr>
<td>
<code-block lang="kotlin">
class User {
    fun email(): String
    internal fun sendVerificationEmail()
}
</code-block>
</td>
<td>
<code-block lang="graphql">
type User {
    email: String!
}
</code-block>
</td>
</tr>
</table>

## Unions and interfaces

Non-empty Kotlin interfaces are mapped to GraphQL interfaces:

<table>
<tr><th>Kotlin</th><th>GraphQL</th></tr>
<tr>
<td>
<code-block lang="kotlin">
sealed interface Node {
    id: String
}
class User(
    override val id: String
) : Node 
</code-block>
</td>
<td>
<code-block lang="graphql">
interface Node {
    id: String!
}
type User implements Node {
    id: String!
}
</code-block>
</td>
</tr>
</table>

Empty Kotlin interfaces are mapped to GraphQL unions:

<table>
<tr><th>Kotlin</th><th>GraphQL</th></tr>
<tr>
<td>
<code-block lang="kotlin">
sealed interface Actor 
class User(
    val id: String,
    val email: String
) : Actor
class Organisation(
    val users: List&lt;User&gt;
) : Actor
</code-block>
</td>
<td>
<code-block lang="graphql">
union Actor = User | Organisation
type User {
    val id: String!
    val email: String!
}
type Organisation {
    val users: [User]
}
</code-block>
</td>
</tr>
</table>

> All interfaces, empty or not must be sealed for KSP to be able to traverse the possible implementations.
{style="note"}

## Enums

Kotlin enums are mapped to GraphQL enums:

<table>
<tr><th>Kotlin</th><th>GraphQL</th></tr>
<tr>
<td>
<code-block lang="kotlin">
enum class Role {
    Read,
    Write,
    Admin
}
</code-block>
</td>
<td>
<code-block lang="graphql">
enum Role {
    Read,
    Write,
    Admin
}
</code-block>
</td>
</tr>
</table>

## Scalars

Classes annotated with `@GraphQLScalar` are mapped to GraphQL scalars:

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

You can also map existing classes that you don't own to GraphQL scalars using `typealias`

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

> Scalars require defining an associated Coercing. You can read more in the dedicated [scalars page](scalars.md).
{style="note"}

## Arguments

Kotlin parameters are mapped to GraphQL arguments:

<table>
<tr><th>Kotlin</th><th>GraphQL</th></tr>
<tr>
<td>
<code-block lang="kotlin">
class Organisation {
    fun users(
        first: Int, 
        after: String
    ): UserConnection
}
</code-block>
</td>
<td>
<code-block lang="graphql">
type Organisation {
    users(
        first: Int!,
        after: String!
    ): UserConnection!
}
</code-block>
</td>
</tr>
</table>

Use `Optional<>` anf `@GraphQLDefault` to further control your input values. 

Note that because nullability vs optionality are so closely related in GraphQL, not all combination are allowed:

```
type Query { 
  // A non-null argument
  fun fieldA(arg: Int) = 0
  
  // Disallowed: argument type is nullable and doesn't have a default value: it must also be optional.
  //fun fieldB(arg: Int?) = 0 
  
  // An nullable argument, the client may omit it in which case the function body must handle `Absent`.
  fun fieldC(arg: Optional<Int?>) = 0
  
  // Disallowed: argument type is not nullable and cannot be optional
  //fun fieldD(arg: Optional<Int>) = 0
  
  // An non-null argument, if the client omits it, the default value will be used.
  fun fieldE(@GraphQLDefault("10") arg: Int) = 0

  // An nullable argument, if the client omits it, the default value will be used.
  fun fieldF(@GraphQLDefault("10") arg: Int?) = 0

  // Disallowed: there is a default value and the argument type cannot be optional
  //fun fieldG(@GraphQLDefault("10") arg: Optional<Int?>) = 0 

  // Disallowed: there is a default value and the argument type cannot be optional
  //fun fieldH(@GraphQLDefault("10") arg: Optional<Int>) = 0
}
```

Kotlin parameters may be of class type in which case the class is generated as a GraphQL input object:

<table>
<tr><th>Kotlin</th><th>GraphQL</th></tr>
<tr>
<td>
<code-block lang="kotlin">
class UserFilter(
    val role: Role?, 
    val organisationId: String?
)
class Query {
    fun users(
        where: UserFilter, 
    ): List&lt;User&gt;
}
</code-block>
</td>
<td>
<code-block lang="graphql">
input UserFilter {
    role: Role,
    organisationId: String
}
type Query {
    users(
        where: UserFilter!,
    ): [User!]
}
</code-block>
</td>
</tr>
</table>

## Names
The GraphQL name can be customized using `@GraphQLName`:

<table>
<tr><th>Kotlin</th><th>GraphQL</th></tr>
<tr>
<td>
<code-block lang="kotlin">
@GraphQLName("User")
class DomainUser {
    @GraphQLName("email")
    fun emailAddress(): String
}
</code-block>
</td>
<td>
<code-block lang="graphql">
type User {
    email: String
}
</code-block>
</td>
</tr>
</table>

## Descriptions
The GraphQL description is generated from the KDoc:

<table>
<tr><th>Kotlin</th><th>GraphQL</th></tr>
<tr>
<td>
<code-block lang="kotlin">
/**
 * A logged-in user of the service.
 */
class User {
    /**
     * The email address of the user.
     */
    fun email(): String
}
</code-block>
</td>
<td>
<code-block lang="graphql">
"""
A logged-in user of the service.
"""
type User {
    """
    The email address of the user.
    """
    email: String
}
</code-block>
</td>
</tr>
</table>

## Nullability

Nullable fields and input fields are also nullable in GraphQL:

<table>
<tr><th>Kotlin</th><th>GraphQL</th></tr>
<tr>
<td>
<code-block lang="kotlin">
class User {
    fun email(): String?
    fun id(): String
}
</code-block>
</td>
<td>
<code-block lang="graphql">
type User {
    email: String
    id: String!
}
</code-block>
</td>
</tr>
</table>

> Note how the logic is inverted. In GraphQL, fields are nullable by default.

## Directives

Define custom directives using `@GraphQLDirective`:

<table>
<tr><th>Kotlin</th><th>GraphQL</th></tr>
<tr>
<td>
<code-block lang="kotlin">
/**
 * A field requires a specific opt-in
 */
@GraphQLDirective
annotation class requiresOptIn(val feature: String)
</code-block>
</td>
<td>
<code-block lang="graphql">
"""
A field requires a specific opt-in
"""
directive @requiresOptIn(feature: String!) on FIELD_DEFINITION
</code-block>
</td>
</tr>
</table>

> The directive locations and repeatable values are inferred automatically from the usages.
{style="note"}

Use your directive by annotating your Kotlin code:

<table>
<tr><th>Kotlin</th><th>GraphQL</th></tr>
<tr>
<td>
<code-block lang="kotlin">
class Query {
    @requiresOptIn(feature = "experimental")
    fun experimentalField(): String { 
        TODO() 
    }
}
</code-block>
</td>
<td>
<code-block lang="graphql">
type Query {
    experimentalField: String! @requiresOptIn(feature: "experimental")
}
</code-block>
</td>
</tr> 
</table>

The order of the directives in the GraphQL schema is the same as the order of the annotations in the Kotlin code.

Kotlin annotation classes are mapped to input objects when used as parameter types:

<table>
<tr><th>Kotlin</th><th>GraphQL</th></tr>
<tr>
<td>
<code-block lang="kotlin">
enum class OptInLevel {
    Ignore,
    Warning,
    Error
}

annotation class OptInFeature(
    val name: String,
    val level: OptInLevel
)

@GraphQLDirective
annotation class requiresOptIn(val feature: OptInFeature)
</code-block>
</td>
<td>
<code-block lang="graphql">
enum OptInLevel {
    Ignore,
    Warning,
    Error
}
input OptInFeature {
    name: String!,
    level: OptInLevel!
}
directive @requiresOptIn(feature: OptInFeature!) on FIELD_DEFINITION
</code-block>
</td>
</tr>
</table>

Limitations:
1. It is impossible to reuse other input objects in directive arguments. The directive input objects can only be used in directives. This is because [Kotlin annotations do not support regular class paramaters, only annotation classes](https://kotlinlang.org/docs/annotations.html#constructors) unlike regular function parameters.
2. Directive arguments cannot have default values. This is because KSP does not support reading Kotlin default values and using `@GraphQLDefault` only without a default value would not compile.

## Deprecation

Kotlin symbols annotated as `@Deprecated` are marked deprecated in GraphQL when applicable:

<table>
<tr><th>Kotlin</th><th>GraphQL</th></tr>
<tr>
<td>
<code-block lang="kotlin">
class User {
    fun role(): Role
    @Deprecated("Check for `role == Admin` instead")
    fun isAdmin(): Boolean
}
</code-block>
</td>
<td>
<code-block lang="graphql">
type User {
    isAdmin: String @deprecated("Check for `role == Admin` instead")
}
</code-block>
</td>
</tr>
</table>

> As of 2024, field, input fields, arguments and enum values support deprecation in GraphQL. Other locations, and especially objects and input objects cannot be deprecated. See [graphql-spec#550](https://github.com/graphql/graphql-spec/issues/550) for more details.
{style="note"}

## Default values

[KSP cannot read default parameter values](https://github.com/google/ksp/issues/642).

In order to define a default value for your arguments, use `@GraphQLDefault` and pass the value [encoded as GraphQL](https://spec.graphql.org/draft/#Value):

<table>
<tr><th>Kotlin</th><th>GraphQL</th></tr>
<tr>
<td>
<code-block lang="kotlin">
class Organisation {
    fun users(
        @GraphQLDefault("100") first: Int, 
        @GraphQLDefault("null") after: String?
    ): UserConnection
}
</code-block>
</td>
<td>
<code-block lang="graphql">
type Organisation {
    users(
        first: Int! = 100,
        after: String = null
    ): UserConnection!
}
</code-block>
</td>
</tr>
</table>

> Default values are not supported in directives
{style=note}


## Built-in types

Kotlin built-in types map to their GraphQL equivalent:

| Kotlin  | GraphQL |
|---------|---------|
| Int     | Int     |
| String  | String  |
| Double  | Float   |
| Boolean | Boolean |
| List    | List    |