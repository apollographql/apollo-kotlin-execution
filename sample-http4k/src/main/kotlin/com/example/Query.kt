package com.example

import com.apollographql.execution.annotation.GraphQLQuery

// Define your root query class
@GraphQLQuery
class Query {
    // Public functions become GraphQL fields
    // Kdoc comments become GraphQL descriptions
    // Kotlin parameters become GraphQL arguments
    /**
     * Greeting for name
     */
    fun hello(name: String): String {
        return "Hello $name"
    }

    fun bar()  = "bar"
}