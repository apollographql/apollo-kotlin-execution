package com.apollographql.execution.processor.codegen

import com.apollographql.apollo.ast.*
import com.apollographql.execution.processor.codegen.KotlinSymbols.AstBooleanValue
import com.apollographql.execution.processor.codegen.KotlinSymbols.AstDirective
import com.apollographql.execution.processor.codegen.KotlinSymbols.AstDirectiveDefinition
import com.apollographql.execution.processor.codegen.KotlinSymbols.AstDocument
import com.apollographql.execution.processor.codegen.KotlinSymbols.AstEnumValue
import com.apollographql.execution.processor.codegen.KotlinSymbols.AstFieldDefinition
import com.apollographql.execution.processor.codegen.KotlinSymbols.AstFloatValue
import com.apollographql.execution.processor.codegen.KotlinSymbols.AstInputObjectTypeDefinition
import com.apollographql.execution.processor.codegen.KotlinSymbols.AstInputValueDefinition
import com.apollographql.execution.processor.codegen.KotlinSymbols.AstIntValue
import com.apollographql.execution.processor.codegen.KotlinSymbols.AstInterfaceTypeDefinition
import com.apollographql.execution.processor.codegen.KotlinSymbols.AstListValue
import com.apollographql.execution.processor.codegen.KotlinSymbols.AstNullValue
import com.apollographql.execution.processor.codegen.KotlinSymbols.AstObjectField
import com.apollographql.execution.processor.codegen.KotlinSymbols.AstObjectTypeDefinition
import com.apollographql.execution.processor.codegen.KotlinSymbols.AstObjectValue
import com.apollographql.execution.processor.codegen.KotlinSymbols.AstOperationTypeDefinition
import com.apollographql.execution.processor.codegen.KotlinSymbols.AstSchemaDefinition
import com.apollographql.execution.processor.codegen.KotlinSymbols.AstStringValue
import com.apollographql.execution.processor.codegen.KotlinSymbols.AstUnionTypeDefinition
import com.apollographql.execution.processor.sir.*
import com.apollographql.execution.processor.sir.SirDefinition
import com.apollographql.execution.processor.sir.SirEnumDefinition
import com.apollographql.execution.processor.sir.SirEnumValueDefinition
import com.apollographql.execution.processor.sir.SirErrorType
import com.apollographql.execution.processor.sir.SirExecutionContextArgumentDefinition
import com.apollographql.execution.processor.sir.SirFieldDefinition
import com.apollographql.execution.processor.sir.SirInputValueDefinition
import com.apollographql.execution.processor.sir.SirInputObjectDefinition
import com.apollographql.execution.processor.sir.SirInterfaceDefinition
import com.apollographql.execution.processor.sir.SirListType
import com.apollographql.execution.processor.sir.SirNamedType
import com.apollographql.execution.processor.sir.SirNonNullType
import com.apollographql.execution.processor.sir.SirObjectDefinition
import com.apollographql.execution.processor.sir.SirScalarDefinition
import com.apollographql.execution.processor.sir.SirType
import com.apollographql.execution.processor.sir.SirUnionDefinition
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.joinToCode

internal class SchemaDocumentBuilder(
  val context: KotlinExecutableSchemaContext,
  serviceName: String,
  val sirDefinitions: List<SirDefinition>,
) : CgFileBuilder {
  private val simpleName = "${serviceName.decapitalizeFirstLetter()}SchemaDocument"

  val schemaDocument: MemberName
    get() = MemberName(packageName = context.packageName, simpleName)

  override fun prepare() {}

  private fun propertySpec(): PropertySpec {
    return PropertySpec.builder(simpleName, AstDocument)
        .addModifiers(KModifier.INTERNAL)
        .initializer(
            buildCode {
              add("%T(\n", AstDocument)
              indent {
                add("")
                add("definitions = listOf(\n")
                indent {
                  add("%L,\n", sirDefinitions.schemaDefinitionCodeBlock())
                  sirDefinitions.forEach {
                    when (it) {
                      is SirScalarDefinition -> add("%L,\n", it.codeBlock())
                      is SirEnumDefinition -> add("%L,\n", it.codeBlock())
                      is SirInputObjectDefinition -> add("%L,\n", it.codeBlock())
                      is SirInterfaceDefinition -> add("%L,\n", it.codeBlock())
                      is SirObjectDefinition -> add("%L,\n", it.codeBlock())
                      is SirUnionDefinition -> add("%L,\n", it.codeBlock())
                      is SirDirectiveDefinition -> add("%L,\n", it.codeBlock())
                    }
                  }
                }
                add("),\n")
                add("sourceLocation = null,\n")
              }
              add(")\n")
            }
        )
        .build()
  }

  override fun build(): FileSpec {
    return FileSpec.builder(context.packageName, simpleName)
        .addProperty(propertySpec())
        .build()
  }
}

