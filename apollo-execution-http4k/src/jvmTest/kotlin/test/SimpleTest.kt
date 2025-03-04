package test

import com.apollographql.apollo.execution.ExecutableSchema
import com.apollographql.execution.http4k.apolloHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Uri
import org.http4k.core.query
import kotlin.test.Test
import kotlin.test.assertEquals

class SimpleTest {
  @Test
  fun simpleTest() {

    val schema = """
            type Query {
                foo: String!
            }
        """.trimIndent()
    val executableSchema = ExecutableSchema.Builder()
        .schema(schema)
        .resolver {
          "bar"
        }
        .build()
    val uri = Uri.of("https://example.com/graphql").query("query", "{foo}")
    val response = apolloHandler(executableSchema)(Request(Method.GET, uri))

    val responseText = response.body.stream.reader().readText()
    assertEquals("{\"data\":{\"foo\":\"bar\"}}", responseText)
  }
}
