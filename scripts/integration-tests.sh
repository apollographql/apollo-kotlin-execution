#!/usr/bin/env sh
set -e
set -x
./gradlew -p tests build
./gradlew -p sample-ktor build
./gradlew -p sample-http4k build