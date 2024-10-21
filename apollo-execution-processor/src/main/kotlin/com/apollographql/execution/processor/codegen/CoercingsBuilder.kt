package com.apollographql.execution.processor.codegen

import com.apollographql.execution.processor.codegen.KotlinSymbols.AstEnumValue
import com.apollographql.execution.processor.codegen.KotlinSymbols.AstValue
import com.apollographql.execution.processor.sir.*
import com.apollographql.execution.processor.sir.SirDefinition
import com.apollographql.execution.processor.sir.SirEnumDefinition
import com.apollographql.execution.processor.sir.SirInputObjectDefinition
import com.apollographql.execution.processor.sir.SirNonNullType
import com.apollographql.execution.processor.sir.asKotlinPoet
import com.google.devtools.ksp.processing.KSPLogger
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.withIndent
import java.util.Locale

internal fun String.capitalizeFirstLetter(): String {
  return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
}

internal fun String.decapitalizeFirstLetter(): String {
  return this.replaceFirstChar { if (it.isUpperCase()) it.lowercase(Locale.ROOT) else it.toString() }
}

internal class CoercingsBuilder(
  private val context: KotlinExecutableSchemaContext,
  serviceName: String,
  sirDefinitions: List<SirDefinition>,
  private val logger: KSPLogger,
) : CgFileBuilder {
  private val sirEnumDefinitions = sirDefinitions.filterIsInstance<SirEnumDefinition>()
  private val sirInputObjectDefinitions = sirDefinitions.filterIsInstance<SirInputObjectDefinition>()
  val fileName = "${serviceName}Coercings".capitalizeFirstLetter()
  override fun prepare() {
    sirEnumDefinitions.forEach {
      context.coercings.put(it.name, MemberName(context.packageName, it.name.coercingName()))
    }
    sirInputObjectDefinitions.forEach {
      context.coercings.put(it.name, MemberName(context.packageName, it.name.coercingName()))
    }
  }

  private fun String.coercingName() = "${this}Coercing".decapitalizeFirstLetter()

  override fun build(): FileSpec {

    val funSpecs = if (sirEnumDefinitions.isNotEmpty() || sirInputObjectDefinitions.isNotEmpty()) {
      listOf(getInputFunSpec(), getRequiredInputFunSpec())
    } else {
      emptyList()
    }
    return FileSpec.builder(packageName = context.packageName, fileName = fileName)
      .apply {
        funSpecs.forEach {
          addFunction(it)
        }
        sirEnumDefinitions.map {
          it.propertySpec()
        }.forEach {
          addProperty(it)
        }
        sirInputObjectDefinitions.map {
          it.propertySpec()
        }.forEach {
          addProperty(it)
        }
      }
      .build()
  }

  private fun inputFunSpecInternal(name: String, block: CodeBlock.Builder.() -> Unit): FunSpec {
    return FunSpec.builder(name)
      .addAnnotation(AnnotationSpec.builder(KotlinSymbols.Suppress).addMember("\"UNCHECKED_CAST\"").build())
      .addTypeVariable(TypeVariableName("T"))
      .receiver(KotlinSymbols.Any.copy(nullable = true))
      .addModifiers(KModifier.PRIVATE)
      .addParameter(ParameterSpec("key", KotlinSymbols.String))
      .returns(TypeVariableName("T"))
      .addCode(
        buildCodeBlock {
          add("if (this == null) return null as T\n")
          add("check(this is %T<*,*>)\n", KotlinSymbols.Map)
          block()
        }
      )
      .build()
  }

  private fun getInputFunSpec(): FunSpec {
    return inputFunSpecInternal("getInput") {
      add("return if(containsKey(key)) {\n")
      withIndent {
        add("%T(get(key))\n", KotlinSymbols.Present)
      }
      add("} else {\n")
      withIndent {
        add("%T\n", KotlinSymbols.Absent)
      }
      add("} as T\n")
    }
  }

  private fun getRequiredInputFunSpec(): FunSpec {
    return inputFunSpecInternal("getRequiredInput") {
      add("return get(key) as T\n")
    }
  }

  private fun SirEnumDefinition.propertySpec(): PropertySpec {
    return PropertySpec.builder(
      name.coercingName(),
      KotlinSymbols.Coercing.parameterizedBy(targetClassName.asKotlinPoet())
    )
      .addModifiers(KModifier.INTERNAL)
      .initializer(
        buildCodeBlock {
          add("object: %T<%T> {\n", KotlinSymbols.Coercing, targetClassName.asKotlinPoet())
          withIndent {
            add("override fun serialize(internalValue: %T): Any?{\n", targetClassName.asKotlinPoet())
            withIndent {
              add("return internalValue.name\n")
            }
            add("}\n")
            add("override fun deserialize(value: Any?): %T {\n", targetClassName.asKotlinPoet())
            withIndent {
              add("return %T.valueOf(value.toString())\n", targetClassName.asKotlinPoet())
            }
            add("}\n")
            add("override fun parseLiteral(gqlValue: %T): %T {\n", AstValue, targetClassName.asKotlinPoet())
            withIndent {
              add("return %T.valueOf((gqlValue as %T).value)\n", targetClassName.asKotlinPoet(), AstEnumValue)
            }
            add("}\n")
          }
          add("}\n")
        }
      )
      .build()
  }

  private fun SirInputObjectDefinition.propertySpec(): PropertySpec {
    return PropertySpec.builder(
      name.coercingName(),
      KotlinSymbols.Coercing.parameterizedBy(targetClassName.asKotlinPoet())
    )
      .initializer(
        buildCodeBlock {
          add("object: %T<%T> {\n", KotlinSymbols.Coercing, targetClassName.asKotlinPoet())
          withIndent {
            add("override fun serialize(internalValue: %T): Any?{\n", targetClassName.asKotlinPoet())
            withIndent {
              add("error(\"Input objects cannot be serialized\")")
            }
            add("}\n")
            add("override fun deserialize(value: Any?): %T {\n", targetClassName.asKotlinPoet())
            withIndent {
              add("return %T(\n", targetClassName.asKotlinPoet())
              withIndent {
                inputFields.forEach {
                  val getInput = if (it.defaultValue == null && it.type !is SirNonNullType) {
                    "getInput"
                  } else {
                    "getRequiredInput"
                  }
                  add("%L = value.$getInput(%S),\n", it.name, it.name)
                }
              }
              add(")\n")
            }
            add("}\n")
            add("override fun parseLiteral(gqlValue: %T): %T {\n", AstValue, targetClassName.asKotlinPoet())
            withIndent {
              add("error(\"Input objects cannot be parsed from literals\")")
            }
            add("}\n")
          }
          add("}\n")
        }
      )
      .build()
  }
}