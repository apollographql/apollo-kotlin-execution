#!/usr/bin/env kotlin

@file:Repository("https://repo.maven.apache.org/maven2/")
@file:Repository("https://storage.googleapis.com/gradleup/m2")
@file:Repository("https://jitpack.io")
@file:DependsOn("com.gradleup.librarian:librarian-cli:0.2.1")

import com.gradleup.librarian.cli.updateRepo
import java.util.regex.Pattern

updateRepo(args) {
  file("Writerside/v.list") {
    replaceRegex(Regex("${Pattern.quote("<var name=\"latest_version\" instance=\"doc\" value=\"")}[^\"]*\"")) {
      "<var name=\"latest_version\" instance=\"doc\" value=\"$version\""
    }
  }
}
