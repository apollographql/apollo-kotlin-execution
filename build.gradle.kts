import com.gradleup.librarian.gradle.librarianRoot

buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
        classpath(libs.librarian.gradle.plugin)
        classpath(libs.wire.gradle.plugin)
    }
    repositories {
        mavenCentral()
    }
}

librarianRoot()
