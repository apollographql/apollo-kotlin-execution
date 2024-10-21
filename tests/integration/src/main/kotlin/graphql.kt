import com.apollographql.execution.annotation.GraphQLQuery

@GraphQLQuery
class Query {
  fun field(): String = "hello"
}