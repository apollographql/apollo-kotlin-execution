package com.apollographql.execution.annotation

/**
 * Marks the target annotation class as a GraphQL directive.
 *
 * The directive locations are inferred from the annotation usages.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
annotation class GraphQLDirective
