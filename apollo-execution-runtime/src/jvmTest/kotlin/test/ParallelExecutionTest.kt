@file:OptIn(ExperimentalCoroutinesApi::class)

package test

import com.apollographql.apollo.ast.toGQLDocument
import com.apollographql.execution.ExecutableSchema
import com.apollographql.execution.toGraphQLRequest
import kotlinx.coroutines.*
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.times

class ParallelExecutionTest {
  @Test
  fun fieldsAreExecutedConcurrently() {
    // language=graphql
    val schema = """
      type Query {
        field1: Int
        field2: Int
        field3: Int
      }
    """.trimIndent()

    val items = mutableListOf<Int>()
    val executableSchema = ExecutableSchema.Builder()
      .schema(schema.toGQLDocument())
      .defaultResolver {
        val item = when (it.field.name) {
          "field1" -> 1
          "field2" -> 2
          "field3" -> 3
          else -> error("Unknown field '${it.field.name}'")
        }

        // Return the items in opposite order
        delay((3 - item) * 200.milliseconds)
        items.add(item)
        item
      }
      .build()

    val response = runBlocking {
      executableSchema.execute(
        """
        {field1 field2 field3}
      """.trimIndent().toGraphQLRequest()
      )
    }

    assertEquals(mapOf("field1" to 1, "field2" to 2, "field3" to 3), response.data)
    assertEquals(listOf(3, 2, 1), items)
  }

  class Batch {
    val deferred = mutableMapOf<String, CompletableDeferred<String>>()
  }

  class Loader(private val dispatcher: CoroutineDispatcher) {
    private var batch: Batch? = null

    suspend fun load(id: String): String {
      if (batch == null) {
        batch = Batch()
        dispatcher.dispatch(EmptyCoroutineContext) {
          assertEquals(4, batch!!.deferred.size)
          batch!!.deferred.forEach {
            it.value.complete("item-${it.key}")
          }
          batch = null
        }
      }

      val deferred = CompletableDeferred<String>()
      batch!!.deferred.put(id, deferred)

      return deferred.await()
    }
  }

  @Test
  fun subFieldAreStartedFromTheSameStackFrame() {
    val schema = """
      type Query {
        field1: Field
        field2: Field
      }
      type Field {
        subField1: String
        subField2: String
      }
    """.trimIndent()

    var inFirstFrame = true
    var runAfterFirstFrame: Runnable? = null
    val dispatcher = Dispatchers.Default.limitedParallelism(1)
    val loader = Loader(dispatcher)

    val executableSchema = ExecutableSchema.Builder()
      .schema(schema.toGQLDocument())
      .defaultResolver {
        if (inFirstFrame) {
          if (runAfterFirstFrame == null) {
            runAfterFirstFrame = Runnable {
              inFirstFrame = false
            }
          }
        }

        check(inFirstFrame)

        if (it.field.name.startsWith("field")) {
          return@defaultResolver it.field.name.substring(5).toInt()
        }

        check(it.field.name.startsWith("subField"))

        loader.load("${it.parentObject}-${it.field.name.substring(8)}")
      }
      .build()

    runBlocking(dispatcher) {
      val response = executableSchema.execute(
        """
        {
            field1 {
                subField1
                subField2            
            }
            field2 {
                subField1
                subField2            
            }
        }
      """.trimIndent().toGraphQLRequest()
      )

      assertEquals(
        mapOf(
          "field1" to mapOf(
            "subField1" to "item-1-1",
            "subField2" to "item-1-2",
          ),
          "field2" to mapOf(
            "subField1" to "item-2-1",
            "subField2" to "item-2-2",
          ),
        ), response.data
      )
    }
  }
}