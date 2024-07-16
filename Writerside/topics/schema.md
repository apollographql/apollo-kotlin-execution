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

> Non-root classes do not need to be annotated and are added to the GraphQL schema automatically.

### Objects and fields

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

### Unions and interfaces

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

### Enums

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

### Scalars

Classes annotated with `@GraphQLScalar` are mapped to GraphQL scalars:

<table>
<tr><th>Kotlin</th><th>GraphQL</th></tr>
<tr>
<td>
<code-block lang="kotlin">
@GraphQLScalar
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

@GraphQLScalar
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

### Arguments

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

### Names
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

### Descriptions
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

### Nullability

Nullable fields and input fields are also nullable in GraphQL:

<table>
<tr><th>Kotlin</th><th>GraphQL</th></tr>
<tr>
<td>
<code-block lang="kotlin">
class User {
    fun email(): String?
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

> Note how the logic is inverted. In GraphQL, fields are nullable by default.

### Deprecation

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

### Built-in types

Kotlin built-in types map to their GraphQL equivalent:

| Kotlin  | GraphQL |
|---------|---------|
| Int     | Int     |
| String  | String  |
| Double  | Float   |
| Boolean | Boolean |
| List    | List    |