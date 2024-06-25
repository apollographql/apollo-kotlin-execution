package com.apollographql.execution.processor.sir

import com.squareup.kotlinpoet.ClassName

internal data class SirClassName(
    val packageName: String,
    val names: List<String>
) {
  fun asString(): String {
    return "$packageName.${names.joinToString(".")}"
  }
}

internal fun SirClassName.asKotlinPoet(): ClassName = ClassName(packageName, names)

internal class SirFieldDefinition(
    val name: String,
    val description: String?,
    val deprecationReason: String?,
    val targetName: String,
    val isFunction: Boolean,
    val type: SirType,
    val arguments: List<SirArgumentDefinition>
)

internal sealed interface SirArgumentDefinition

internal class SirExecutionContextArgumentDefinition(val name: String): SirArgumentDefinition

internal class SirGraphQLArgumentDefinition(
    val name: String,
    val description: String?,
    val deprecationReason: String?,
    val targetName: String,
    val type: SirType,
    /**
     * The defaultValue, encoded in GraphQL
     */
    val defaultValue: String?
): SirArgumentDefinition

internal sealed interface SirType

internal class SirNonNullType(val type: SirType): SirType
internal class SirListType(val type: SirType): SirType
internal class SirNamedType(val name: String): SirType
/**
 * There was an error resolving that type
 */
internal data object SirErrorType: SirType

internal sealed interface SirTypeDefinition {
  val qualifiedName: String
}

internal class SirScalarDefinition(
    /**
     * The GraphQL name
     */
    val name: String,
    override val qualifiedName: String,
    val description: String?,
    val coercing: SirCoercing,
): SirTypeDefinition

internal class SirCoercing(
    val className: SirClassName,
    val instantiation: Instantiation,
    val scalarQualifiedName: String,
)

internal enum class Instantiation {
  OBJECT,
  NO_ARG_CONSTRUCTOR,
  UNKNOWN
}

internal class SirObjectDefinition(
    /**
     * The GraphQL name
     */
    val name: String,
    val description: String?,
    override val qualifiedName: String,
    val interfaces: List<String>,
    val targetClassName: SirClassName,
    val instantiation: Instantiation,
    /**
     * If this is a root type, what root it is for
     */
    val operationType: String?,
    val fields: List<SirFieldDefinition>,
): SirTypeDefinition

internal class SirInterfaceDefinition(
    /**
     * The GraphQL name
     */
    val name: String,
    val description: String?,
    override val qualifiedName: String,
    val interfaces: List<String>,
    val fields: List<SirFieldDefinition>,
): SirTypeDefinition

internal class SirUnionDefinition(
    /**
     * The GraphQL name
     */
    val name: String,
    val description: String?,
    override val qualifiedName: String,
    val memberTypes: List<String>,
): SirTypeDefinition

internal class SirEnumDefinition(
    /**
     * The GraphQL name
     */
    val name: String,
    val description: String?,
    override val qualifiedName: String,
    val targetClassName: SirClassName,
    val values: List<SirEnumValueDefinition>,
): SirTypeDefinition

internal class SirInputObjectDefinition(
    /**
     * The GraphQL name
     */
    val name: String,
    val description: String?,
    override val qualifiedName: String,
    val targetClassName: SirClassName,
    val inputFields: List<SirInputFieldDefinition>,
): SirTypeDefinition

internal class SirInputFieldDefinition(
    val name: String,
    val description: String?,
    val deprecationReason: String?,
    val type: SirType,
    /**
     * defaultValue as a GraphQL value
     */
    val defaultValue: String?
)

internal class SirEnumValueDefinition(
    val name: String,
    val description: String?,
    val deprecationReason: String?,
    val className: SirClassName
)