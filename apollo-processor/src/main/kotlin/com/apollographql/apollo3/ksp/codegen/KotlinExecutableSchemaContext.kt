package com.apollographql.apollo3.ksp.codegen

import com.squareup.kotlinpoet.MemberName

internal class KotlinExecutableSchemaContext(val packageName: String) {
  val coercings = mutableMapOf<String, MemberName>()
}