#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
python3 scripts/check_architecture.py
mvn "${AUCTION_MAVEN_ARGS[@]}" -B -ntp -pl server -am clean verify
mvn "${AUCTION_MAVEN_ARGS[@]}" -B -ntp -pl client -am clean verify
