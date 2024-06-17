plugins {
    id("org.jetbrains.kotlin.jvm").version("2.0.0")
    id("com.google.devtools.ksp").version("2.0.0-1.0.21")
    // Add the Apollo Execution plugin
    id("com.apollographql.execution").version("0.0.1")
}

dependencies {
    // Add the runtime dependency
    implementation("com.apollographql.execution:runtime-ktor:0.0.1")
    // This sample uses netty as an engine.
    // See https://ktor.io/docs/server-dependencies.html#core-dependencies for more details
    implementation("io.ktor:ktor-server-netty:2.3.11")
}

// Configure codegen
apolloExecution {
    service("service") {
        packageName = "com.example"
    }
}