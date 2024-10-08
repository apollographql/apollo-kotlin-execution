package com.apollographql.execution

import com.apollographql.apollo.api.ExecutionContext
import com.apollographql.apollo.api.Optional
import com.apollographql.apollo.ast.GQLField
import com.apollographql.apollo.ast.GQLFieldDefinition
import com.apollographql.apollo.ast.Schema
import com.apollographql.apollo.ast.definitionFromScope
import com.apollographql.execution.internal.InternalValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.coroutineContext

fun interface Resolver {
  /**
   * Resolves a field. A typical implementation is to use [ResolveInfo.parentObject]:
   *
   * ```kotlin
   * fun resolve(resolveInfo: ResolveInfo): Any? {
   *   val parent = resolveInfo.parentObject as Map<String, Any?>
   *   return parent[resolveInfo.fieldName]
   * }
   * ```
   *
   * @param resolveInfo information about the field being resolved
   * @return the resolved result:
   * - If the field type is a non-nullable type and [resolve] returns null, a field error is raised.
   * - For leaf types (scalars and enums), the resolved result must be coercible according to the type of the field.
   * - For composite types, the resolved result is an opaque type that is passed down to child resolvers.
   * - For list types, the resolved result must be a kotlin List.
   */
  suspend fun resolve(resolveInfo: ResolveInfo): Any?
}


internal class Roots(
  val query: (() -> Any?)?,
  val mutation: (() -> Any?)?,
  val subscription: (() -> Any?)?
)

/**
 * A resolver that always throws
 */
internal object ThrowingResolver : Resolver {
  override suspend fun resolve(resolveInfo: ResolveInfo): Any? {
    error("No resolver found for '${resolveInfo.coordinates()}' and no defaultResolver set.")
  }
}

interface Instrumentation {
  /**
   * For subscriptions, this is called only once on the root field and then for every data in the nested fields
   */
  fun beforeResolve(resolveInfo: ResolveInfo)
}

class ResolveTypeInfo(
  val type: String,
  val schema: Schema
)

class ResolveInfo internal constructor(
  /**
   * The parent object
   *
   * @see [ExecutableSchema.Builder.queryRoot]
   * @see [ExecutableSchema.Builder.mutationRoot]
   * @see [ExecutableSchema.Builder.subscriptionRoot]
   */
  val parentObject: Any?,
  val executionContext: ExecutionContext,
  val fields: List<GQLField>,
  val schema: Schema,
  /**
   * Coerced arguments
   */
  private val arguments: Map<String, Any?>,
  val parentType: String,
) {
  val field: GQLField
    get() = fields.first()

  val fieldName: String
    get() = fields.first().name

  fun fieldDefinition(): GQLFieldDefinition {
    return field.definitionFromScope(schema, parentType)
      ?: error("Cannot find fieldDefinition $parentType.${field.name}")
  }

  /**
   * Returns the argument for [name]. It is the caller responsibility to use a type parameter [T] matching
   * the expected argument type. If not, [getArgument] may succeed but subsequent calls may fail with [ClassCastException].
   *
   * @param T the type of the expected [InternalValue]. The caller must have knowledge of what Kotlin type
   * to expect for this argument. T
   *
   * @return the argument for [name] or [Optional.Absent] if that argument is not present. The return
   * value is automatically cast to [T].
   */
  fun <T> getArgument(
    name: String,
  ): Optional<T> {
    return if (!arguments.containsKey(name)) {
      Optional.absent()
    } else {
      @Suppress("UNCHECKED_CAST")
      Optional.present(arguments.get(name)) as Optional<T>
    }
  }

  fun <T> getRequiredArgument(name: String): T {
    return getArgument<T>(name).getOrThrow()
  }

  fun coordinates(): String {
    return "$parentType.$fieldName"
  }
}
