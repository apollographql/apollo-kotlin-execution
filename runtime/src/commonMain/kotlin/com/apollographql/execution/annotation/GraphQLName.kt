package com.apollographql.execution.annotation

/**
 * Changes the GraphQL name of a Kotlin symbol.
 * By default:
 * - Kotlin classes generate GraphQL object/input object/scalar of the same name
 * - Kotlin interfaces generate GraphQL interfaces of the same name (or unions if the interface doesn't have any field)
 * - Kotlin properties generate GraphQL fields/input fields of the same name
 * - Kotlin functions generate GraphQL fields/input fields of the same name
 * - Kotlin parameters generate GraphQL arguments of the same name
 * - Kotlin enums generate GraphQL enums of the same name
 * - Kotlin enum values generate GraphQL enum values of the same name
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY, AnnotationTarget.FUNCTION, AnnotationTarget.TYPEALIAS)
annotation class GraphQLName(val name: String)
