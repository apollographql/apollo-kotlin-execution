@file:OptIn(ApolloExperimental::class)

import com.apollographql.apollo.annotations.ApolloExperimental
import com.apollographql.apollo.ast.GQLDirectiveDefinition
import com.apollographql.apollo.ast.GQLObjectTypeDefinition
import com.apollographql.apollo.ast.parseAsGQLDocument
import com.apollographql.apollo.ast.validateAsSchema
import com.apollographql.execution.toGraphQLRequest
import com.example.ServiceExecutableSchemaBuilder
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MainTest {
  @Test
  fun entityIsResolved(): Unit = runBlocking {
    ServiceExecutableSchemaBuilder()
      .build()
      .execute(
        """
          { 
            _entities(representations: [{__typename: "Product", id: "2" }]) {
              __typename
              ... on Product {
                name
              }
            }
          }
        """
          .trimIndent()
          .toGraphQLRequest()
      ).apply {
        val data = data
        assertIs<Map<*, *>>(data)
        val entities = data.get("_entities")
        assertIs<List<*>>(entities)
        assertEquals(1, entities.size)
        val entity = entities.single()
        assertIs<Map<*, *>>(entity)
        assertEquals("bar", entity.get("name"))
      }
  }

  @Test
  fun sdlReturnsSchema(): Unit = runBlocking {
    ServiceExecutableSchemaBuilder()
      .build()
      .execute(
        """
          { 
            _service { sdl }
          }
        """
          .trimIndent()
          .toGraphQLRequest()
      ).apply {
        val data = data
        assertIs<Map<*, *>>(data)
        val service = data.get("_service")
        assertIs<Map<*, *>>(service)
        val schema = (service.get("sdl") as String).parseAsGQLDocument().getOrThrow().validateAsSchema().getOrThrow()

        assertTrue(schema.directiveDefinitions.get("key") is GQLDirectiveDefinition)
        assertTrue(schema.typeDefinitions.get("_Service") is GQLObjectTypeDefinition)
      }
  }
}