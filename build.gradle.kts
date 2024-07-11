import com.gradleup.librarian.gradle.librarianRoot

plugins {
    id("org.jetbrains.kotlin.multiplatform").version("2.0.0").apply(false)
    id("com.gradleup.librarian").version("0.0.4").apply(false)
}

librarianRoot()
