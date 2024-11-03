import com.gradleup.librarian.gradle.Librarian

plugins {
  id("org.jetbrains.kotlin.multiplatform")
}

Librarian.module(project)

kotlin {
  jvm()

  sourceSets {
    getByName("commonMain") {
      dependencies {
      }
    }
    getByName("jvmMain") {
      dependencies {
        api(project(":apollo-execution-runtime"))
        implementation(libs.coroutines)
        implementation(project.dependencies.platform(libs.http4k.bom.get()))
        implementation(libs.http4k.core)
        implementation(libs.http4k.realtime.core)
      }
    }
    getByName("jvmTest") {
      dependencies {
        implementation(libs.http4k.server.netty)
        implementation(libs.slf4j.nop)
      }
    }
    getByName("commonTest") {
      dependencies {
        implementation(libs.kotlin.test)
      }
    }
  }
}

