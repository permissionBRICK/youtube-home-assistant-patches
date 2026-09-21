#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
test_dir=$(mktemp -d)
trap 'rm -r "$test_dir"' EXIT
javac --release 17 -d "$test_dir" extensions/src/main/java/net/permissionbrick/ha/{Playback,Webhook}.java tests/WebhookTest.java
java -cp "$test_dir" net.permissionbrick.ha.WebhookTest
