#!/usr/bin/env python3
"""M8 R2/R3 targeted mutation runner: copy ../reference, apply one mutant (exactly one occurrence required), run the
verification build with -Pm8RefDir=<copy>, list failing tests. CAUGHT = at least one failure or a compile error."""
import glob, os, shutil, subprocess, sys, tempfile, xml.etree.ElementTree as ET
HERE = os.path.dirname(os.path.abspath(__file__)); ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..'))
REF = os.path.join(HERE, '..', 'reference')
def load():
    scope = {}; exec(open(os.path.join(HERE, 'mutants.py')).read(), scope); return scope['M']
def failing():
    out = []
    for p in glob.glob(os.path.join(HERE, 'build', 'test-results', 'test', '*.xml')):
        s = ET.parse(p).getroot()
        for c in s.findall('testcase'):
            if c.find('failure') is not None or c.find('error') is not None: out.append(s.get('name').split('.')[-1] + '.' + c.get('name'))
    return sorted(out)
def run(m):
    mid, rel, old, new, desc = m
    tmp = tempfile.mkdtemp(prefix='m8-mutant-')
    try:
        shutil.copytree(REF, os.path.join(tmp, 'ref')); f = os.path.join(tmp, 'ref', rel); t = open(f).read()
        if t.count(old) != 1: return mid, desc, 'PATTERN-ERROR', []
        open(f, 'w').write(t.replace(old, new))
        shutil.rmtree(os.path.join(HERE, 'build', 'test-results'), ignore_errors=True)
        p = subprocess.run([os.path.join(ROOT, 'gradlew'), '-p', HERE, '--no-daemon', '-q', '-Porg.gradle.java.installations.auto-download=false',
                            '-Pm8RefDir=' + os.path.join(tmp, 'ref'), 'test'], capture_output=True, text=True)
        if 'error:' in p.stdout + p.stderr and not glob.glob(os.path.join(HERE, 'build', 'test-results', 'test', '*.xml')):
            return mid, desc, 'CAUGHT (compile error)', []
        fl = failing(); return mid, desc, 'CAUGHT' if fl else 'SURVIVED', fl
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, 'build', 'm8-r3-mutants.txt')
    ms = [m for m in load() if len(sys.argv) <= 2 or m[0] in sys.argv[2:]]; lines = []; caught = 0
    for m in ms:
        mid, desc, v, fl = run(m); caught += v.startswith('CAUGHT')
        line = '%s (%s): %s, %d failing test(s)' % (mid, desc, v, len(fl)); print(line, flush=True); lines.append(line); lines += ['    ' + x for x in fl]
    s = 'MUTANTS: %d caught of %d' % (caught, len(ms)); print(s); lines.append(s)
    os.makedirs(os.path.dirname(out), exist_ok=True); open(out, 'w').write('\n'.join(lines) + '\n')
    return 0 if caught == len(ms) else 1
if __name__ == '__main__': sys.exit(main())
