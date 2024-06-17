package com.apollographql.execution.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

abstract class ApolloExecutionService {
  abstract var packageName: String
  abstract val schemaPath: Property<String>

  init {
    schemaPath.set("graphql/schema.graphqls")
  }
}
