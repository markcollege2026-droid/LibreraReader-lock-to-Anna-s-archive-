#!/usr/bin/env python3
"""Expose JUnit diagnostics through the Checks API as well as downloadable HTML reports."""
from pathlib import Path
import xml.etree.ElementTree as ET

count = failures = assumptions = 0
paths = list(Path('app/build/test-results').rglob('TEST-*.xml'))
paths += list(Path('app/build/outputs/androidTest-results').rglob('TEST-*.xml'))
for path in paths:
    suite = ET.parse(path).getroot()
    count += int(suite.get('tests', 0))
    for case in suite.iter('testcase'):
        for failure in list(case.findall('failure')) + list(case.findall('error')):
            # Some connected-test XML encodes JUnit assumptions as failure elements.
            # Missing optional device fixtures are skips, not broken application assertions.
            if failure.get('type', '').endswith('AssumptionViolatedException'):
                assumptions += 1
                continue
            failures += 1
            lines = (failure.text or failure.get('message', '')).splitlines()
            if len(lines) > 70:
                lines = lines[:40] + ['...'] + lines[-30:]
            text = f"{case.get('classname')}.{case.get('name')}:\n" + '\n'.join(lines)
            text = text[:12000].replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
            print(f'::error::{text}')
print(f'JUnit XML summary: {count} tests, {failures} failures/errors, {assumptions} assumption skips')
