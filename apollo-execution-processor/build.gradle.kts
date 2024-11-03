import com.gradleup.librarian.gradle.Librarian

plugins {
  id("org.jetbrains.kotlin.jvm")
}

Librarian.module(project)

dependencies {
  implementation(libs.ksp.api)
  implementation(libs.kotlinpoet)
  implementation(libs.apollo.ast)
  testImplementation(libs.kotlin.test)
}
