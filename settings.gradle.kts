pluginManagement {
    listOf(repositories, dependencyResolutionManagement.repositories).forEach {
        it.mavenCentral()
    }
}

includeBuild("build-logic")

include("apollo-execution", "apollo-execution-ktor", "apollo-processor")
