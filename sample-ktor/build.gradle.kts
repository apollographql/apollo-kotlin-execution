plugins {
    alias(libs.plugins.kgp.jvm)
    alias(libs.plugins.ksp)
    id("com.apollographql.execution")
    id("application")
}

dependencies {
    implementation("com.apollographql.execution:apollo-execution-ktor")
    // This sample uses netty as an engine.
    // See https://ktor.io/docs/server-dependencies.html#core-dependencies for more details
    implementation(libs.ktor.server.netty)
    implementation(libs.slf4j.simple)

    testImplementation(libs.kotlin.test)
}

apolloExecution {
    service("service") {
        packageName = "com.example"
    }
}

application {
    mainClass.set("com.example.MainKt")
}