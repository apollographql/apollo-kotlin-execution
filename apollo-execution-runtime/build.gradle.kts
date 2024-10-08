import com.gradleup.librarian.gradle.librarianModule

plugins {
  id("org.jetbrains.kotlin.multiplatform")
}

librarianModule(true)

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
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
      }
    }

    getByName("commonTest") {
      dependencies {
        implementation(libs.kotlin.test)
      }
    }
  }
}

