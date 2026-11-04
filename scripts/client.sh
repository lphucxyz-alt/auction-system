#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
mvn "${AUCTION_MAVEN_ARGS[@]}" -f client/pom.xml -Dauction.host="${AUCTION_HOST:-127.0.0.1}" -Dauction.port="${AUCTION_PORT:-5000}" javafx:run
