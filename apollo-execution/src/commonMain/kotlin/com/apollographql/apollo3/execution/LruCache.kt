package com.apollographql.apollo3.execution

internal typealias Weigher<Key, Value> = (Key, Value?) -> Int

