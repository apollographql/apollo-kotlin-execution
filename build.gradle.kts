import com.gradleup.librarian.gradle.librarianRoot

plugins {
    alias(libs.plugins.kgp.jvm).apply(false)
    alias(libs.plugins.librarian).apply(false)
}

librarianRoot()