/**
 * ```
 * fun foo() {
 *   GQLSchemaDefinition(
 *       rootOperationTypeDefinitions = listOf(
 *           GQLOperationTypeDefinition(
 *               operationType = "",
 *               namedType = ""
 *           )
 *       )
 *   )
 * }
 * ``
 */
private fun List<SirDefinition>.schemaDefinitionCodeBlock(): CodeBlock {
  return buildCode {
    add("%T(\n", AstSchemaDefinition)
    indent {
      add("sourceLocation = null,\n")
      add("description = null,\n")
      add("directives = emptyList(),\n")
      add("rootOperationTypeDefinitions = listOf(\n")
      indent {
        filterIsInstance<SirObjectDefinition>().filter { it.operationType != null }.forEach {
          add("%T(\n", AstOperationTypeDefinition)
          indent {
            add("operationType = %S,\n", it.operationType)
            add("namedType = %S,\n", it.name)
          }
          add("),\n")
        }
      }
      add(")\n")
    }
    add(")")
  }
}

private fun SirInputObjectDefinition.codeBlock(): CodeBlock {
  return buildCommon(className = AstInputObjectTypeDefinition, name = name, description = description, directives = directives) {
    add("inputFields = listOf(\n")
    indent()
    inputFields.forEach {
      add("%L,\n", it.codeBlock())
    }
    unindent()
    add(")")
  }
}

private fun SirUnionDefinition.codeBlock(): CodeBlock {
  return buildCommon(className = AstUnionTypeDefinition, name = name, description = description, directives = directives) {
    add("memberTypes = listOf(")
    memberTypes.forEach {
      add("%S,·", it)
    }
    add(")")
  }
}

internal fun CodeBlock.Builder.indent(block: CodeBlock.Builder.() -> Unit) {
  indent()
  block()
  unindent()
}

private fun SirDirectiveDefinition.codeBlock(): CodeBlock {
  return buildCode {
    add("%T(\n", AstDirectiveDefinition)
    indent {
      add("sourceLocation·=·null,\n")
      add("description·=·%S,\n", description)
      add("name·=·%S,\n", name)
      add("arguments·=·listOf(\n")
      indent {
        argumentDefinitions.forEach {
          add("%L,\n", it.codeBlock())
        }
      }
      add("),\n")
      add("repeatable·=·%L,\n", repeatable.toString())
      add("locations·=·%L\n", locations.map { it.codeBlock() }.joinToCode(prefix = "listOf(", suffix = ")"))
    }
    add(")")
  }
}

private fun GQLDirectiveLocation.codeBlock(): CodeBlock {
  return CodeBlock.of("%T", ClassName("com.apollographql.apollo.ast", name))
}

private fun SirObjectDefinition.codeBlock(): CodeBlock {
  return buildCommon(className = AstObjectTypeDefinition, name = name, description = description, directives = directives) {
    add("implementsInterfaces = listOf(%L),\n", interfaces.map { CodeBlock.builder().add("%S", it).build() }.joinToCode(",·"))
    add("fields = listOf(\n")
    indent()
    fields.forEach {
      add("%L,\n", it.codeBlock())
    }
    unindent()
    add("),\n")
  }
}

private fun SirInterfaceDefinition.codeBlock(): CodeBlock {
  return buildCommon(className = AstInterfaceTypeDefinition, name = name, description = description, directives = directives) {
    add("implementsInterfaces = listOf(%L),\n", interfaces.map { CodeBlock.builder().add("%S", it).build() }.joinToCode(",·"))
    add("fields = listOf(\n")
    indent()
    fields.forEach {
      add("%L,\n", it.codeBlock())
    }
    unindent()
    add("),\n")
  }
}

private fun SirFieldDefinition.codeBlock(): CodeBlock {
  return buildCommon(className = AstFieldDefinition, name = name, description = description, directives = directives) {
    add("arguments = listOf(\n")
    indent()
    arguments.forEach {
      when (it) {
        is SirExecutionContextArgumentDefinition -> Unit
        is SirInputValueDefinition -> {
          add("%L,\n", it.codeBlock())
        }
      }
    }
    unindent()
    add("),\n")
    add("type = %L,\n", type.codeBlock())
  }
}

