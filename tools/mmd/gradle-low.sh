#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
#
# A gentle local Gradle run, for quick compile checks while editing:
#   tools/mmd/gradle-low.sh :AnkiDroid:compilePlayDebugKotlin
#
# - one JVM: Kotlin compiles inside the Gradle daemon instead of a second daemon
# - 4 GB heap (overrides the larger machine-wide ~/.gradle/gradle.properties), 2 workers
# - every Java process runs at BelowNormal priority
# - the daemon exits after 10 idle minutes, giving the memory back
#
# Full builds, lint and unit tests run in the fork CI instead (.github/workflows/mmd_ci.yml).
set -u
cd "$(dirname "$0")/../.."

./gradlew \
  -Dorg.gradle.jvmargs="-Xmx4g -XX:+UseParallelGC -Dfile.encoding=UTF-8" \
  -Dorg.gradle.daemon.idletimeout=600000 \
  -Pkotlin.compiler.execution.strategy=in-process \
  --max-workers=2 \
  "$@" &
pid=$!

while kill -0 "$pid" 2>/dev/null; do
  powershell -NoProfile -Command "Get-Process java,javaw -ErrorAction SilentlyContinue | ForEach-Object { try { \$_.PriorityClass = 'BelowNormal' } catch {} }" >/dev/null 2>&1
  sleep 15
done
wait "$pid"
