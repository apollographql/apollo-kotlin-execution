package com.apollographql.execution.gradle

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

abstract class ApolloExecutionService {
  abstract val packageName: Property<String>
  abstract val schemaFile: RegularFileProperty
}
