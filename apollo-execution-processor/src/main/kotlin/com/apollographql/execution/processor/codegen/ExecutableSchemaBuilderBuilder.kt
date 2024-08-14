package com.apollographql.execution.processor.codegen

import com.apollographql.execution.processor.sir.*
import com.squareup.kotlinpoet.*

internal class ExecutableSchemaBuilderBuilder(
  private val context: KotlinExecutableSchemaContext,
  serviceName: String,
  private val schemaDocument: MemberName,
  private val sirDefinitions: List<SirDefinition>,
) : CgFileBuilder {
  private val simpleName = "${serviceName}ExecutableSchemaBuilder".capitalizeFirstLetter()
  override fun prepare() {}

  override fun build(): FileSpec {
    return FileSpec.builder(context.packageName, simpleName)
        .addFunction(funSpec())
        .build()
  }

  private fun funSpec(): FunSpec {
    return FunSpec.builder(simpleName)
        .returns(KotlinSymbols.ExecutableSchemaBuilder)
        .addModifiers(KModifier.INTERNAL)
        .addCode(
            buildCode {
              add("@Suppress(\"UNCHECKED_CAST\")")
              add("fun <T> Any?.cast(): T = this as T\n\n")
              // Use a variable so that we don't get an expression return
              add("val schemaBuilder = %L()\n", KotlinSymbols.ExecutableSchemaBuilder)
              indent {
                add(".schema(%M)\n", schemaDocument)
                sirDefinitions.filterIsInstance<SirObjectDefinition>().forEach { sirObjectDefinition ->
                  add(".addTypeChecker(%S)·{·it·is·%T·}\n", sirObjectDefinition.name, sirObjectDefinition.targetClassName.asKotlinPoet())
                  sirObjectDefinition.fields.forEach { irTargetField ->
                    val coordinates = "${sirObjectDefinition.name}.${irTargetField.name}"
                    add(".addResolver(%S)·{\n%L\n}\n", coordinates, resolverBody(sirObjectDefinition, irTargetField))
                  }
                }
                sirDefinitions.filterIsInstance<SirScalarDefinition>().forEach { sirScalarDefinition ->
                  add(".addCoercing(%S, %L)\n", sirScalarDefinition.name, sirScalarDefinition.coercing.codeBlock())
                }
                sirDefinitions.filterIsInstance<SirInputObjectDefinition>().forEach { sirInputObjectDefinition ->
                  add(".addCoercing(%S, %M)\n", sirInputObjectDefinition.name, context.coercings.get(sirInputObjectDefinition.name))
                }
                sirDefinitions.filterIsInstance<SirEnumDefinition>().forEach { sirEnumDefinition ->
                  add(".addCoercing(%S, %M)\n", sirEnumDefinition.name, context.coercings.get(sirEnumDefinition.name))
                }
                listOf("query", "mutation", "subscription").forEach { operationType ->
                  val sirObjectDefinition = sirDefinitions.rootType(operationType)
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

internal fun List<SirDefinition>.rootType(operationType: String): SirObjectDefinition? {
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