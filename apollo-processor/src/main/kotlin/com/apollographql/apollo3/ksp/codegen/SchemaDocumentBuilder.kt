package com.apollographql.apollo3.ksp.codegen

import com.apollographql.apollo3.ksp.codegen.KotlinSymbols.AstArgument
import com.apollographql.apollo3.ksp.codegen.KotlinSymbols.AstDirective
import com.apollographql.apollo3.ksp.codegen.KotlinSymbols.AstDocument
import com.apollographql.apollo3.ksp.codegen.KotlinSymbols.AstFieldDefinition
import com.apollographql.apollo3.ksp.codegen.KotlinSymbols.AstInputObjectTypeDefinition
import com.apollographql.apollo3.ksp.codegen.KotlinSymbols.AstInputValueDefinition
import com.apollographql.apollo3.ksp.codegen.KotlinSymbols.AstInterfaceTypeDefinition
import com.apollographql.apollo3.ksp.codegen.KotlinSymbols.AstObjectTypeDefinition
import com.apollographql.apollo3.ksp.codegen.KotlinSymbols.AstOperationTypeDefinition
import com.apollographql.apollo3.ksp.codegen.KotlinSymbols.AstSchemaDefinition
import com.apollographql.apollo3.ksp.codegen.KotlinSymbols.AstStringValue
import com.apollographql.apollo3.ksp.codegen.KotlinSymbols.AstUnionTypeDefinition
import com.apollographql.apollo3.compiler.decapitalizeFirstLetter
import com.apollographql.apollo3.ksp.sir.SirEnumDefinition
import com.apollographql.apollo3.ksp.sir.SirEnumValueDefinition
import com.apollographql.apollo3.ksp.sir.SirErrorType
import com.apollographql.apollo3.ksp.sir.SirExecutionContextArgumentDefinition
import com.apollographql.apollo3.ksp.sir.SirFieldDefinition
import com.apollographql.apollo3.ksp.sir.SirGraphQLArgumentDefinition
import com.apollographql.apollo3.ksp.sir.SirInputFieldDefinition
import com.apollographql.apollo3.ksp.sir.SirInputObjectDefinition
import com.apollographql.apollo3.ksp.sir.SirInterfaceDefinition
import com.apollographql.apollo3.ksp.sir.SirListType
import com.apollographql.apollo3.ksp.sir.SirNamedType
import com.apollographql.apollo3.ksp.sir.SirNonNullType
import com.apollographql.apollo3.ksp.sir.SirObjectDefinition
import com.apollographql.apollo3.ksp.sir.SirScalarDefinition
import com.apollographql.apollo3.ksp.sir.SirType
import com.apollographql.apollo3.ksp.sir.SirTypeDefinition
import com.apollographql.apollo3.ksp.sir.SirUnionDefinition
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.joinToCode

