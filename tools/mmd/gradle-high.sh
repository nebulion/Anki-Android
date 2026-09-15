#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Runs ./gradlew with every Java process (Gradle daemon, Kotlin daemon, test and lint workers)
# raised to High CPU priority on Windows for as long as the build runs.
#
# Usage: tools/mmd/gradle-high.sh <gradle args...>
# Gradle itself can only lower priority (org.gradle.priority=low), hence this wrapper.
# Windows only (uses powershell.exe); elsewhere it just runs gradlew.

set -u
cd "$(dirname "$0")/../.." || exit 1

boost() {
    powershell.exe -NoProfile -Command \
        "Get-Process java,javaw -ErrorAction SilentlyContinue | Where-Object { \$_.PriorityClass -ne 'High' } | ForEach-Object { try { \$_.PriorityClass = 'High' } catch {} }" \
        >/dev/null 2>&1
}

if ! command -v powershell.exe >/dev/null 2>&1; then
    exec ./gradlew "$@"
fi

./gradlew "$@" &
gradle_pid=$!

# New worker JVMs start throughout a build, so keep raising them until it finishes.
while kill -0 "$gradle_pid" 2>/dev/null; do
    boost
    sleep 10
done

wait "$gradle_pid"
