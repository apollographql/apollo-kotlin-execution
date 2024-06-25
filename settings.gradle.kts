pluginManagement {
  listOf(repositories, dependencyResolutionManagement.repositories).forEach {
    it.mavenCentral()
  }
}

includeBuild("build-logic")

include("processor", "gradle-plugin", "runtime", "runtime-ktor", "runtime-http4k")