internal class SchemaDocumentBuilder(
    val context: com.apollographql.apollo3.ksp.codegen.KotlinExecutableSchemaContext,
    serviceName: String,
    val sirTypeDefinitions: List<SirTypeDefinition>,
) : CgFileBuilder {
  private val simpleName = "${serviceName.decapitalizeFirstLetter()}SchemaDocument"

  val schemaDocument: MemberName
    get() = MemberName(packageName = context.packageName, simpleName)

  override fun prepare() {}

  private fun propertySpec(): PropertySpec {
    return PropertySpec.builder(simpleName, AstDocument)
        .initializer(
            buildCode {
              add("%T(\n", AstDocument)
              indent {
                add("")
                add("definitions = listOf(\n")
                indent {
                  sirTypeDefinitions.forEach {
                    when (it) {
                      is SirScalarDefinition -> add("%L,\n", it.codeBlock())
                      is SirEnumDefinition -> add("%L,\n", it.codeBlock())
                      is SirInputObjectDefinition -> add("%L,\n", it.codeBlock())
                      is SirInterfaceDefinition -> add("%L,\n", it.codeBlock())
                      is SirObjectDefinition -> add("%L,\n", it.codeBlock())
                      is SirUnionDefinition -> add("%L,\n", it.codeBlock())
                    }
                  }
                  add("%L,\n", sirTypeDefinitions.schemaDefinitionCodeBlock())
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
private fun List<SirTypeDefinition>.schemaDefinitionCodeBlock(): CodeBlock {
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
  return buildCommon(className = AstInputObjectTypeDefinition, name = name, description = description, deprecationReason = null) {
    add("inputFields = listOf(\n")
    indent()
    inputFields.forEach {
      add("%L,\n", it.codeBlock())
    }
    unindent()
    add(")")
  }
}

private fun SirInputFieldDefinition.codeBlock(): CodeBlock {
  return buildCommon(className = AstInputValueDefinition, name = name, description = description, deprecationReason = deprecationReason) {
    add("type = %L,\n", type.codeBlock())
    if (defaultValue != null) {
      add("defaultValue = %S.%M().getOrThrow(),\n", defaultValue, KotlinSymbols.AstParseAsGQLValue)
    } else {
      add("defaultValue = null,\n")
    }
  }
}

private fun SirUnionDefinition.codeBlock(): CodeBlock {
  return buildCommon(className = AstUnionTypeDefinition, name = name, description = description, deprecationReason = null) {
    add("memberTypes = listOf(")
    memberTypes.forEach {
      add("%S,·", it)
    }
    add(")")
  }
}

private fun SirObjectDefinition.codeBlock(): CodeBlock {
  return buildCommon(className = AstObjectTypeDefinition, name = name, description = description, deprecationReason = null) {
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
  return buildCommon(className = AstInterfaceTypeDefinition, name = name, description = description, deprecationReason = null) {
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
  return buildCommon(className = AstFieldDefinition, name = name, description = description, deprecationReason = deprecationReason) {
    add("arguments = listOf(\n")
    indent()
    arguments.forEach {
      when (it) {
        is SirExecutionContextArgumentDefinition -> Unit
        is SirGraphQLArgumentDefinition -> {
          add("%L,\n", it.codeBlock())
        }
      }
    }
    unindent()
    add("),\n")
    add("type = %L,\n", type.codeBlock())
  }
}

private fun SirGraphQLArgumentDefinition.codeBlock(): CodeBlock {
  return buildCommon(className = AstInputValueDefinition, name = name, deprecationReason = deprecationReason, description = description) {
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
    deprecationReason: String?,
    block: CodeBlock.Builder.() -> Unit = {},
): CodeBlock {
  return buildCode {
    add("%T(\n", className)
    indent()
    add("sourceLocation = null,\n")
    add("description = %S,\n", description)
    add("name = %S,\n", name)
    add("directives = %L,\n", deprecationReason.toDirectives())
    block()
    unindent()
    add(")")
  }
}

private fun String?.toDirectives(): CodeBlock = buildCode {
  if (this@toDirectives == null) {
    add("emptyList()")
    return@buildCode
  }
  add("listOf(%T(null, %S, listOf(%T(null, %S, %T(null, %S)))))", AstDirective, "deprecated", AstArgument, "reason", AstStringValue, this@toDirectives)
}

private fun SirEnumDefinition.codeBlock(): CodeBlock {
  return buildCommon(className = KotlinSymbols.AstEnumTypeDefinition, name = name, description = description, deprecationReason = null) {
    add("enumValues = listOf(\n")
    indent()
    add("%L", values.map { it.codeBlock() }.joinToCode(",\n", suffix = ",\n"))
    unindent()
    add("),\n")
  }
}

internal fun SirEnumValueDefinition.codeBlock(): CodeBlock {
  return buildCommon(className = KotlinSymbols.AstEnumValueDefinition, name = name, deprecationReason = deprecationReason, description = description)
}

internal fun buildCode(block: CodeBlock.Builder.() -> Unit): CodeBlock {
  return CodeBlock.builder()
      .apply(block)
      .build()
}

private fun SirScalarDefinition.codeBlock(): CodeBlock {
  return buildCommon(className = KotlinSymbols.AstScalarTypeDefinition, name = name, description = description, deprecationReason = null)
}