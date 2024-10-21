@file:Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")

package test

import com.apollographql.apollo.api.ExecutionContext
import com.apollographql.execution.ExecutableSchema
import com.apollographql.execution.toGraphQLRequest
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import kotlin.test.Test
import kotlin.test.assertNull

class IntrospectionTest {
  @Test
  fun introspection() = runBlocking {
    val schema = """
            type Query {
                foo: String!
            }
        """.trimIndent()


    val document = javaClass.classLoader.getResourceAsStream("introspection.graphql").reader().readText()

    val response = ExecutableSchema.Builder()
        .schema(schema)
        .build()
        .execute(
            document.toGraphQLRequest(),
            ExecutionContext.Empty
        )
    assertNull(response.errors)
  }
}
