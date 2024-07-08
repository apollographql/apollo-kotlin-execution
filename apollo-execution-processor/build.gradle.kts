import com.gradleup.librarian.gradle.librarianModule

plugins {
  id("org.jetbrains.kotlin.jvm")
}

librarianModule(true)

dependencies {
  implementation(libs.ksp.api)
  implementation(libs.kotlinpoet)
  implementation(libs.apollo.ast)
  testImplementation(libs.kotlin.test)
}
