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
      }
    }
    getByName("jvmMain") {
      dependencies {
        api(project(":apollo-execution-runtime"))
        implementation(libs.coroutines)
        implementation(libs.spring.webflux)
      }
    }
    getByName("jvmTest") {
      dependencies {
      }
    }
    getByName("commonTest") {
      dependencies {
        implementation(libs.kotlin.test)
      }
    }
  }
}

