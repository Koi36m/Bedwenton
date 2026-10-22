import sys,json,xml.etree.ElementTree as ET
from pathlib import Path
r=Path(sys.argv[1]);m7=set(json.loads((r/'docs/m8/k0/M7-REGRESSION-CLASSES.json').read_text()))
counts={k:0 for k in ['tests','failures','errors','skipped']};groups={'M1-M6':0,'M7':0,'K0':0,'K1':0,'K1.1':0,'K1.2':0,'K1.2.2':0,'Reference':0};files=[];seen=set()
for folder in [r/'build/test-results/test',r/'paper-adapter/build/test-results/test']:
 xml=sorted(folder.glob('TEST-*.xml'));assert xml,folder;files+=xml
 for p in xml:
  suite=ET.parse(p).getroot();name=suite.attrib['name']
  for c in suite.findall('testcase'):seen.add(c.attrib['classname']+':'+c.attrib['name'])
  for k in counts:counts[k]+=int(suite.attrib.get(k,0))
  group='M1-M6' if folder==r/'build/test-results/test' else 'Reference' if '.reference.' in name else 'M7' if name in m7 else 'K0' if '.K0' in name else 'K1.1' if '.K11' in name else 'K1.2.2' if '.K122' in name else 'K1.2' if '.K12' in name else 'K1'
  if group=='K1':assert '.K1' in name,name
  groups[group]+=int(suite.attrib['tests'])
assert groups=={'M1-M6':415,'M7':61,'K0':50,'K1':57,'K1.1':6,'K1.2':14,'K1.2.2':8,'Reference':20},groups
assert counts==dict(tests=631,failures=0,errors=0,skipped=0),counts
baseline=json.loads((r/'docs/reference-vfx/BASELINE-TEST-IDENTIFIERS.json').read_text())
assert set(baseline)<=seen,sorted(set(baseline)-seen)
print(json.dumps(dict(counts=counts,groups=groups,xml_reports=len(files)),indent=2))
