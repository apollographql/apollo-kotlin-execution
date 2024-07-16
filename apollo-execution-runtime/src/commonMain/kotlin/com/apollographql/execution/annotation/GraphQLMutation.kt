package com.apollographql.execution.annotation

/**
 * Marks the target class as a GraphQL mutation root.
 *
 * There can be only one [GraphQLMutation] class in a given compilation.
 */
@Target(AnnotationTarget.CLASS)
annotation class GraphQLMutation
