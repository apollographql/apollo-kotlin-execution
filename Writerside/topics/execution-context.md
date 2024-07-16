# Execution context

Kotlin functions may define an additional `ExecutionContext` parameter. This parameter is special and never exposed in GraphQL.

<table>
<tr><th>Kotlin</th><th>GraphQL</th></tr>
<tr>
<td>
<code-block lang="kotlin">
@GraphQLQuery
class Query {
  fun viewer(context: ExecutionContext): User {
    // Do something with context
    return user
  }
}
</code-block>
</td>
<td>
<code-block lang="graphql">
type Query {
    # this field has no arguments
    viewer: user
}
</code-block>
</td>
</tr>
</table>

`ExecutionContext` is a typesafe map inspired by `CoroutinesContext`. Define your own context by extending `ExecutionContext.Element`:

```kotlin
class CurrentUser(val id: String) : ExecutionContext.Element {
  override val key: ExecutionContext.Key<*>
    get() = Key

  companion object Key : ExecutionContext.Key<CurrentUser>
}
```

And pass it to `ExecutableSchema.execute()`:

```kotlin
// Get the current logged-in user
val context = CurrentUser(getUserIdFromHttpHeaders())

val response = executableSchema.execute(
  GraphQLRequest.Builder()
    .document("{ viewer { name } }")
    .build(),
  context
)
```

`CurrentUser` is now available in `Query.viewer`:

```kotlin
class Query {
  fun viewer(context: ExecutionContext): User {
    val id = context[CurrentUser]!!.id
    return userById(id)
  }
}

```





