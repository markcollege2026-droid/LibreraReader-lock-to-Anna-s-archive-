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
lines = Path(sys.argv[1]).read_text(errors='replace').splitlines()
# A stack trace's final frames hide Gradle's actual diagnostic. Prefer its summary.
start = next((i for i, line in enumerate(lines) if '* What went wrong:' in line), None)
if start is not None:
    end = next((i for i in range(start + 1, len(lines)) if lines[i].startswith('* Try:')), start + 70)
    useful = lines[start:min(end, start + 100)]
else:
    useful = lines[-40:]
compiler_errors = [line for line in lines if 'error:' in line or 'Caused by:' in line]
message = '\n'.join(useful + compiler_errors[:20])[:16000]
message = message.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
print(f'::error::{message}')
PY
fi
exit "$status"
