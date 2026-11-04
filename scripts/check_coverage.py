#!/usr/bin/env python3
"""Check coverage from app unit tests plus the separate client/server integration tests."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
report = root / 'integration-tests/target/site/jacoco-aggregate/jacoco.xml'
if not report.is_file():
    raise SystemExit('Missing aggregate report; run mvn clean verify at repository root')
covered = missed = 0
for group in ET.parse(report).getroot().findall('group'):
    counter = next(c for c in group.findall('counter') if c.get('type') == 'LINE')
    hit, miss = int(counter.get('covered')), int(counter.get('missed'))
    print(f'{group.get("name")}: {hit}/{hit + miss} lines ({hit / (hit + miss):.1%})')
    covered += hit
    missed += miss
if covered + missed == 0:
    raise SystemExit('Empty coverage report')
ratio = covered / (covered + missed)
print(f'TOTAL: {covered}/{covered + missed} lines ({ratio:.1%}); required >= 60%')
if ratio < 0.60:
    raise SystemExit(1)
