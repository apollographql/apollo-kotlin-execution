import com.gradleup.librarian.core.librarianModule

plugins {
  id("org.jetbrains.kotlin.multiplatform")
}

librarianModule()

kotlin {
  jvm()
  macosArm64()

  sourceSets {
    getByName("commonMain") {
      dependencies {
        api(libs.apollo.ast)
        api(libs.apollo.api)
        implementation(libs.atomicfu)
        implementation(libs.coroutines)
      }
    }

    getByName("commonTest") {
      dependencies {
        implementation(libs.kotlin.test)
      }
    }
  }
}

