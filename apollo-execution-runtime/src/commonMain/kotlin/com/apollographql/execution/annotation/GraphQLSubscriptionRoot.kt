package com.apollographql.execution.annotation


/**
 * Marks the target class as a GraphQL subscription root.
 *
 * There can be only one [GraphQLSubscriptionRoot] class in a given compilation.
 */
@Target(AnnotationTarget.CLASS)
annotation class GraphQLSubscriptionRoot
