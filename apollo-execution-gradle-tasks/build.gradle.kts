import com.gradleup.librarian.gradle.Librarian

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("com.google.devtools.ksp")
  id("com.gradleup.gratatouille.tasks")
}

Librarian.module(project)

gratatouille {
  codeGeneration {
    classLoaderIsolation()
    addDependencies.set(false)
  }
}

dependencies {
  implementation(libs.java.diff.utils)
  implementation(libs.gratatouille.tasks.runtime)
  testImplementation(libs.kotlin.test)
}
