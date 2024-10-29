import com.gradleup.librarian.gradle.librarianRoot

buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
        classpath(libs.librarian.gradle.plugin)
        classpath(libs.wire.gradle.plugin)
        // workaround for https://github.com/Kotlin/kotlinx.serialization/issues/2803
        classpath(libs.kotlinx.serialization.core)
    }
    repositories {
        mavenCentral()
    }
}

librarianRoot()
