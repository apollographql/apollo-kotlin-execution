import com.gradleup.librarian.core.librarianModule

plugins {
  id("org.jetbrains.kotlin.multiplatform")
}

librarianModule()

kotlin {
  jvm()

  sourceSets {
    getByName("commonMain") {
      dependencies {
        api(project(":apollo-execution"))
        implementation(libs.atomicfu)
        api(libs.coroutines)
        api(libs.ktor.server.core)
      }
    }
    getByName("jvmTest") {
      dependencies {
        implementation(libs.ktor.server.netty)
        implementation(libs.ktor.server.cors)
        implementation(libs.slf4j)
      }
    }
    getByName("commonTest") {
      dependencies {
        implementation(libs.kotlin.test)
      }
    }
  }
}

