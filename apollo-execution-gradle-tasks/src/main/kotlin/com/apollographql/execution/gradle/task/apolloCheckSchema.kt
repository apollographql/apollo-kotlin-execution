package com.apollographql.execution.gradle.task

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import gratatouille.tasks.GInputFile
import gratatouille.tasks.GTask
import java.io.File

internal const val apolloCheckSchema = "apolloCheckSchema"

@GTask
fun apolloCheckSchema(
  existing: GInputFile,
  new: GInputFile
) {
  if (!existing.exists()) {
    error("No GraphQL schema found at '${existing.path}'. Run '$apolloDumpSchema' to generate the schema.")
  }
  val diff = compareFiles(existing, new)
  if (diff.isNotEmpty()) {
    error("Apollo schema check failed.\n$diff\n\nRun '$apolloDumpSchema' to overwrite the schema.")
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