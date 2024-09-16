package com.apollographql.execution.gradle

import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Action
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import javax.inject.Inject

abstract class ApolloExecutionExtension @Inject constructor(val project: Project) {
  var hasService = false

  fun service(serviceName: String, action: Action<ApolloExecutionService>) {
    if (hasService) {
      error("Apollo: there can be only one execution service. Use different Gradle module for different services.")
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
       * KMP support isn't great, so we wire most of the things manually
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

internal const val apolloDumpSchema = "apolloDumpSchema"
internal const val apolloCheckSchema = "apolloCheckSchema"