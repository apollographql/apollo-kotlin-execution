import com.gradleup.librarian.gradle.Librarian

plugins {
  id("org.jetbrains.kotlin.multiplatform")
  id("com.squareup.wire")
}

Librarian.module(project)

kotlin {
  jvm()
  macosArm64()

  sourceSets {
    getByName("commonMain") {
      dependencies {
        api(project(":apollo-execution-runtime"))
        implementation(libs.kotlinx.datetime)
        implementation(libs.ktor.client.cio)
      }
    }

    getByName("commonTest") {
      dependencies {
        implementation(libs.kotlin.test)
      }
    }
  }
}

wire {
  kotlin {

  }
}
