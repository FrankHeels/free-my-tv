#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TEST_OUT="$(mktemp -d)"
trap 'rm -rf "$TEST_OUT"' EXIT
"${JAVA_HOME:+$JAVA_HOME/bin/}javac" -encoding UTF-8 -d "$TEST_OUT" app/src/io/freemytv/ContinueModel.java tests/io/freemytv/ContinueModelTest.java
"${JAVA_HOME:+$JAVA_HOME/bin/}java" -cp "$TEST_OUT" io.freemytv.ContinueModelTest
