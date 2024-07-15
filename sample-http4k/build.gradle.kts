plugins {
    alias(libs.plugins.kgp.jvm)
    alias(libs.plugins.ksp)
    id("com.apollographql.execution")
    id("application")
}

dependencies {
    implementation("com.apollographql.execution:apollo-execution-http4k")
    implementation(platform(libs.http4k.bom.get()))
    implementation(libs.http4k.core)
    implementation(libs.http4k.server.netty)
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