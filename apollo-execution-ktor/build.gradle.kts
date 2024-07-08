import com.gradleup.librarian.gradle.librarianModule

plugins {
  id("org.jetbrains.kotlin.multiplatform")
}

librarianModule(true)

kotlin {
  jvm()

  sourceSets {
    getByName("commonMain") {
      dependencies {
        api(project(":apollo-execution-runtime"))
        implementation(libs.atomicfu)
        api(libs.coroutines)
        api(libs.ktor.server.core)
      }
    }
    getByName("jvmTest") {
      dependencies {
        implementation(libs.ktor.server.netty)
        implementation(libs.ktor.server.cors)
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

