"""Report unchanged regressions separately from production K0 tests."""
import sys,json
import xml.etree.ElementTree as ET
from pathlib import Path
root=Path(sys.argv[1]);old=json.loads((root/'docs/m8/k0/M7-REGRESSION-CLASSES.json').read_text())
def totals(folder,expected):
 reports=sorted(folder.glob('TEST-*.xml'));counts={k:0 for k in ['tests','failures','errors','skipped']}
 assert reports,folder
 for path in reports:
  suite=ET.parse(path).getroot()
  for k in counts:counts[k]+=int(suite.attrib.get(k,0))
 assert counts==dict(tests=expected,failures=0,errors=0,skipped=0),counts
 return counts,reports
engine,files=totals(root/'build/test-results/test',415)
adapter,reports=totals(root/'paper-adapter/build/test-results/test',111)
groups={'M7':0,'K0':0};seen=set()
for path in reports:
 suite=ET.parse(path).getroot();name=suite.attrib['name'];seen.add(name)
 if name in old:groups['M7']+=int(suite.attrib['tests'])
 else:
  assert '.K0' in name,name
  groups['K0']+=int(suite.attrib['tests'])
assert set(old)<=seen
assert groups==dict(M7=61,K0=50),groups
print(json.dumps(dict(engine=engine,adapter=adapter,unchanged_regressions=476,new_k0_tests=50,total_tests=526,groups=groups,engine_xml=len(files),adapter_xml=len(reports)),indent=2))
