import com.apollographql.apollo.execution.GraphQLRequest
import com.apollographql.apollo.execution.parseAsGraphQLRequest
import com.example.ServiceExecutableSchemaBuilder
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private fun String.toGraphQLRequest(): GraphQLRequest{
  return mapOf("query" to this).parseAsGraphQLRequest().getOrThrow()
}

class MainTest {
  @Test
  fun test() {
    ServiceExecutableSchemaBuilder()
      .build()
      .let {
        runBlocking {
          it.execute(
            """
            {
              field
              animal {
                ... on Cat { meow }
              }
            }
            """.trimIndent().toGraphQLRequest())
        }
      }.data
      .apply {
        assertIs<Map<*, *>>(this)
        assertEquals(get("field"), "hello")
        get("animal").apply {
          assertIs<Map<*, *>>(this)
          assertEquals(get("meow"), "meeeooooooowwwww")
        }
      }
  }
}
