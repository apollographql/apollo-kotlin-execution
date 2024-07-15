import com.apollographql.apollo.api.ExecutionContext
import com.apollographql.execution.GraphQLRequest
import com.example.ServiceExecutableSchemaBuilder
import kotlin.test.Test

class SampleTest {
  @Test
  fun test() {
    val executableSchema = ServiceExecutableSchemaBuilder()
      .build()

    val response = executableSchema.execute(
      GraphQLRequest.Builder()
        .document("{ hello(name: \"sample\") }")
        .build(),
      ExecutionContext.Empty
    )

    println(response.data)
    // {hello=Hello sample}
  }
}