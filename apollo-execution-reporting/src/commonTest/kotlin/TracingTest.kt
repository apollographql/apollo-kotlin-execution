@file:OptIn(ExperimentalEncodingApi::class)

import com.apollographql.apollo.ast.toGQLDocument
import com.apollographql.execution.ExecutableSchema
import com.apollographql.execution.toGraphQLRequest
import com.apollographql.execution.reporting.ApolloReportsInstrumentation
import com.apollographql.execution.reporting.ApolloReportsOperationContext
import com.apollographql.execution.reporting.Trace
import kotlinx.coroutines.runBlocking
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TracingTest {
  private fun schema(): ExecutableSchema {
    // language=graphql
    val sdl = """
      type Query {
          widgets: [Widget!]
          listOfLists: [[Widget!]!]
          listOfScalars: [String!]!
      }

      type Widget {
          foo: String
          bar: String
      }
    """.trimIndent()

    return ExecutableSchema.Builder()
      .schema(sdl.toGQLDocument())
      .resolver {
        when (it.fieldName) {
          "widgets" -> listOf(0, 1)
          "listOfLists" -> listOf(0, 1).map { listOf(0, 1) }
          "listOfScalars" -> listOf("one", "two", "three")
          "foo" -> "hello world"
          "bar" -> throw Exception("woops")
          else -> Unit
        }
      }
      .addInstrumentation(ApolloReportsInstrumentation())
      .build()
  }

  @Test
  fun testTracing() {
    val context = ApolloReportsOperationContext()
    runBlocking {
      schema().execute("{ widgets { foo, baz: bar }, listOfLists { foo }, listOfScalars }".toGraphQLRequest(), context)
    }

    val trace = context.apolloOperationTracing.toProtoTrace()

    assertTrue(trace.start_time!!.getEpochSecond() > 0, "Start time has seconds")
    assertTrue(trace.start_time.getNano() > 0, "Start time has nanoseconds")
    assertTrue(trace.end_time!!.getEpochSecond() > 0, "End time has seconds")
    assertTrue(trace.end_time.getNano() > 0, "End time has nanoseconds")
    assertTrue(trace.duration_ns > 0, "DurationNs is greater than zero")
    assertEquals(3, trace.root!!.child.size)

    val widgets = trace.root.child.get(0)
    assertTrue(widgets.start_time > 0, "Field start time is greater than zero")
    assertTrue(widgets.end_time > 0, "Field end time is greater than zero")
    assertEquals("Query", widgets.parent_type)
    assertEquals("[Widget!]", widgets.type)
    assertEquals("widgets", widgets.response_name)
    assertEquals(2, widgets.child.size)
    
    val secondItem = widgets.child.get(1)
    assertEquals(1, secondItem.index)
    assertEquals(2, secondItem.child.count())

    val foo = secondItem.child.get(0)
    assertTrue(foo.start_time > 0, "Field start time is greater than zero")
    assertTrue(foo.end_time > 0, "Field end time is greater than zero")
    assertEquals("Widget", foo.parent_type)
    assertEquals("String", foo.type)
    assertEquals("foo", foo.response_name)
    assertEquals(0, foo.error.size)

    val bar = secondItem.child.get(1)
    assertTrue(bar.start_time > 0, "Field start time is greater than zero")
    assertTrue(bar.end_time > 0, "Field end time is greater than zero")
    assertEquals("Widget", bar.parent_type)
    assertEquals("String", bar.type)
    assertEquals("baz", bar.response_name)
    assertEquals("bar", bar.original_field_name)
    assertEquals(1, bar.error.size)

    val error = bar.error.get(0)
    assertEquals("Cannot resolve 'baz': woops", error.message)

    val listOfLists = trace.root.child.get(1)
    assertEquals(0, listOfLists.child.get(0).index)
    assertEquals(2, listOfLists.child.get(0).child.size)
    assertEquals(1, listOfLists.child.get(1).index)
    assertEquals(2, listOfLists.child.get(1).child.size)

    assertEquals(0, listOfLists.child.get(0).child.get(0).index)
    assertEquals(1, listOfLists.child.get(0).child.get(0).child.size)
    assertEquals(1, listOfLists.child.get(0).child.get(1).index)
    assertEquals(1, listOfLists.child.get(0).child.get(1).child.size)

    val deeplyNestedFoo = listOfLists.child.get(0).child.get(0).child.get(0)
    assertTrue(deeplyNestedFoo.start_time > 0, "Field start time is greater than zero")
    assertTrue(deeplyNestedFoo.end_time > 0, "Field end time is greater than zero")
    assertEquals("Widget", deeplyNestedFoo.parent_type)
    assertEquals("String", deeplyNestedFoo.type)
    assertEquals("foo", deeplyNestedFoo.response_name)
    assertEquals(0, deeplyNestedFoo.error.size)

    val listOfScalars = trace.root.child.get(2)
    assertTrue(listOfScalars.start_time > 0, "Field start time is greater than zero")
    assertTrue(listOfScalars.end_time > 0, "Field end time is greater than zero")
    assertEquals("Query", listOfScalars.parent_type)
    assertEquals("[String!]!", listOfScalars.type)
    assertEquals("listOfScalars", listOfScalars.response_name)
  }

  @Test
  fun decodeBase64() {
    val ftv1 = "IgsI1+L/uAYQ5+OsdBoLCNfi/7gGEInX23RYxecvcp8BYpwBcghwcm9kdWN0cxoLW1Byb2R1Y3QhXSFqBVF1ZXJ5QPihH0jfzStiImIecgJpZBoDSUQhagdQcm9kdWN0QO6xJkitjCcKAmlkEABiImIecgJpZBoDSUQhagdQcm9kdWN0QMDLKUjw4ykKAmlkEAFiImIecgJpZBoDSUQhagdQcm9kdWN0QPyJK0iKnisKAmlkEAIKCHByb2R1Y3Rz"
    val bytes = Base64.decode(ftv1)
    val trace = Trace.ADAPTER.decode(bytes)

    println(trace)
  }
}