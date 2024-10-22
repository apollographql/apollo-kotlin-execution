package com.apollographql.execution.processor.codegen

import com.apollographql.execution.processor.sir.SirArgumentDefinition
import com.apollographql.execution.processor.sir.SirExecutionContextArgumentDefinition
import com.apollographql.execution.processor.sir.SirFieldDefinition
import com.apollographql.execution.processor.sir.SirInputValueDefinition
import com.apollographql.execution.processor.sir.SirNonNullType
import com.apollographql.execution.processor.sir.SirObjectDefinition
import com.apollographql.execution.processor.sir.asKotlinPoet
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.withIndent

internal fun resolverBody(sirObjectDefinition: SirObjectDefinition, sirTargetField: SirFieldDefinition): CodeBlock {
  return buildCodeBlock {
    add("it.parentObject.cast<%T>().%L", sirObjectDefinition.targetClassName.asKotlinPoet(), sirTargetField.targetName)
    if (sirTargetField.isFunction) {
      add("(")
      if (sirTargetField.arguments.isNotEmpty()) {
        add("\n")
        withIndent {
          add(sirTargetField.arguments.map { argumentCodeBlock(it) }.joinToCode(",\n"))
        }
      }
      add(")\n")
    }
  }
}

internal fun CodeBlock.Builder.indent(condition: Boolean = true, block: CodeBlock.Builder.() -> Unit) {
  if (condition) {
    indent()
  }
  block()
  if (condition) {
    unindent()
  }
}

private fun argumentCodeBlock(sirArgument: SirArgumentDefinition): CodeBlock {
  return buildCodeBlock {
    when (sirArgument) {
      is SirInputValueDefinition -> {
        val getArgument = if (sirArgument.defaultValue == null && sirArgument.type !is SirNonNullType) {
          // No default value and nullable => Optional
          "getArgument"
        } else {
          "getRequiredArgument"
        }
        add(
          "%L·=·it.$getArgument(%S)",
          sirArgument.kotlinName,
          sirArgument.name,
        )
      }

      is SirExecutionContextArgumentDefinition -> {
        add("%L·=·it.executionContext", sirArgument.name)
      }
    }
  }
}