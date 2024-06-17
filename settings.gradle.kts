pluginManagement {
    listOf(repositories, dependencyResolutionManagement.repositories).forEach {
        it.mavenCentral()
    }
}

includeBuild("build-logic")

include("runtime", "runtime-ktor", "processor", "gradle-plugin")
