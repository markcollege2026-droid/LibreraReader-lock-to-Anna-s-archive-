#!/usr/bin/env python3
"""Regression tripwire for new, unguarded network entry points (not a security proof)."""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[1]
failures = []
for source in (root / 'app/src/main/java').rglob('*.java'):
    text = source.read_text()
    relative = source.relative_to(root).as_posix()
    # Ignore comments so historical snippets do not look like live clients.
    text = re.sub(r'/\*.*?\*/|//[^\n]*', '', text, flags=re.S)
    if 'com/foobnix/network/' not in relative:
        for pattern in (r'new\s+(?:android\.webkit\.)?WebView\s*\(',
                        r'new\s+(?:okhttp3\.)?OkHttpClient(?:\.Builder)?\s*\(',
                        r'new\s+(?:android\.webkit\.)?WebViewClient\s*\(',
                        r'\.openConnection\s*\(', r'Jsoup\.connect\s*\(',
                        r'new\s+(?:java\.net\.)?(?:Socket|DatagramSocket)\s*\('):
            if re.search(pattern, text):
                failures.append(f'{relative}: forbidden entry point {pattern}')
for layout in (root / 'app/src/main/res').rglob('*.xml'):
    if re.search(r'<(?:android\.webkit\.)?WebView\b', layout.read_text()):
        failures.append(f'{layout.relative_to(root)}: unguarded XML WebView')
build = (root / 'app/build.gradle').read_text()
for pattern in (r"(?:Implementation|implementation)\s+(?:dep_free|dep_pro)",
                r"project\(['\"]:libDep(?:Free|Pro)['\"]\)",
                r'alias\(libs\.plugins\.googleServices\)'):
    if re.search(pattern, build):
        failures.append(f'app/build.gradle: independent SDK network stack {pattern}')
if failures:
    sys.exit('\n'.join(failures))
print('Network entry-point audit passed')
