package com.apollographql.execution.gradle.internal

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class CopySchema: DefaultTask() {
  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val from: RegularFileProperty

  @get:OutputFile
  abstract val to: RegularFileProperty

  @TaskAction
  fun taskAction() {
    from.get().asFile.copyTo(to.asFile.get(), overwrite = true)
  }
}