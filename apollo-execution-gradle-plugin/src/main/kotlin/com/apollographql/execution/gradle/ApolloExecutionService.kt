package com.apollographql.execution.gradle

import org.gradle.api.file.RegularFileProperty

abstract class ApolloExecutionService {
  abstract var packageName: String
  abstract val schemaFile: RegularFileProperty
}
