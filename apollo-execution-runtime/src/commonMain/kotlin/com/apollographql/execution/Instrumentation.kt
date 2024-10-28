package com.apollographql.execution

typealias InstrumentationCompletion = (InternalValue) -> Unit

/**
 * An [Instrumentation] monitors the execution algorithm
 */
interface Instrumentation {
  fun beforeResolve(resolveInfo: ResolveInfo): InstrumentationCompletion?
}