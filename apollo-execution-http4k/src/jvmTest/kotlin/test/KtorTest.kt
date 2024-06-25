package test

import com.apollographql.execution.DefaultQueryRoot
import com.apollographql.execution.ExecutableSchema
import com.apollographql.execution.ResolveInfo
import com.apollographql.execution.Resolver
import com.apollographql.execution.http4k.*
import org.http4k.core.Method
import org.http4k.core.Request
import kotlin.test.Ignore
import kotlin.test.Test

class MyDefaultResolver: Resolver {
  override fun resolve(resolveInfo: ResolveInfo): Any? {
    return when (val parent = resolveInfo.parentObject) {
      is DefaultQueryRoot -> mapOf("foo" to "bar")
      else -> (parent as Map<String, *>).get(resolveInfo.fieldName)
    }
  }
}

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
        .defaultResolver(MyDefaultResolver())
        .build()
    val response = apolloHandler(executableSchema)(Request(Method.POST, ""))
  }
}
