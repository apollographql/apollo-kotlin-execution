package com.apollographql.execution.processor.codegen

import com.apollographql.execution.processor.sir.SirArgumentDefinition
import com.apollographql.execution.processor.sir.SirExecutionContextArgumentDefinition
import com.apollographql.execution.processor.sir.SirFieldDefinition
import com.apollographql.execution.processor.sir.SirInputValueDefinition
import com.apollographql.execution.processor.sir.SirNonNullType
import com.apollographql.execution.processor.sir.SirObjectDefinition
import com.apollographql.execution.processor.sir.asKotlinPoet
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.joinToCode

internal fun resolverBody(sirObjectDefinition: SirObjectDefinition, sirTargetField: SirFieldDefinition): CodeBlock {
  val singleLine = sirTargetField.arguments.size < 2
  val nl = if (singleLine) "" else "\n"
  val sep = if (singleLine) ",·" else ",\n"

  return buildCode {
    indent {
      add("it.parentObject.cast<%T>().%L", sirObjectDefinition.targetClassName.asKotlinPoet(), sirTargetField.targetName)
      if (sirTargetField.isFunction) {
        add("($nl")
        indent(!singleLine) {
          add(sirTargetField.arguments.map { argumentCodeBlock(it) }.joinToCode(sep))
        }
        add(")")
      }
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
  return buildCode {
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