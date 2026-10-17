"""Require fresh successful JUnit XML, with exact milestone totals."""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
root=Path(sys.argv[1]);expected=int(sys.argv[2])
reports=sorted(root.glob('TEST-*.xml')); totals=dict(tests=0,failures=0,errors=0,skipped=0)
if not reports:raise SystemExit('No JUnit XML reports')
for p in reports:
    suite=ET.parse(p).getroot()
    for k in totals:totals[k]+=int(suite.attrib.get(k,0))
print('JUnit XML reports:',len(reports));print('Totals:',totals)
if totals!=dict(tests=expected,failures=0,errors=0,skipped=0):raise SystemExit('Unexpected test results')
