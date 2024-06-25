package com.apollographql.execution.annotation

import kotlin.reflect.KClass

/**
 * Marks a given class as a GraphQL scalar coercing.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPEALIAS)
annotation class GraphQLCoercing
