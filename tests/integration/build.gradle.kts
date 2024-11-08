plugins {
  alias(libs.plugins.kgp.jvm)
  alias(libs.plugins.ksp)
  alias(libs.plugins.apollo.execution)
}

apolloExecution {
  service("service") {
    packageName.set("com.example")
  }
}

dependencies {
  implementation(libs.apollo.execution.runtime)
  testImplementation(libs.kotlin.test)
}