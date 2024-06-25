package com.apollographql.execution.annotation

import com.apollographql.apollo3.annotations.ApolloExperimental

/**
 * Marks the target class as a GraphQL mutation root.
 *
 * There can be only one [GraphQLMutationRoot] class in a given compilation.
 */
@Target(AnnotationTarget.CLASS)
annotation class GraphQLMutationRoot
