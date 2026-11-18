#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
printf '\033]0;%s\007' "server"
export AUCTION_DATA="${AUCTION_DATA:-data/local-demo}"
printf 'Kho demo: %s\n' "$AUCTION_DATA"
printf 'Chờ dòng "Auction server ready" rồi mở các terminal client.\n'
printf 'Tài khoản cho kho mới: seller, bidder1, bidder2, admin (xem README).\n'
bash scripts/server.sh --demo
