import com.gradleup.librarian.gradle.Librarian

plugins {
  id("org.jetbrains.kotlin.multiplatform")
}

Librarian.module(project)

kotlin {
  jvm()
  macosArm64()

  sourceSets {
    getByName("commonMain") {
      dependencies {
        api(libs.apollo.ast)
        api(libs.apollo.api)
        implementation(libs.atomicfu)
        api(libs.coroutines)
        api(libs.apollo.execution)
      }
    }

    getByName("commonTest") {
      dependencies {
        implementation(libs.kotlin.test)
      }
    }
  }
}

