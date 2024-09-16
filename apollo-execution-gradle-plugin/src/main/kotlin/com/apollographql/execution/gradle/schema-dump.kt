package com.apollographql.execution.gradle

import com.apollographql.execution.gradle.internal.ApolloCheckSchema
import com.apollographql.execution.gradle.internal.ApolloDumpSchema
import org.gradle.api.Project
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty

private fun Project.defaultSchemaDump(): RegularFile {
  return layout.projectDirectory.file("graphql/schema.graphqls")
}

fun Project.enableSchemaDump(kspSchemaPath: String, kspTaskName: String, schemaDump: RegularFileProperty) {
  val dumpSchema = tasks.register(apolloDumpSchema, ApolloDumpSchema::class.java) {
    it.dependsOn(kspTaskName)
    it.from.set(file(kspSchemaPath))
    it.to.set(schemaDump.orElse(defaultSchemaDump()))
  }
  val checkSchema = tasks.register(apolloCheckSchema, ApolloCheckSchema::class.java) {
    it.dependsOn(kspTaskName)
    it.new.set(file(kspSchemaPath))
    it.existing.set(schemaDump.orElse(defaultSchemaDump()))
  }
  tasks.named("check") {
    it.dependsOn(apolloCheckSchema)
  }

  tasks.configureEach { maybeKsp ->
    if (maybeKsp.name == kspTaskName) {
      /**
       * XXX: eager configuration on dumpSchema and checkSchema but
       * without this, configureEach above doesn't work 🤷
       */
      dumpSchema.get().dependsOn(maybeKsp)
      checkSchema.get().dependsOn(maybeKsp)
    }
  }
}