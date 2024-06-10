import com.gradleup.librarian.core.librarianModule

plugins {
  id("org.jetbrains.kotlin.jvm")
}

librarianModule()

dependencies {
  implementation(libs.apollo.compiler)
  implementation(libs.ksp.api)
  implementation(libs.apollo.ast)
  testImplementation(libs.kotlin.test)
}
