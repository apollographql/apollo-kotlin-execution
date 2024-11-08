import com.apollographql.apollo.api.Optional
import com.apollographql.execution.annotation.GraphQLDefault
import com.apollographql.execution.annotation.GraphQLQuery

@GraphQLQuery
class Query {
  fun field(): String = "hello"
  val animal = Cat("meeeooooooowwwww")
  fun a(arg: Int) = 0
  //fun b(arg: Int?) = 0 // Input value is nullable and doesn't have a default value: it must also be optional.
  fun c(arg: Optional<Int?>) = 0
  //fun d(arg: Optional<Int>) = 0 // Input value is not nullable and cannot be optional
  fun e(@GraphQLDefault("10") arg: Int) = 0
  fun f(@GraphQLDefault("10") arg: Int?) = 0
  //fun g(@GraphQLDefault("10") arg: Optional<Int?>) = 0 // Input value has a default value and cannot be optional
  //fun h(@GraphQLDefault("10") arg: Optional<Int>) = 0 // Input value has a default value and cannot be optional
}

sealed interface Animal

class Cat(val meow:String): Animal
class Dog(val barf:String): Animal