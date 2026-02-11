package com.apollographql.execution.processor.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy

internal object KotlinSymbols {
  val executionPackageName = "com.apollographql.execution"
  val subgraphPackageName = "com.apollographql.execution.subgraph"
  val annotationPackageName = "com.apollographql.execution.annotation"

  val apolloExecutionPackageName = "com.apollographql.apollo.execution"
  val apolloAstPackageName = "com.apollographql.apollo.ast"
  val apolloApiPackageName = "com.apollographql.apollo.api"

  val ExecutableSchemaBuilder = ClassName(apolloExecutionPackageName, "ExecutableSchema", "Builder")
  val Resolver = ClassName(apolloExecutionPackageName, "Resolver")
  val ResolveInfo = ClassName(apolloExecutionPackageName, "ResolveInfo")
  val Coercing = ClassName(apolloExecutionPackageName, "Coercing")

  val GraphQLQuery = ClassName(annotationPackageName, "GraphQLQuery")
  val GraphQLMutation = ClassName(annotationPackageName, "GraphQLMutation")
  val GraphQLSubscription = ClassName(annotationPackageName, "GraphQLSubscription")

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
  val AstDirectiveDefinition = ClassName(apolloAstPackageName, "GQLDirectiveDefinition")
  val AstSchema = ClassName(apolloAstPackageName, "Schema")
  val AstValue = ClassName(apolloAstPackageName, "GQLValue")
  val AstNullValue = ClassName(apolloAstPackageName, "GQLNullValue")
  val AstBooleanValue = ClassName(apolloAstPackageName, "GQLBooleanValue")
  val AstIntValue = ClassName(apolloAstPackageName, "GQLIntValue")
  val AstFloatValue = ClassName(apolloAstPackageName, "GQLFloatValue")
  val AstStringValue = ClassName(apolloAstPackageName, "GQLStringValue")
  val AstListValue = ClassName(apolloAstPackageName, "GQLListValue")
  val AstObjectValue = ClassName(apolloAstPackageName, "GQLObjectValue")
  val AstObjectField = ClassName(apolloAstPackageName, "GQLObjectField")
  val AstEnumValue = ClassName(apolloAstPackageName, "GQLEnumValue")
  val AstSchemaDefinition = ClassName(apolloAstPackageName, "GQLSchemaDefinition")
  val AstOperationTypeDefinition = ClassName(apolloAstPackageName, "GQLOperationTypeDefinition")
  val AstParseAsGQLValue = MemberName(apolloAstPackageName, "parseAsGQLValue")
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