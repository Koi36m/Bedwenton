#!/usr/bin/env python3
"""Summarize JUnit XML reports: one line per test (PASS/FAIL), per-class totals and the grand total."""
import glob
import sys
import xml.etree.ElementTree as ET

def main(results_dir):
    total = failed = 0
    files = sorted(glob.glob(results_dir + '/*.xml'))
    for path in files:
        suite = ET.parse(path).getroot()
        tests = int(suite.get('tests'))
        bad = int(suite.get('failures')) + int(suite.get('errors')) + int(suite.get('skipped'))
        total += tests
        failed += bad
        print('%s  %d tests, %d not passed' % (suite.get('name'), tests, bad))
        for case in suite.findall('testcase'):
            problem = case.find('failure')
            if problem is None:
                problem = case.find('error')
            if problem is None and case.find('skipped') is not None:
                print('    SKIP  ' + case.get('name'))
            elif problem is None:
                print('    PASS  ' + case.get('name'))
            else:
                print('    FAIL  ' + case.get('name') + '  :: ' + (problem.get('message') or '')[:300])
    print('TOTAL %d tests, %d passed, %d not passed, in %d report files' % (total, total - failed, failed, len(files)))
    return 0 if files and failed == 0 else 1

if __name__ == '__main__':
    sys.exit(main(sys.argv[1]))
