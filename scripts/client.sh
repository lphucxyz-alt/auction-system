#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
RUNNER="${1:-${AUCTION_USER:-}}"
TAB_TITLE="${RUNNER:-client}"
printf '\033]0;%s\007' "$TAB_TITLE"
mvn "${AUCTION_MAVEN_ARGS[@]}" -pl client -Dmaven.test.skip=true -Dauction.host="${AUCTION_HOST:-127.0.0.1}" -Dauction.port="${AUCTION_PORT:-5000}" -Dauction.username="$RUNNER" javafx:run
