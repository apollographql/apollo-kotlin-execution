package com.apollographql.execution.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalDependency

abstract class ApolloExecutionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.extensions.create("apolloExecution", ApolloExecutionExtension::class.java, target)
    target.configureDefaultVersionsResolutionStrategy()
  }

  private fun Project.configureDefaultVersionsResolutionStrategy() {
    configurations.configureEach { configuration ->
      configuration.withDependencies { dependencySet ->
        val pluginVersion = VERSION
        dependencySet.filterIsInstance<ExternalDependency>()
          .filter { it.group == "com.apollographql.execution" && it.version.isNullOrEmpty() }
          .forEach { it.version { constraint -> constraint.require(pluginVersion) } }
      }
    }
  }
}