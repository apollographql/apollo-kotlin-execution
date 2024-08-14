package com.apollographql.execution.processor.sir

import com.apollographql.apollo.ast.GQLDirectiveLocation
import com.apollographql.apollo.ast.GQLValue
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
  val directives: List<SirDirective>,
  val targetName: String,
  val isFunction: Boolean,
  val type: SirType,
  val arguments: List<SirArgumentDefinition>
)

internal sealed interface SirArgumentDefinition

internal class SirExecutionContextArgumentDefinition(val name: String) : SirArgumentDefinition

internal class SirInputValueDefinition(
    val name: String,
    val description: String?,
    val directives: List<SirDirective>,
    val kotlinName: String,
    val type: SirType,
    /**
   * The defaultValue, encoded in GraphQL
   */
  val defaultValue: String?
) : SirArgumentDefinition

internal sealed interface SirType

internal class SirNonNullType(val type: SirType) : SirType
internal class SirListType(val type: SirType) : SirType
internal class SirNamedType(val name: String) : SirType

/**
 * There was an error resolving that type
 */
internal data object SirErrorType : SirType

internal sealed interface SirDefinition

internal sealed interface SirTypeDefinition : SirDefinition {
  /**
   * The GraphQL name
   */
  val name: String
  val qualifiedName: String
  val directives: List<SirDirective>
}

internal class SirScalarDefinition(
  override val name: String,
  override val qualifiedName: String,
  val description: String?,
  val coercing: SirCoercing,
  override val directives: List<SirDirective>,
) : SirTypeDefinition

internal class SirDirectiveDefinition(
    val name: String,
    val description: String?,
    val repeatable: Boolean,
    val argumentDefinitions: List<SirInputValueDefinition>,
    val locations: List<GQLDirectiveLocation>
) : SirDefinition

internal class SirDirective(
  val name: String,
  val arguments: List<SirArgument>
)

internal class SirArgument(
  val name: String,
  val value: GQLValue
)

internal class SirCoercing(
  val className: SirClassName,
  val instantiation: Instantiation,
)

internal enum class Instantiation {
  OBJECT,
  NO_ARG_CONSTRUCTOR,
  UNKNOWN
}

internal class SirObjectDefinition(
  override val name: String,
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
  override val directives: List<SirDirective>,
) : SirTypeDefinition

internal class SirInterfaceDefinition(
  override val name: String,
  val description: String?,
  override val qualifiedName: String,
  val interfaces: List<String>,
  val fields: List<SirFieldDefinition>,
  override val directives: List<SirDirective>,
) : SirTypeDefinition

internal class SirUnionDefinition(
  override val name: String,
  val description: String?,
  override val qualifiedName: String,
  val memberTypes: List<String>,
  override val directives: List<SirDirective>,
) : SirTypeDefinition

internal class SirEnumDefinition(
  override val name: String,
  val description: String?,
  override val qualifiedName: String,
  val targetClassName: SirClassName,
  val values: List<SirEnumValueDefinition>,
  override val directives: List<SirDirective>,
) : SirTypeDefinition

internal class SirInputObjectDefinition(
  override val name: String,
  val description: String?,
  override val qualifiedName: String,
  val targetClassName: SirClassName,
  val inputFields: List<SirInputValueDefinition>,
  override val directives: List<SirDirective>,
) : SirTypeDefinition

internal class SirEnumValueDefinition(
  val name: String,
  val description: String?,
  val directives: List<SirDirective>,
  val className: SirClassName
)