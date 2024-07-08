import com.gradleup.librarian.gradle.librarianRoot

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("build-logic:build-logic")
    }
}

librarianRoot()
