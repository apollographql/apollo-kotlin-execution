package com.apollographql.apollo3.ksp.codegen

import com.squareup.kotlinpoet.FileSpec

internal interface CgFileBuilder {
  fun prepare()
  fun build(): FileSpec
}