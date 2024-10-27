import com.gradleup.librarian.gradle.librarianModule

plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("com.squareup.wire")
}

librarianModule(true)

kotlin {
  jvm()
  macosArm64()

  sourceSets {
    getByName("commonMain") {
      dependencies {
        api(project(":apollo-execution-runtime"))
      }
    }

    getByName("commonTest") {
      dependencies {
        implementation(libs.kotlin.test)
      } }

  }
}

wire {
  kotlin {}
}
