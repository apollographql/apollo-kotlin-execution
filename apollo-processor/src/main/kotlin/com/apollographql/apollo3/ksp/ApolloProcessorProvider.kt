package com.apollographql.apollo3.ksp

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class ApolloProcessorProvider : SymbolProcessorProvider {
  override fun create(
      environment: SymbolProcessorEnvironment,
  ): SymbolProcessor {
    return ApolloProcessor(
        environment.codeGenerator,
        environment.logger,
        serviceName = environment.options.get("apolloService") ?: error("Apollo: serviceName is required:\nksp {\n  arg(\"apolloService\", \"service\")\n}"),
        packageName = environment.options.get("apolloPackageName") ?: error("Apollo: packageName is required:\nksp {\n  arg(\"apolloPackageName\", \"package\")\n}"),
    )
  }
}

