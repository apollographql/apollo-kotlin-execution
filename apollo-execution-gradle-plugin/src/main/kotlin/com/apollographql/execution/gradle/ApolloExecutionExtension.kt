package com.apollographql.execution.gradle

import com.google.devtools.ksp.gradle.KspExtension
import gratatouille.wiring.GExtension
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalDependency
import org.jetbrains.kotlin.gradle.dsl.KotlinCompile
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import javax.inject.Inject

@GExtension(pluginId = "com.apollographql.execution")
abstract class ApolloExecutionExtension @Inject constructor(private val project: Project) {
  var hasService = false

  init {
    project.configurations.configureEach { configuration ->
      configuration.withDependencies { dependencySet ->
        val pluginVersion = VERSION
        dependencySet.filterIsInstance<ExternalDependency>()
          .filter { it.group == "com.apollographql.execution" && it.version.isNullOrEmpty() }
          .forEach { it.version { constraint -> constraint.require(pluginVersion) } }
      }
    }
  }

  fun service(serviceName: String, action: Action<ApolloExecutionService>) {
    if (hasService) {
      error("Apollo: there can be only one execution service. Use different Gradle module for different services.")
    }
    if (!project.pluginManager.hasPlugin("com.google.devtools.ksp")) {
      error("Apollo: the 'com.apollographql.execution' plugin requires the 'com.google.devtools.ksp' plugin.")
    }
    val service = project.objects.newInstance(ApolloExecutionService::class.java)

    action.execute(service)

    val ksp = project.extensions.findByType(KspExtension::class.java)
    if (ksp == null) {
      error("The 'com.apollographql.execution' plugin requires the 'com.google.devtools.ksp' plugin.")
    }
    ksp.arg("apolloService", serviceName)
    ksp.arg("apolloPackageName", service.packageName.getOrElse("com.example"))

    val kotlin = project.extensions.findByName("kotlin")
    if (kotlin == null) {
      error("The 'com.apollographql.execution' plugin requires the Kotlin plugin.")
    }

    val configurationName: String
    val kspSchemaPath: String
    val kspTaskName: String

    if (kotlin is KotlinMultiplatformExtension) {
      /**
       * KSP configuration
       * To avoid calling the processor multiple times, we wire things manually
       * See https://github.com/google/ksp/pull/1021
       */
      kotlin.sourceSets.getByName("commonMain").kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
      project.tasks.withType(KotlinCompile::class.java).configureEach {
        it.dependsOn("kspCommonMainKotlinMetadata")
      }
      kspSchemaPath = "build/generated/ksp/metadata/commonMain/resources/${serviceName}Schema.graphqls"
      kspTaskName = "kspCommonMainKotlinMetadata"
      configurationName = "kspCommonMainMetadata"
    } else {
      kspSchemaPath = "build/generated/ksp/main/resources/${serviceName}Schema.graphqls"
      kspTaskName = "kspKotlin"
      configurationName = "ksp"
    }

    project.dependencies.add(
      configurationName,
      "com.apollographql.execution:apollo-execution-processor"
    )

    project.enableSchemaDump(
      kspSchemaPath = kspSchemaPath,
      kspTaskName = kspTaskName,
      schemaDump = service.schemaFile
    )
  }
}

