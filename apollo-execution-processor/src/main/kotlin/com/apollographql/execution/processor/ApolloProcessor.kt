package com.apollographql.execution.processor

import com.apollographql.apollo.ast.GQLObjectTypeDefinition
import com.apollographql.execution.processor.codegen.*
import com.apollographql.execution.processor.sir.SirClassName
import com.apollographql.execution.processor.sir.SirObjectDefinition
import com.apollographql.execution.processor.sir.maybeFederate
import com.google.devtools.ksp.containingFile
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.AnnotationSpec

class ApolloProcessor(
  private val codeGenerator: CodeGenerator,
  private val logger: KSPLogger,
  private val packageName: String,
  private val serviceName: String,
) : SymbolProcessor {
  private var done = false

  private fun getRootSymbol(resolver: Resolver, annotationName: String): KSClassDeclaration? {
    val ret = getSymbolsWithAnnotation(resolver, annotationName).toList()

    if (ret.size > 1) {
      val locations = ret.map { it.location }.joinToString("\n")
      logger.error("There can be only one '$annotationName' annotated class, found ${ret.size}:\n$locations", ret.first())
      return null
    }

    ret.forEach {
      if (it !is KSClassDeclaration || it.isAbstract()) {
        logger.error("'$annotationName' cannot be set on node $it", it)
        return null
      }
    }

    return ret.singleOrNull() as KSClassDeclaration?
  }

  private fun getSymbolsWithAnnotation(resolver: Resolver, annotationName: String): List<KSAnnotated> {
    return resolver.getSymbolsWithAnnotation(annotationName)
      .filter { it.containingFile != null }
      .toList()
  }

  override fun process(resolver: Resolver): List<KSAnnotated> {
    if (done) {
      return emptyList()
    }

    done = true

    val query = getRootSymbol(resolver, KotlinSymbols.GraphQLQuery.canonicalName)
    if (query == null) {
      logger.error("No '@GraphQLQuery' class found")
      return emptyList()
    }

    val result = doTraversal(
      logger,
      query,
      getRootSymbol(resolver, KotlinSymbols.GraphQLMutation.canonicalName),
      getRootSymbol(resolver, KotlinSymbols.GraphQLSubscription.canonicalName),
    )
    val definitions = result.definitions

    val context = KotlinExecutableSchemaContext(packageName)
    val maybeFederatedDefinitions = definitions.maybeFederate()
    val schemaDocumentBuilder = SchemaDocumentBuilder(
      context = context,
      serviceName = serviceName,
      sirDefinitions = maybeFederatedDefinitions
    )

    val builders = mutableListOf<CgFileBuilder>()

    builders.add(schemaDocumentBuilder)

    builders.add(
      CoercingsBuilder(
        context = context,
        serviceName = serviceName,
        sirDefinitions = definitions,
        logger = logger
      )
    )

    val entities = definitions.filterIsInstance<SirObjectDefinition>().filter { it.isEntity }
    val entityResolverBuilder = if (entities.isNotEmpty()) {
      EntityResolverBuilder(
        context = context,
        serviceName = serviceName,
        entities = entities,
      ).also {
        builders.add(it)
      }
    } else {
      null
    }

    builders.add(
      ExecutableSchemaBuilderBuilder(
        context = context,
        serviceName = serviceName,
        schemaDocument = schemaDocumentBuilder.schemaDocument,
        sirDefinitions = definitions,
        entityResolver = entityResolverBuilder?.entityResolver
      )
    )

    builders.forEach {
      it.prepare()
    }

    val dependencies = Dependencies(true, *result.analyzedFiles.toTypedArray())
    builders.map {
      it.build()
        .toBuilder()
        .addAnnotation(AnnotationSpec.builder(KotlinSymbols.Suppress).addMember("\"DEPRECATION\"").build())
        .addFileComment(
          """
                
                AUTO-GENERATED FILE. DO NOT MODIFY.
                
                This class was automatically generated by Apollo GraphQL version '$VERSION'.
                
          """.trimIndent()
        ).build()
    }
      .forEach { sourceFile ->
        codeGenerator.createNewFile(
          dependencies,
          packageName = sourceFile.packageName,
          // SourceFile contains.kt
          fileName = sourceFile.name.substringBeforeLast('.'),
        ).bufferedWriter().use {
          sourceFile.writeTo(it)
        }
      }

    codeGenerator.createNewFileByPath(
      dependencies,
      "${serviceName}Schema.graphqls",
      "",
    ).bufferedWriter().use {
      it.write(schemaString(maybeFederatedDefinitions))
    }
    return emptyList()
  }
}

internal fun KSClassDeclaration.hasNoArgsConstructor(): Boolean {
  return getConstructors().any {
    it.parameters.isEmpty()
  }
}

//internal fun KSAnnotated.deprecationReason(): String? {
//  return findAnnotation("Deprecated")?.getArgumentValueAsString("reason")
//}

internal fun KSDeclaration.graphqlName(): String {
  return graphqlNameOrNull() ?: simpleName.asString()
}

internal fun KSAnnotated.graphqlNameOrNull(): String? {
  return findAnnotation("GraphQLName")?.getArgumentValueAsString("name")
}

internal fun KSPropertyDeclaration.graphqlName(): String {
  return graphqlNameOrNull() ?: simpleName.asString()
}

internal fun KSAnnotated.findAnnotation(name: String): KSAnnotation? {
  return annotations.firstOrNull { it.shortName.asString() == name }
}

internal fun KSAnnotation.getArgumentValue(name: String): Any? {
  return arguments.firstOrNull {
    it.name!!.asString() == name
  }?.value
}

internal fun KSAnnotation.getArgumentValueAsString(name: String): String? {
  return getArgumentValue(name)?.toString()
}

internal fun KSDeclaration.asClassName(): SirClassName {
  return SirClassName(packageName.asString(), listOf(simpleName.asString()))
}

internal val executionContextClassName = SirClassName("com.apollographql.apollo.api", listOf("ExecutionContext"))



