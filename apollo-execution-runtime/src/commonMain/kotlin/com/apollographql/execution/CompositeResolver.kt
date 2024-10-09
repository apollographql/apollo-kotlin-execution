package com.apollographql.execution

@DslMarker
annotation class CompositeResolverMarker

@CompositeResolverMarker
class CompositeResolverBuilder {
  private val types = mutableMapOf<String, TypeBuilder>()
  private var default: Resolver? = null

  fun type(name: String, builder: TypeBuilder.() -> Unit) {
    types.put(name, TypeBuilder().apply(builder))
  }

  fun default(resolver: Resolver) {
    this.default = resolver
  }

  fun build(): Resolver {
    return Resolver {
      val type = types.get(it.parentType)
      if (type != null) {
        val resolver = type.fields.get(it.fieldName)
        if (resolver != null) {
          return@Resolver resolver.resolve(it)
        }
      }
      if (default != null) {
        return@Resolver default!!.resolve(it)
      }
      error("CompositeResolver cannot resolve '${it.parentType}.${it.fieldName}'")
    }
  }

  @CompositeResolverMarker
  class TypeBuilder {
    internal val fields = mutableMapOf<String, Resolver>()

    fun field(name: String, resolver: Resolver) {
      fields.put(name, resolver)
    }
  }
}

fun ExecutableSchema.Builder.compositeResolver(builder: CompositeResolverBuilder.() -> Unit) = apply {
  CompositeResolverBuilder().apply(builder).builder()
}