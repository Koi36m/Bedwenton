"""Compile verification-only probes against the fresh Gradle classes; preserve all output."""
import os
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parents[2]
evidence = Path(sys.argv[1]).resolve()
classes = Path(sys.argv[2]).resolve()
classes.mkdir(parents=True, exist_ok=True)
cache = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle')))
jars = sorted((cache / 'caches/modules-2/files-2.1').rglob('*.jar'))
classpath = os.pathsep.join(map(str, [root / 'build/classes/java/main', root / 'build/classes/java/test', *jars]))
sources = [root / 'verification/m6/BoundaryProbe.java', root / 'docs/m6/validation/PerformanceProbe.java']
with (evidence / 'probe-compile.txt').open('w') as log:
    subprocess.run(['javac', '--release', '25', '-Xlint:all', '-cp', classpath, '-d', str(classes), *map(str, sources)],
                   stdout=log, stderr=subprocess.STDOUT, check=True)
for name, logname in [('BoundaryProbe', 'independent-boundaries.txt'), ('PerformanceProbe', 'performance.txt')]:
    with (evidence / logname).open('w') as log:
        subprocess.run(['java', '-Xmx512m', '-cp', str(classes) + os.pathsep + classpath,
                        'workshop.vfx.delivery.' + name], stdout=log, stderr=subprocess.STDOUT, check=True)
    print((evidence / logname).read_text(), flush=True)
