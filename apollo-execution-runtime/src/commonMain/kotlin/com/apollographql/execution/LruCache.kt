package com.apollographql.execution

internal typealias Weigher<Key, Value> = (Key, Value?) -> Int

