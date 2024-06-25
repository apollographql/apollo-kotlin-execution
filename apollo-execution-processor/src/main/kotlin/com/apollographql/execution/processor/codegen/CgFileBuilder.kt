package com.apollographql.execution.processor.codegen

import com.squareup.kotlinpoet.FileSpec

internal interface CgFileBuilder {
  fun prepare()
  fun build(): FileSpec
}