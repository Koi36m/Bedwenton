#!/usr/bin/env python3
"""M7 targeted mutation runner. For each mutant in mutants.py: copy ../reference to a temporary directory, apply the
single textual replacement (it must occur exactly once), run the verification build with -Pm7RefDir=<copy>, and record
the failing tests. A mutant is CAUGHT when at least one test fails (or compilation fails). The unmutated reference is
never modified. Usage: python3 run_mutants.py [output file] [mutant ids...]"""
import glob
import os
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, '..', '..', '..'))
REF = os.path.join(HERE, '..', 'reference')
PKG = os.path.join('workshop', 'vfx', 'paper', 'core')

def load():
    scope = {}
    with open(os.path.join(HERE, 'mutants.py')) as f:
        exec(f.read(), scope)
    return scope['M']

def failing_tests():
    names = []
    for path in glob.glob(os.path.join(HERE, 'build', 'test-results', 'test', '*.xml')):
        suite = ET.parse(path).getroot()
        for case in suite.findall('testcase'):
            if case.find('failure') is not None or case.find('error') is not None:
                names.append(suite.get('name').split('.')[-1] + '.' + case.get('name'))
    return sorted(names)

def run(mutant):
    mid, filename, old, new, description = mutant
    tmp = tempfile.mkdtemp(prefix='m7-mutant-' + mid + '-')
    try:
        shutil.copytree(REF, os.path.join(tmp, 'ref'))
        target = os.path.join(tmp, 'ref', PKG, filename)
        with open(target) as f:
            text = f.read()
        if text.count(old) != 1:
            return mid, description, 'PATTERN-ERROR', ['pattern occurs %d times' % text.count(old)]
        with open(target, 'w') as f:
            f.write(text.replace(old, new))
        shutil.rmtree(os.path.join(HERE, 'build', 'test-results'), ignore_errors=True)
        proc = subprocess.run([os.path.join(ROOT, 'gradlew'), '-p', HERE, '--no-daemon', '-q',
                               '-Porg.gradle.java.installations.auto-download=false',
                               '-Pm7RefDir=' + os.path.join(tmp, 'ref'), 'test'],
                              capture_output=True, text=True)
        out = proc.stdout + proc.stderr
        if 'error:' in out and not glob.glob(os.path.join(HERE, 'build', 'test-results', 'test', '*.xml')):
            return mid, description, 'CAUGHT (compile error)', []
        fails = failing_tests()
        return mid, description, ('CAUGHT' if fails else 'SURVIVED'), fails
    finally:
        shutil.rmtree(tmp, ignore_errors=True)

def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, 'build', 'm7-mutation-run.txt')
    wanted = set(sys.argv[2:])
    mutants = [m for m in load() if not wanted or m[0] in wanted]
    os.makedirs(os.path.dirname(out), exist_ok=True)
    lines = []
    caught = 0
    for m in mutants:
        mid, description, verdict, fails = run(m)
        caught += verdict.startswith('CAUGHT')
        line = '%s (%s): %s, %d failing test(s)' % (mid, description, verdict, len(fails))
        print(line, flush=True)
        lines.append(line)
        lines.extend('    ' + f for f in fails)
    summary = 'MUTANTS: %d caught of %d' % (caught, len(mutants))
    print(summary)
    lines.append(summary)
    with open(out, 'w') as f:
        f.write('\n'.join(lines) + '\n')
    return 0 if caught == len(mutants) else 1

if __name__ == '__main__':
    sys.exit(main())
