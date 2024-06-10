package com.apollographql.apollo3.ksp.codegen

import com.apollographql.apollo3.compiler.capitalizeFirstLetter
import com.apollographql.apollo3.ksp.sir.Instantiation
import com.apollographql.apollo3.ksp.sir.SirCoercing
import com.apollographql.apollo3.ksp.sir.SirEnumDefinition
import com.apollographql.apollo3.ksp.sir.SirInputObjectDefinition
import com.apollographql.apollo3.ksp.sir.SirObjectDefinition
import com.apollographql.apollo3.ksp.sir.SirScalarDefinition
import com.apollographql.apollo3.ksp.sir.asKotlinPoet
import com.apollographql.apollo3.ksp.sir.SirTypeDefinition
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.MemberName

internal class ExecutableSchemaBuilderBuilder(
    private val context: KotlinExecutableSchemaContext,
    private val serviceName: String,
    private val schemaDocument: MemberName,
    private val sirTypeDefinitions: List<SirTypeDefinition>,
) : CgFileBuilder {
  val simpleName = "${serviceName}ExecutableSchemaBuilder".capitalizeFirstLetter()
  override fun prepare() {}

  override fun build(): FileSpec {
    return FileSpec.builder(context.packageName, simpleName)
        .addFunction(funSpec())
        .build()
  }

  private fun funSpec(): FunSpec {
    return FunSpec.builder(simpleName)
        .returns(KotlinSymbols.ExecutableSchemaBuilder)
        .addCode(
            buildCode {
              add("@Suppress(\"UNCHECKED_CAST\")")
              add("fun <T> Any?.cast(): T = this as T\n\n")
              // Use a variable so that we don't get an expression return
              add("val schemaBuilder = %L()\n", KotlinSymbols.ExecutableSchemaBuilder)
              indent {
                add(".schema(%M)\n", schemaDocument)
                sirTypeDefinitions.filterIsInstance<SirObjectDefinition>().forEach { sirObjectDefinition ->
                  add(".addTypeChecker(%S)·{·it·is·%T·}\n", sirObjectDefinition.name, sirObjectDefinition.targetClassName.asKotlinPoet())
                  sirObjectDefinition.fields.forEach { irTargetField ->
                    val coordinates = "${sirObjectDefinition.name}.${irTargetField.name}"
                    add(".addResolver(%S)·{\n%L\n}\n", coordinates, resolverBody(sirObjectDefinition, irTargetField))
                  }
                }
                sirTypeDefinitions.filterIsInstance<SirScalarDefinition>().forEach { sirScalarDefinition ->
                  add(".addCoercing(%S, %L)\n", sirScalarDefinition.name, sirScalarDefinition.coercing.codeBlock())
                }
                sirTypeDefinitions.filterIsInstance<SirInputObjectDefinition>().forEach { sirInputObjectDefinition ->
                  add(".addCoercing(%S, %M)\n", sirInputObjectDefinition.name, context.coercings.get(sirInputObjectDefinition.name))
                }
                sirTypeDefinitions.filterIsInstance<SirEnumDefinition>().forEach { sirEnumDefinition ->
                  add(".addCoercing(%S, %M)\n", sirEnumDefinition.name, context.coercings.get(sirEnumDefinition.name))
                }
                listOf("query", "mutation", "subscription").forEach { operationType ->
                  val sirObjectDefinition = sirTypeDefinitions.rootType(operationType)
                  if (sirObjectDefinition != null && sirObjectDefinition.instantiation != Instantiation.UNKNOWN) {
                    add(".${operationType}Root { %L }\n", sirObjectDefinition.codeBlock())
                  }
                }
              }
              add("return schemaBuilder")
            }
        )
        .build()
  }
}

private fun SirCoercing.codeBlock(): CodeBlock {
  return buildCode {
    add("%T", className.asKotlinPoet())
    if (instantiation == Instantiation.NO_ARG_CONSTRUCTOR) {
      add("()")
    }
  }
}

internal fun List<SirTypeDefinition>.rootType(operationType: String): SirObjectDefinition? {
  return firstOrNull { it is SirObjectDefinition && it.operationType == operationType } as SirObjectDefinition?
}

private fun SirObjectDefinition.codeBlock(): CodeBlock {
  return buildCode {
    add("%T", targetClassName.asKotlinPoet())
    if (instantiation == Instantiation.NO_ARG_CONSTRUCTOR) {
      add("()")
    }
  }
}