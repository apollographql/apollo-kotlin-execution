package com.apollographql.execution.processor.codegen

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.withIndent

internal fun CodeBlock.Builder.indent(block: CodeBlock.Builder.() -> Unit) = withIndent(block)

internal fun buildCode(block: CodeBlock.Builder.() -> Unit) = buildCodeBlock(block)