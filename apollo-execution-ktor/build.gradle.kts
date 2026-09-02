import com.gradleup.librarian.gradle.Librarian

plugins {
  id("org.jetbrains.kotlin.multiplatform")
}

Librarian.module(project)

kotlin {
  jvm()
  macosArm64()
  linuxX64()
  linuxArm64()

  sourceSets {
    getByName("commonMain") {
      dependencies {
        api(project(":apollo-execution-runtime"))
        implementation(libs.atomicfu)
        api(libs.coroutines)
        api(libs.ktor.server.core)
        api(libs.apollo.execution)
        implementation(libs.ktor.server.websockets)
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

