import com.gradleup.librarian.gradle.Librarian

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("java-gradle-plugin")
}

Librarian.module(project)

dependencies {
  compileOnly(libs.ksp.gradle)
  compileOnly(libs.kgp.min)
  compileOnly(libs.gradle.api.min)
  implementation(libs.java.diff.utils)
  testImplementation(libs.kotlin.test)
}

gradlePlugin {
  plugins {
    create("com.apollographql.execution") {
      this.id = "com.apollographql.execution"
      this.displayName = "com.apollographql.execution"
      this.description = "Execution Gradle plugin"
      this.implementationClass = "com.apollographql.execution.gradle.ApolloExecutionPlugin"
    }
  }
}