package com.apollographql.execution.annotation

/**
 * Marks a given class as a GraphQL scalar coercing.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPEALIAS)
annotation class GraphQLCoercing
