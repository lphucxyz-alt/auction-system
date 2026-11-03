#!/usr/bin/env python3
"""Check actual JaCoCo line coverage across every application module, including GUI."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
covered = missed = 0
for module in ('common', 'server', 'client'):
    report = root / module / 'target/site/jacoco/jacoco.xml'
    if not report.is_file():
        raise SystemExit(f'Missing {report}; run mvn clean verify first')
    counter = next(c for c in ET.parse(report).getroot().findall('counter') if c.get('type') == 'LINE')
    hit, miss = int(counter.get('covered')), int(counter.get('missed'))
    if hit + miss == 0:
        raise SystemExit(f'Empty coverage report: {module}')
    print(f'{module}: {hit}/{hit + miss} lines ({hit / (hit + miss):.1%})')
    covered += hit
    missed += miss
ratio = covered / (covered + missed)
print(f'TOTAL: {covered}/{covered + missed} lines ({ratio:.1%}); required >= 60%')
if ratio < 0.60:
    raise SystemExit(1)
