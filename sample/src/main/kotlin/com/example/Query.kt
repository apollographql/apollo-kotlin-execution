package com.example

import com.apollographql.execution.annotation.GraphQLQueryRoot

// Define your root query class
@GraphQLQueryRoot
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
}