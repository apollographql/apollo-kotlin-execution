package com.apollographql.execution.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

abstract class ApolloExecutionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.extensions.create("apolloExecution", ApolloExecutionExtension::class.java, target)
  }
}