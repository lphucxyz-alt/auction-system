#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
printf '\033]0;%s\007' "server"
source scripts/env.sh
mvn "${AUCTION_MAVEN_ARGS[@]}" -B -ntp -pl server -am package -DskipTests
exec java -Dauction.host="${AUCTION_HOST:-127.0.0.1}" -Dauction.port="${AUCTION_PORT:-5000}" -Dauction.data="${AUCTION_DATA:-data}" -jar server/target/server-1.0-SNAPSHOT-all.jar "$@"
