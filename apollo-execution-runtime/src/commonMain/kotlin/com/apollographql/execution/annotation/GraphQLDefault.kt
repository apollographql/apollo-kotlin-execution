package com.apollographql.execution.annotation

/**
 * KSP cannot access the default values of functions. Use [com.apollographql.execution.annotation.GraphQLDefault] to provide them.
 *
 * @param value the GraphQL default value as a [GraphQL value literal](https://spec.graphql.org/draft/#Value).
 */
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class GraphQLDefault(val value: String)
