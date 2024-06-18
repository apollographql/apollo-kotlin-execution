package com.apollographql.execution.gradle.internal

import com.apollographql.execution.gradle.apolloDumpSchema
import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class ApolloCheckSchema: DefaultTask() {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val existing: RegularFileProperty

  @get:InputFile
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val new: RegularFileProperty

  @TaskAction
  fun taskAction() {
    val existing = existing.get().asFile
    if (!existing.exists()) {
      error("No GraphQL schema found at '${existing.path}'. Run '$apolloDumpSchema' to generate the schema.")
    }
    val diff = compareFiles(existing, new.asFile.get())
    if (diff.isNotEmpty()) {
      error("Apollo schema check failed.\n$diff\n\nRun '$apolloDumpSchema' to overwrite the schema.")
    }
  }
}

/**
 * From BCV
 * https://github.com/Kotlin/binary-compatibility-validator/blob/ad1bea6630508abc0ec2bb0fd516a90aa6786258/src/main/kotlin/KotlinApiCompareTask.kt#L92
 */
private fun compareFiles(originalFile: File, revisedFile: File): String {
  // We don't compare full text because newlines on Windows & Linux/macOS are different
  val originalLines = originalFile.readText().lines()
  val revisedLines = revisedFile.readText().lines()
  if (originalLines == revisedLines)
    return ""

  val patch = DiffUtils.diff(originalLines, revisedLines)
  val diff = UnifiedDiffUtils.generateUnifiedDiff(originalFile.toString(), revisedFile.toString(), originalLines, patch, 3)
  return diff.joinToString("\n")
}