package com.apollographql.execution.processor.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy

internal object KotlinSymbols {
  val apolloExecutionPackageName = "com.apollographql.execution"
  val apolloAstPackageName = "com.apollographql.apollo3.ast"
  val apolloApiPackageName = "com.apollographql.apollo3.api"

  val ExecutableSchemaBuilder = ClassName(apolloExecutionPackageName, "ExecutableSchema", "Builder")
  val Resolver = ClassName(apolloExecutionPackageName, "Resolver")
  val ResolveInfo = ClassName(apolloExecutionPackageName, "ResolveInfo")
  val Coercing = ClassName(apolloExecutionPackageName, "Coercing")
  val AstDocument = ClassName(apolloAstPackageName, "GQLDocument")
  val AstScalarTypeDefinition = ClassName(apolloAstPackageName, "GQLScalarTypeDefinition")
  val AstEnumTypeDefinition = ClassName(apolloAstPackageName, "GQLEnumTypeDefinition")
  val AstInterfaceTypeDefinition = ClassName(apolloAstPackageName, "GQLInterfaceTypeDefinition")
  val AstUnionTypeDefinition = ClassName(apolloAstPackageName, "GQLUnionTypeDefinition")
  val AstObjectTypeDefinition = ClassName(apolloAstPackageName, "GQLObjectTypeDefinition")
  val AstInputObjectTypeDefinition = ClassName(apolloAstPackageName, "GQLInputObjectTypeDefinition")
  val AstEnumValueDefinition = ClassName(apolloAstPackageName, "GQLEnumValueDefinition")
  val AstFieldDefinition = ClassName(apolloAstPackageName, "GQLFieldDefinition")
  val AstNonNullType = ClassName(apolloAstPackageName, "GQLNonNullType")
  val AstListType = ClassName(apolloAstPackageName, "GQLListType")
  val AstNamedType = ClassName(apolloAstPackageName, "GQLNamedType")
  val AstInputValueDefinition = ClassName(apolloAstPackageName, "GQLInputValueDefinition")
  val AstSchema = ClassName(apolloAstPackageName, "Schema")
  val AstValue = ClassName(apolloAstPackageName, "GQLValue")
  val AstEnumValue = ClassName(apolloAstPackageName, "GQLEnumValue")
  val AstStringValue = ClassName(apolloAstPackageName, "GQLStringValue")
  val AstSchemaDefinition = ClassName(apolloAstPackageName, "GQLSchemaDefinition")
  val AstOperationTypeDefinition = ClassName(apolloAstPackageName, "GQLOperationTypeDefinition")
  val AstParseAsGQLValue = MemberName(apolloAstPackageName, "parseAsGQLValue")
  val AstBuiltinDefinitions = MemberName(apolloAstPackageName, "builtinDefinitions")
  val AstDirective = ClassName(apolloAstPackageName, "GQLDirective")
  val AstArgument = ClassName(apolloAstPackageName, "GQLArgument")
  val Boolean = ClassName("kotlin", "Boolean")
  val Int = ClassName("kotlin", "Int")
  val String = ClassName("kotlin", "String")
  val Double = ClassName("kotlin", "Double")
  val Any = ClassName("kotlin", "Any")
  val Deprecated = ClassName("kotlin", "Deprecated")
  val Unit = ClassName("kotlin", "Unit")

  val List = ClassName("kotlin.collections", "List")
  val Map = ClassName("kotlin.collections", "Map")
  val Array = ClassName("kotlin", "Array")
  val Set = ClassName("kotlin.collections", "Set")

  val MapOfStringToNullableAny = Map.parameterizedBy(String, Any.copy(nullable = true))

  val Suppress = ClassName("kotlin", "Suppress")
  val OptIn = ClassName("kotlin", "OptIn")
  val JvmOverloads = ClassName("kotlin.jvm", "JvmOverloads")
  val Absent = ClassName(apolloApiPackageName, "Optional", "Absent")
  val Present = ClassName(apolloApiPackageName, "Optional", "Present")
}