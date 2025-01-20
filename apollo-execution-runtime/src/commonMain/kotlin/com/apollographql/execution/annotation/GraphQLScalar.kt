package com.apollographql.execution.annotation

import kotlin.reflect.KClass

/**
 * Marks a given class or typealias as a custom GraphQL scalar.
 *
 * ```kotlin
 * @GraphQLScalar(GeoPointCoercing::class)
 * class GeoPoint(val x: Double, val y: Double)
 * ```
 *
 * If you do not control the type, you can use a type alias:
 *
 * ```kotlin
 * @GraphQLScalar(DateCoercing::class)
 * @GraphQLName("Date")
 * typealias GraphQLDate = java.util.Date
 * ```
 *
 * When using type aliases, you may use either the alias or the original type.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPEALIAS)
annotation class GraphQLScalar(val coercing: KClass<*>)
