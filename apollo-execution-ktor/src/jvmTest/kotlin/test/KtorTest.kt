package test

import com.apollographql.apollo3.execution.internal.DefaultQueryRoot
import com.apollographql.apollo3.execution.ExecutableSchema
import com.apollographql.apollo3.execution.ResolveInfo
import com.apollographql.apollo3.execution.Resolver
import com.apollographql.apollo3.execution.ktor.apolloModule
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.cors.routing.CORS
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
  @Ignore
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

    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
      install(CORS) {
        anyHost()
        allowHeaders {
          true
        }
        allowNonSimpleContentTypes = true
      }
      apolloModule(executableSchema)
    }.start(wait = true)
  }
}
