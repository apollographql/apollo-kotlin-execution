package com.apollographql.execution.processor.codegen

import com.apollographql.execution.processor.sir.*
import com.apollographql.execution.processor.sir.SirExecutionContextArgumentDefinition
import com.apollographql.execution.processor.sir.SirInputValueDefinition
import com.apollographql.execution.processor.sir.SirObjectDefinition
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.withIndent

internal class EntityResolverBuilder(
  val context: KotlinExecutableSchemaContext,
  serviceName: String,
  val entities: List<SirObjectDefinition>,
) : CgFileBuilder {
  private val simpleName = "${serviceName.decapitalizeFirstLetter()}EntityResolver"

  val entityResolver: MemberName
    get() = MemberName(packageName = context.packageName, simpleName)

  override fun prepare() {}

  private fun funSpec(): FunSpec {
    return FunSpec.builder(simpleName)
      .returns(ClassName("kotlin", "Any"))
      .addModifiers(KModifier.INTERNAL, KModifier.SUSPEND)
      .addParameter(
        ParameterSpec.builder(
          "executionContext",
          ClassName("com.apollographql.apollo.api", "ExecutionContext")
        ).build()
      )
      .addParameter(
        ParameterSpec.builder(
          "representation", ClassName("kotlin.collections", "Map").parameterizedBy(
            ClassName("kotlin", "String"),
            ClassName("kotlin", "Any")
          )
        ).build()
      )
      .addCode(
        buildCodeBlock {
          add("@Suppress(\"UNCHECKED_CAST\")")
          add("fun <T> Any?.cast():·T =·this·as·T\n\n")

          add("return when(val·typename·=·representation[\"__typename\"])·{\n")
          withIndent {
            entities.forEach { entity ->
              add("%S·->·%T.resolve(", entity.name, entity.targetClassName.asKotlinPoet())
              entity.resolve!!.arguments.forEach {
                when (it) {
                  is SirExecutionContextArgumentDefinition -> {
                    add("executionContext,·")
                  }

                  is SirInputValueDefinition -> {
                    add("representation.get(%S).cast(),·", it.name)
                  }
                }
              }
              add(")\n")
            }
            add("else·->·error(\"Unknown entity:'${'$'}typename'\")\n")
          }
          add("}\n")
        }
      )
      .build()
  }

  override fun build(): FileSpec {
    return FileSpec.builder(context.packageName, simpleName)
      .addFunction(funSpec())
      .build()
  }
}