private fun SirInputValueDefinition.codeBlock(): CodeBlock {
  return buildCommon(className = AstInputValueDefinition, name = name, directives = directives, description = description) {
    add("type = %L,\n", type.codeBlock())
    if (defaultValue != null) {
      add("defaultValue = %S.%M().getOrThrow(),\n", defaultValue, KotlinSymbols.AstParseAsGQLValue)
    } else {
      add("defaultValue = null,\n")
    }
  }
}

private fun SirType.codeBlock(): CodeBlock {
  return when (this) {
    SirErrorType -> CodeBlock.of("%T", ClassName("kotlin", "Nothing"))
    is SirListType -> CodeBlock.of("%T(type·=·%L)", KotlinSymbols.AstListType, type.codeBlock())
    is SirNamedType -> CodeBlock.of("%T(name·=·%S)", KotlinSymbols.AstNamedType, name)
    is SirNonNullType -> CodeBlock.of("%T(type·=·%L)", KotlinSymbols.AstNonNullType, type.codeBlock())
  }
}

private fun buildCommon(
    className: ClassName,
    name: String,
    description: String?,
    directives: List<SirDirective>,
    block: CodeBlock.Builder.() -> Unit = {},
): CodeBlock {
  return buildCode {
    add("%T(\n", className)
    indent()
    add("sourceLocation·=·null,\n")
    add("description·=·%S,\n", description)
    add("name·=·%S,\n", name)
    add("directives·=·%L,\n", directives.map { it.codeBlock() }.joinToCode(prefix = "listOf(", suffix = ")"))
    block()
    unindent()
    add(")")
  }
}

private fun SirDirective.codeBlock(): CodeBlock {
  return CodeBlock.of("%T(null, %S, %L)", AstDirective, name, arguments.map { it.codeBlock() }.joinToCode(prefix = "listOf(", suffix = ")"))
}

private fun SirArgument.codeBlock(): CodeBlock {
  return value.codeBlock()
}

private fun GQLValue.codeBlock(): CodeBlock {
  return when (this) {
    is GQLBooleanValue -> CodeBlock.of("%T(null, %L)", AstBooleanValue, value.toString())
    is GQLEnumValue -> CodeBlock.of("%T(null, %S)", AstEnumValue, value)
    is GQLFloatValue -> CodeBlock.of("%T(null, %S)", AstFloatValue, value)
    is GQLIntValue -> CodeBlock.of("%T(null, %S)", AstIntValue, value)
    is GQLListValue -> CodeBlock.of("%T(null, %L)", AstListValue, values.map { it.codeBlock() }.joinToCode(prefix = "listOf(", suffix = ")"))
    is GQLNullValue -> CodeBlock.of("%T(null)", AstNullValue)
    is GQLObjectValue -> CodeBlock.of("%T(null, %L)", AstObjectValue, fields.map { it.codeBlock() }.joinToCode(prefix = "listOf(", suffix = ")"))
    is GQLStringValue -> CodeBlock.of("%T(null, %S)", AstStringValue, value)
    is GQLVariableValue -> error("Variable cannot be used in const position")
  }
}

private fun GQLObjectField.codeBlock(): CodeBlock {
  return CodeBlock.of("%T(null, %S, %L)", AstObjectField, name, value.codeBlock())
}

private fun SirEnumDefinition.codeBlock(): CodeBlock {
  return buildCommon(className = KotlinSymbols.AstEnumTypeDefinition, name = name, description = description, directives = directives) {
    add("enumValues = listOf(\n")
    indent()
    add("%L", values.map { it.codeBlock() }.joinToCode(",\n", suffix = ",\n"))
    unindent()
    add("),\n")
  }
}

internal fun SirEnumValueDefinition.codeBlock(): CodeBlock {
  return buildCommon(className = KotlinSymbols.AstEnumValueDefinition, name = name, description = description, directives = directives)
}

internal fun buildCode(block: CodeBlock.Builder.() -> Unit): CodeBlock {
  return CodeBlock.builder()
      .apply(block)
      .build()
}

private fun SirScalarDefinition.codeBlock(): CodeBlock {
  return buildCommon(className = KotlinSymbols.AstScalarTypeDefinition, name = name, description = description, directives = directives)
}