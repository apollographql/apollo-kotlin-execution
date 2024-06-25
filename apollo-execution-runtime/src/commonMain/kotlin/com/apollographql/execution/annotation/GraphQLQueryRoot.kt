package com.apollographql.execution.annotation

/**
 * Marks the target class as a GraphQL query root.
 *
 * There can be only one [GraphQLQueryRoot] class in a given compilation.
 */
@Target(AnnotationTarget.CLASS)
annotation class GraphQLQueryRoot
