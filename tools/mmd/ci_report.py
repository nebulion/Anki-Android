#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Turn the fork CI's build, lint and unit-test results into GitHub annotations.

Job logs need an authenticated API call, but a job's annotations are public, so failures are
reported as annotations. GitHub keeps at most 10 annotations of each level per step, so long
reports are packed into a few long messages.

Usage (from the repository root, after the Gradle steps of .github/workflows/mmd_ci.yml):
    ci_report.py build | lint | tests
"""

import pathlib
import re
import sys
import xml.etree.ElementTree as ET
from collections import Counter

MAX_CHUNK = 3500
MAX_ANNOTATIONS = 10
ROOT = str(pathlib.Path.cwd()) + "/"


def emit(level: str, title: str, lines: list[str]) -> None:
    lines = [line.replace(ROOT, "").rstrip() for line in lines]
    if not lines:
        print(f"::{level} title={title}::nothing to report")
        return
    chunks, current = [], ""
    for line in lines:
        line = line[:MAX_CHUNK]
        if current and len(current) + len(line) + 1 > MAX_CHUNK:
            chunks.append(current)
            current = ""
        current += line + "\n"
    if current:
        chunks.append(current)
    if len(chunks) > MAX_ANNOTATIONS:
        dropped = len(chunks) - (MAX_ANNOTATIONS - 1)
        chunks = chunks[: MAX_ANNOTATIONS - 1] + [f"{dropped} more chunks not shown: see the mmd-reports artifact\n"]
    for index, chunk in enumerate(chunks, 1):
        message = chunk.replace("%", "%25").replace("\r", "").replace("\n", "%0A")
        print(f"::{level} title={title} {index} of {len(chunks)}::{message}")


def report_build() -> None:
    log = pathlib.Path("build.log")
    if not log.exists():
        emit("error", "build", ["build.log is missing"])
        return
    lines = log.read_text(encoding="utf-8", errors="replace").splitlines()
    wanted = re.compile(r"^e: |error:|ERROR:|FAILED|BUILD (SUCCESSFUL|FAILED)")
    picked = []
    in_what_went_wrong = False
    for line in lines:
        if line.startswith("* What went wrong:"):
            in_what_went_wrong = True
        elif in_what_went_wrong and line.startswith("* "):
            in_what_went_wrong = False
        if in_what_went_wrong or wanted.search(line):
            picked.append(line)
    emit("error", "build", picked)


def report_lint() -> None:
    report = pathlib.Path("AnkiDroid/build/reports/lint-results-playDebug.txt")
    if not report.exists():
        emit("warning", "lint", ["lint-results-playDebug.txt is missing: lint did not run (see the build report)"])
        return
    lines = report.read_text(encoding="utf-8", errors="replace").splitlines()
    issue = re.compile(r":\d+: (Error|Warning|Information): .*\[(\w+)\]$|: (Error|Warning|Information): .*\[(\w+)\]$")
    issues = [line for line in lines if issue.search(line)]
    counts = Counter(re.search(r"\[(\w+)\]$", line).group(1) for line in issues)
    summary = [lines[-1] if lines else "empty report"] + [f"{count} {issue_id}" for issue_id, count in counts.most_common()]
    emit("warning", "lint", summary + [""] + issues)


def report_tests() -> None:
    results = pathlib.Path("AnkiDroid/build/test-results/testPlayDebugUnitTest")
    files = sorted(results.glob("*.xml")) if results.exists() else []
    if not files:
        emit("notice", "tests", ["no unit test results: the tests did not run (see the build report)"])
        return
    total = failed = skipped = 0
    details = []
    for file in files:
        suite = ET.parse(file).getroot()
        total += int(suite.get("tests", 0))
        skipped += int(suite.get("skipped", 0))
        for case in suite.iter("testcase"):
            for kind in ("failure", "error"):
                problem = case.find(kind)
                if problem is None:
                    continue
                failed += 1
                message = (problem.get("message") or "").strip().splitlines()
                details.append(f"{case.get('classname', '').split('.')[-1]} > {case.get('name')}: {message[0][:300] if message else ''}")
                stack = [line.strip() for line in (problem.text or "").splitlines() if "com.ichi2" in line][:6]
                details += ["    " + line for line in stack]
    emit("notice", "tests", [f"unit tests: {total} run, {failed} failed, {skipped} skipped", ""] + details)


if __name__ == "__main__":
    reports = {"build": report_build, "lint": report_lint, "tests": report_tests}
    if len(sys.argv) != 2 or sys.argv[1] not in reports:
        sys.exit(__doc__)
    reports[sys.argv[1]]()
