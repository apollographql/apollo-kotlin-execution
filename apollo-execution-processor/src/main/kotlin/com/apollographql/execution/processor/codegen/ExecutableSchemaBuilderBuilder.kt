package com.apollographql.execution.processor.codegen

import com.apollographql.apollo.annotations.ApolloExperimental
import com.apollographql.apollo.ast.GQLObjectTypeDefinition
import com.apollographql.execution.processor.sir.*
import com.squareup.kotlinpoet.*

internal class ExecutableSchemaBuilderBuilder(
  private val context: KotlinExecutableSchemaContext,
  serviceName: String,
  private val schemaDocument: MemberName,
  private val entityResolver: MemberName?,
  private val sirDefinitions: List<SirDefinition>,
) : CgFileBuilder {
  private val simpleName = "${serviceName}ExecutableSchemaBuilder".capitalizeFirstLetter()
  override fun prepare() {}

  override fun build(): FileSpec {
    @file:OptIn(ApolloExperimental::class)
    return FileSpec.builder(context.packageName, simpleName)
      .addAnnotation(AnnotationSpec
        .builder(ClassName("kotlin", "OptIn"))
        .addMember(CodeBlock.of("%T::class", ClassName("com.apollographql.apollo.annotations", "ApolloExperimental")))
        .build()
      )
        .addFunction(funSpec())
        .build()
  }

  private fun funSpec(): FunSpec {

    return FunSpec.builder(simpleName)
        .returns(KotlinSymbols.ExecutableSchemaBuilder)
        .addModifiers(KModifier.INTERNAL)
        .addCode(
            buildCodeBlock {
              add("@Suppress(\"UNCHECKED_CAST\")")
              add("fun <T> Any?.cast(): T = this as T\n\n")
              // Use a variable so that we don't get an expression return
              add("val schemaBuilder = %L()\n", KotlinSymbols.ExecutableSchemaBuilder)
              withIndent {
                add(".schema(%M)\n", schemaDocument)
                add(".%M·{\n", MemberName(KotlinSymbols.executionPackageName, "compositeResolver"))
                withIndent {
                  sirDefinitions.filterIsInstance<SirObjectDefinition>().forEach { sirObjectDefinition ->
                    add("type(%S)·{\n", sirObjectDefinition.name)
                    withIndent {
                      sirObjectDefinition.fields.forEach { irTargetField ->
                        add("field(%S)·{\n", irTargetField.name)
                        withIndent {
                          add(resolverBody(sirObjectDefinition, irTargetField))
                        }
                        add("}\n")
                      }
                      if (sirObjectDefinition.operationType == "query" && entityResolver != null) {
                        /**
                         * Federation meta fields
                         */
                        add("field(%S)·{}\n", "_service")
                        add("field(%S)·{\n", "_entities")
                        withIndent {
                          add("val representations·=·it.getRequiredArgument<List<Map<String,·Any>>>(\"representations\")\n")
                          add("representations.map·{·representation·->\n")
                          withIndent {
                            add("%M(it.executionContext,·representation)\n", entityResolver)
                          }
                          add("}\n")
                        }
                        add("}\n")
                      }
                    }
                    add("}\n")
                  }
                  if (entityResolver != null) {
                    add("type(%S)·{\n", "_Service")
                    withIndent {
                      add("field(%S)·{\n", "sdl")
                      withIndent {
                        add("%M.%M(\"  \")", schemaDocument, MemberName("com.apollographql.apollo.ast", "toSDL"))
                      }
                      add("}\n")
                    }
                    add("}\n")
                  }
                }
                add("}\n")
                add(".typeResolver·{·obj,·_·->\n", schemaDocument)
                withIndent {
                  add("when(obj)·{\n")
                  withIndent {
                    sirDefinitions.filterIsInstance<SirObjectDefinition>().forEach { sirObjectDefinition ->
                      add("is·%T·->·%S\n", sirObjectDefinition.targetClassName.asKotlinPoet(), sirObjectDefinition.name)
                    }
                    add("else·->·error(\"Cannot resolve '${'$'}obj'\")")
                  }
                  add("}\n")
                }
                add("}\n")
                sirDefinitions.filterIsInstance<SirScalarDefinition>().forEach { sirScalarDefinition ->
                  add(".addCoercing(%S, %L)\n", sirScalarDefinition.name, sirScalarDefinition.coercing.codeBlock())
                }
                sirDefinitions.filterIsInstance<SirInputObjectDefinition>().forEach { sirInputObjectDefinition ->
                  add(".addCoercing(%S, %M)\n", sirInputObjectDefinition.name, context.coercings.get(sirInputObjectDefinition.name))
                }
                sirDefinitions.filterIsInstance<SirEnumDefinition>().forEach { sirEnumDefinition ->
                  add(".addCoercing(%S, %M)\n", sirEnumDefinition.name, context.coercings.get(sirEnumDefinition.name))
                }
                if (entityResolver != null) {
                  add(".addCoercing(%S, %M)\n", "_Any", MemberName(KotlinSymbols.subgraphPackageName, "_AnyCoercing"))
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
  return buildCodeBlock {
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
  return buildCodeBlock {
    add("%T", targetClassName.asKotlinPoet())
    if (instantiation == Instantiation.NO_ARG_CONSTRUCTOR) {
      add("()")
    }
  }
}