#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
python3 scripts/check_architecture.py
project_root="$PWD"
build_root="$(mktemp -d)"
trap 'rm -rf "$build_root"' EXIT
for app in server client; do
  mkdir -p "$build_root/$app"
  cp "$project_root/$app/pom.xml" "$build_root/$app/pom.xml"
  cp -R "$project_root/$app/src" "$build_root/$app/src"
  # Only this application's source and POM are copied. No root POM, sibling or target.
  mvn "${AUCTION_MAVEN_ARGS[@]}" -B -ntp -f "$build_root/$app/pom.xml" clean verify
  rm -rf "$build_root/$app"
done
