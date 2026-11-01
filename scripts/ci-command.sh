#!/usr/bin/env bash
# Preserve a failing command's status and surface its final diagnostics as a check annotation.
# Useful when Actions' separate log storage is inaccessible to an API client.
set -uo pipefail
log=$(mktemp)
trap 'rm -f "$log"' EXIT
"$@" 2>&1 | tee "$log"
status=${PIPESTATUS[0]}
if [[ "$status" -ne 0 ]]; then
  python3 - "$log" <<'PY'
from pathlib import Path
import sys
message = '\n'.join(Path(sys.argv[1]).read_text(errors='replace').splitlines()[-30:])
message = message.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
print(f'::error::{message}')
PY
fi
exit "$status"
