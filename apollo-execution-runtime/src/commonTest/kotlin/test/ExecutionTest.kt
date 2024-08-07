package test

import com.apollographql.apollo.api.ExecutionContext
import com.apollographql.apollo.ast.GQLListType
import com.apollographql.apollo.ast.GQLNamedType
import com.apollographql.apollo.ast.GQLNonNullType
import com.apollographql.apollo.ast.GQLObjectTypeDefinition
import com.apollographql.apollo.ast.GQLScalarTypeDefinition
import com.apollographql.apollo.ast.GQLType
import com.apollographql.apollo.ast.Schema
import com.apollographql.execution.ExecutableSchema
import com.apollographql.execution.GraphQLRequest
import com.apollographql.execution.ResolveInfo
import com.apollographql.execution.Resolver
import kotlin.test.Test

private val randomResolver = object : Resolver {
    fun GQLType.randomValue(schema: Schema): Any? {
        return when (this) {
            is GQLNonNullType -> type.randomValue(schema)
            is GQLListType -> listOf(type.randomValue(schema))
            is GQLNamedType -> {
                when (schema.typeDefinition(name)) {
                    is GQLObjectTypeDefinition -> mapOf("__typename" to name)
                    is GQLScalarTypeDefinition -> when (name) {
                        "String" -> "Hello"
                        "Int" -> 42
                        "Float" -> 3.0
                        "Boolean" -> true
                        else -> error("No scalar for type '$name'")
                    }
                    else -> error("No random value for type '$name'")
                }
            }
        }
    }

    override fun resolve(resolveInfo: ResolveInfo): Any? {
        val type = resolveInfo.fieldDefinition().type

        return type.randomValue(resolveInfo.schema)
    }
}

internal fun String.toGraphQLRequest(): GraphQLRequest = GraphQLRequest.Builder()
    .document(this)
    .build()

class ExecutionTest {

    @Test
    fun simple() {
        // language=graphql
        val schema = """
            type Query {
                foo: String!
            }
        """.trimIndent()

        // language=graphql
        val document = """
            {
                foo
            }
        """.trimIndent()

        val simpleMainResolver = object : Resolver {
            override fun resolve(resolveInfo: ResolveInfo): Any? {
                if (resolveInfo.parentType != "Query" || resolveInfo.fieldName != "foo") return null
                return "42"
            }
        }

        val response = ExecutableSchema.Builder()
            .schema(schema)
            .defaultResolver(simpleMainResolver)
            .build()
            .execute(document.toGraphQLRequest(), ExecutionContext.Empty)
        println(response.data)
        println(response.errors)
    }

    @Test
    fun argument() {
        val schema = """
            type Query {
                foo(first: Int): String!
            }
        """.trimIndent()

        val document = """
            {
                foo(first = ${'$'}first)
            }
        """.trimIndent()


        val response = ExecutableSchema.Builder()
            .schema(schema)
            .defaultResolver(randomResolver)
            .resolveType { obj, _ ->
                @Suppress("UNCHECKED_CAST")
                (obj as Map<String, String?>).get("__typename")
            }
            .build()
            .execute(document.toGraphQLRequest(), ExecutionContext.Empty)
        println(response.data)
        println(response.errors)
    }
}