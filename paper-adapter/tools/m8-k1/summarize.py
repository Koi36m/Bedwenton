import sys,json,xml.etree.ElementTree as ET
from pathlib import Path
r=Path(sys.argv[1]);m7=set(json.loads((r/'docs/m8/k0/M7-REGRESSION-CLASSES.json').read_text()))
counts={k:0 for k in ['tests','failures','errors','skipped']};groups={'M1-M6':0,'M7':0,'K0':0,'K1':0};files=[]
for folder in [r/'build/test-results/test',r/'paper-adapter/build/test-results/test']:
 xml=sorted(folder.glob('TEST-*.xml'));assert xml,folder;files+=xml
 for p in xml:
  suite=ET.parse(p).getroot();name=suite.attrib['name']
  for k in counts:counts[k]+=int(suite.attrib.get(k,0))
  group='M1-M6' if folder==r/'build/test-results/test' else 'M7' if name in m7 else 'K0' if '.K0' in name else 'K1'
  if group=='K1':assert '.K1' in name,name
  groups[group]+=int(suite.attrib['tests'])
assert groups=={'M1-M6':415,'M7':61,'K0':50,'K1':57},groups
assert counts==dict(tests=583,failures=0,errors=0,skipped=0),counts
print(json.dumps(dict(counts=counts,groups=groups,xml_reports=len(files)),indent=2))
