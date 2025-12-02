package com.apollographql.execution.gradle.task

import gratatouille.tasks.GInputFile
import gratatouille.tasks.GManuallyWired
import gratatouille.tasks.GOutputFile
import gratatouille.tasks.GTask

internal const val apolloDumpSchema = "apolloDumpSchema"

@GTask
fun apolloDumpSchema(
  from: GInputFile,
  @GManuallyWired
  to: GOutputFile
) {
  from.copyTo(to, overwrite = true)
}