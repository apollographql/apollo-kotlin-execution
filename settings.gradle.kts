pluginManagement {
  listOf(repositories, dependencyResolutionManagement.repositories).forEach {
    it.mavenCentral()
    it.maven("https://storage.googleapis.com/gradleup/m2")
    it.maven("https://storage.googleapis.com/apollo-previews/m2/")
  }
}

include(
  "apollo-execution-processor",
  "apollo-execution-gradle-plugin",
  "apollo-execution-runtime",
  "apollo-execution-ktor",
  "apollo-execution-http4k",
  "apollo-execution-spring",
  "apollo-execution-subgraph",
  "apollo-execution-reporting"
)

