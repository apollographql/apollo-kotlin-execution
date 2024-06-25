package com.apollographql.execution.annotation

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class GraphQLDefault(val value: String)
