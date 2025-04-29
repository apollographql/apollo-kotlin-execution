package test

import com.apollographql.apollo.execution.ExecutableSchema
import com.apollographql.apollo.execution.ResolveInfo
import com.apollographql.apollo.execution.Resolver
import com.apollographql.execution.ktor.apolloModule
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.cors.routing.CORS
import kotlin.test.Ignore
import kotlin.test.Test

class MyDefaultResolver: Resolver {
  override suspend fun resolve(resolveInfo: ResolveInfo): Any? {
    @Suppress("UNCHECKED_CAST")
    return when (val parent = resolveInfo.parentObject) {
      null -> mapOf("foo" to "bar")
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
        .resolver(MyDefaultResolver())
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
