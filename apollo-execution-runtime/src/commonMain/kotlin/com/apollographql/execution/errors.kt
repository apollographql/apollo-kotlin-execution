package com.apollographql.execution

import com.apollographql.apollo.api.Error
import com.apollographql.apollo.api.json.BufferedSinkJsonWriter
import com.apollographql.apollo.api.json.JsonWriter
import com.apollographql.apollo.api.json.writeAny
import com.apollographql.apollo.api.json.writeArray
import com.apollographql.apollo.api.json.writeObject
import okio.BufferedSink
import okio.Sink
import okio.buffer

internal fun JsonWriter.writeError(error: Error) {
  writeObject {
    name("message")
    value(error.message)
    if (error.locations != null) {
      name("locations")
      writeArray {
        error.locations!!.forEach {
          writeObject {
            name("line")
            value(it.line)
            name("column")
            value(it.column)
          }
        }
      }
    }
    if (error.path != null) {
      name("path")
      writeArray {
        error.path!!.forEach {
          when (it) {
            is Int -> value(it)
            is String -> value(it)
            else -> error("path can only contain Int and Double (found '${it::class.simpleName}')")
          }
        }
      }
    }
    if (error.extensions != null) {
      name("extensions")
      writeObject {
        error.extensions!!.entries.forEach {
          name(it.key)
          writeAny(it.value)
        }
      }
    }
  }
}

internal fun Sink.jsonWriter(): JsonWriter = BufferedSinkJsonWriter(if (this is BufferedSink) this else this.buffer())
