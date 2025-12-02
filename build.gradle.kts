import com.gradleup.librarian.gradle.Librarian

plugins {
    id("base")
}
buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
        classpath(libs.gratatouille)
        classpath(libs.ksp.gradle)
        classpath(libs.librarian.gradle.plugin)
        classpath(libs.wire.gradle.plugin)
        // workaround for https://github.com/Kotlin/kotlinx.serialization/issues/2803
        classpath(libs.kotlinx.serialization.core)
    }
}

Librarian.root(project)
