#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
export PYTHONPATH="$ROOT/desktop/src${PYTHONPATH:+:$PYTHONPATH}"
exec python3 -m unittest discover -s "$ROOT/desktop/tests" -v
