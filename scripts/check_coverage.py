#!/usr/bin/env python3
"""Sum JaCoCo line coverage across the three Maven modules."""
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
covered = missed = 0
for module in ('common', 'server', 'client'):
    report = root / module / 'target/site/jacoco/jacoco.xml'
    if not report.is_file():
        raise SystemExit(f'Missing {module} JaCoCo report; run mvn clean verify at repository root')
    counter = ET.parse(report).getroot().find("counter[@type='LINE']")
    if counter is None:
        raise SystemExit(f'Missing line counter in {report}')
    hit, miss = int(counter.get('covered')), int(counter.get('missed'))
    print(f'{module}: {hit}/{hit + miss} lines ({hit / (hit + miss):.1%})')
    covered += hit
    missed += miss
if covered + missed == 0:
    raise SystemExit('Empty coverage report')
ratio = covered / (covered + missed)
print(f'TOTAL (module source, counted once): {covered}/{covered + missed} lines ({ratio:.1%}); required >= 60%')
if ratio < 0.60:
    raise SystemExit(1)
