package com.apollographql.apollo3.execution

import com.apollographql.apollo3.api.ExecutionContext
import com.apollographql.apollo3.api.Optional
import com.apollographql.apollo3.ast.GQLBooleanValue
import com.apollographql.apollo3.ast.GQLEnumValue
import com.apollographql.apollo3.ast.GQLField
import com.apollographql.apollo3.ast.GQLFieldDefinition
import com.apollographql.apollo3.ast.GQLFloatValue
import com.apollographql.apollo3.ast.GQLIntValue
import com.apollographql.apollo3.ast.GQLListValue
import com.apollographql.apollo3.ast.GQLNullValue
import com.apollographql.apollo3.ast.GQLObjectValue
import com.apollographql.apollo3.ast.GQLStringValue
import com.apollographql.apollo3.ast.GQLType
import com.apollographql.apollo3.ast.GQLValue
import com.apollographql.apollo3.ast.GQLVariableValue
import com.apollographql.apollo3.ast.Schema
import com.apollographql.apollo3.ast.definitionFromScope
import com.apollographql.apollo3.execution.internal.DefaultMutationRoot
import com.apollographql.apollo3.execution.internal.DefaultQueryRoot
import com.apollographql.apollo3.execution.internal.DefaultSubscriptionRoot
import com.apollographql.apollo3.execution.internal.InternalValue

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
   * @return the resolved result:
   * - If the field type is a non-nullable type and [resolve] returns null, a field error is raised.
   * - For leaf types (scalars and enums), the resolved result must be coercible according to the type of the field.
   * - For composite types, the resolved result is an opaque type that is passed down to child resolvers.
   * - For list types, the resolved result must be a kotlin List.
   */
  fun resolve(resolveInfo: ResolveInfo): Any?
}

internal interface Roots {
  fun query(): Any
  fun mutation(): Any
  fun subscription(): Any

  companion object {
    fun create(queryRoot: (() -> Any)?, mutationRoot: (() -> Any)?, subscriptionRoot: (() -> Any)?): Roots {
      return object : Roots {
        override fun query(): Any {
          return queryRoot?.invoke() ?: DefaultQueryRoot
        }

        override fun mutation(): Any {
          return mutationRoot?.invoke() ?: DefaultMutationRoot
        }

        override fun subscription(): Any {
          return subscriptionRoot?.invoke() ?: DefaultSubscriptionRoot
        }
      }
    }
  }
}

/**
 * A resolver that always throws
 */
internal object ThrowingResolver : Resolver {
  override fun resolve(resolveInfo: ResolveInfo): Any? {
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
     * Coerced variables
     */
    val variables: Map<String, Any?>,
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
   * Returns the argument for field [name]
   *
   * The argument is coerced according to the configured [Coercing] so it is safe to force cast the result to the matching type.
   *
   */
  fun getArgument(
      name: String,
  ): Optional<InternalValue> {
    return if(!arguments.containsKey(name)) {
      Optional.absent()
    } else {
      Optional.present(arguments.get(name))
    }
  }

  fun getRequiredArgument(name: String): InternalValue {
    return getArgument(name).getOrThrow()
  }

  fun coordinates(): String {
    return "$parentType.$fieldName"
  }
}
