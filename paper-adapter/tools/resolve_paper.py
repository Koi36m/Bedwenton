"""Read official repository metadata, record the exact API pin and artifact hash.

This is provenance collection, not permission to use a floating build dependency.
"""
import hashlib
import json
import sys
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

out = Path(sys.argv[1]); out.mkdir(parents=True, exist_ok=True)
base = 'io/papermc/paper/paper-api/'

def fetch(path):
    urls = ['https://repo.papermc.io/repository/maven-public/' + path,
            'https://artifactory.papermc.io/ui/api/v1/download?repoKey=universe&path=' + path]
    errors = []
    for url in urls:
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'Bedwenton-M7-provenance'}), timeout=60) as response:
                data = response.read()
                if data.lstrip().lower().startswith((b'<!doctype html', b'<html')):
                    raise ValueError('Repository returned its HTML UI instead of an artifact')
                with (out/'requests.txt').open('a') as log: log.write(url + '\n')
                return data
        except Exception as e:
            errors.append(f'{url}: {type(e).__name__}: {e}')
    raise RuntimeError('\n'.join(errors))

metadata = fetch(base+'maven-metadata.xml'); (out/'maven-metadata.xml').write_bytes(metadata)
versions = [e.text for e in ET.fromstring(metadata).findall('./versioning/versions/version')]
matching = [v for v in versions if v == '26.3' or v.startswith(('26.3-', '26.3.'))]
if not matching: raise RuntimeError('Official metadata lists no Paper 26.3 API version')
version = matching[-1]; directory = version
if version.endswith('-SNAPSHOT'):
    snapshot = fetch(base+version+'/maven-metadata.xml'); (out/'snapshot-metadata.xml').write_bytes(snapshot)
    values = [e.findtext('value') for e in ET.fromstring(snapshot).findall('./versioning/snapshotVersions/snapshotVersion')
              if e.findtext('extension') == 'jar' and not e.findtext('classifier')]
    if not values: raise RuntimeError('No unique unclassified snapshot JAR in metadata')
    version = values[-1]
jar = fetch(base+directory+'/paper-api-'+version+'.jar')
if jar[:2] != b'PK': raise RuntimeError('Not a JAR')
(out/('paper-api-'+version+'.jar')).write_bytes(jar)
(out/'paper-api.pom').write_bytes(fetch(base+directory+'/paper-api-'+version+'.pom'))
result = dict(paperApiVersion=version, repositoryDirectory=directory, jarSha256=hashlib.sha256(jar).hexdigest(), metadataSha256=hashlib.sha256(metadata).hexdigest())
(out/'pin.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
