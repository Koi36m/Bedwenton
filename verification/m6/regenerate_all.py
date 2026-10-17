"""Run unchanged independent generators into fresh directories; compare every output byte."""
import csv
import hashlib
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parents[2]
out = Path(sys.argv[1]).resolve()
out.mkdir(parents=True, exist_ok=False)
datasets = [
    ('m1', 'tools/generate_golden_vectors.py', 'src/test/resources/golden'),
    ('m2', 'tools/generate_m2_golden_vectors.py', 'src/test/resources/m2/golden'),
    ('m2-review', 'review/m2/generate_boundary_vectors.py', 'src/test/resources/m2/review'),
    ('m3', 'tools/generate_m3_reference_vectors.py', 'src/test/resources/m3/reference'),
    ('m4', 'tools/generate_m4_reference_vectors.py', 'src/test/resources/m4/reference'),
    ('m5', 'tools/generate_m5_reference_frames.py', 'src/test/resources/m5/reference'),
    ('m6', 'tools/generate_m6_reference_plans.py', 'src/test/resources/m6/reference'),
]
for name, generator, original in datasets:
    target = out / name
    subprocess.run([sys.executable, str(root / generator), str(target)], check=True)
    supplied = root / original
    assert sorted(p.name for p in target.iterdir()) == sorted(p.name for p in supplied.iterdir()), name
    for file in sorted(supplied.iterdir()):
        assert file.read_bytes() == (target / file.name).read_bytes(), (name, file.name)
        print(name, file.name, 'byte-identical', hashlib.sha256(file.read_bytes()).hexdigest(), flush=True)
    if name in ('m3', 'm4', 'm5'):
        counts = {}
        for file in sorted(target.glob('*.csv')):
            lines = [line for line in file.read_text().splitlines() if not line.startswith('#')]
            counts[file.name] = len(list(csv.DictReader(lines)))
        assert sum(counts.values()) == {'m3': 2759, 'm4': 3064, 'm5': 1113}[name], counts
        print(name, 'rows', counts, flush=True)
    if name == 'm6':
        counts = {}
        for file in sorted(target.glob('*.txt')):
            lines = file.read_text().splitlines()
            counts[file.stem] = [sum(s.startswith('case ') for s in lines), sum(s.startswith('expect ') for s in lines)]
        assert counts == {'bloom': [6, 57], 'cloudstep': [3, 23], 'healer': [8, 8], 'trident': [6, 6],
                          'astral': [9, 9], 'stress': [13, 55]}, counts
        print('M6 cases/expectations:', counts, 'total 45 / 158', flush=True)
        again = out / 'm6-repeat'
        subprocess.run([sys.executable, str(root / generator), str(again)], check=True)
        assert sorted(p.name for p in again.iterdir()) == sorted(p.name for p in target.iterdir())
        assert all(p.read_bytes() == (again / p.name).read_bytes() for p in target.iterdir())
        print('M6 second regeneration: byte-identical', flush=True)
print('PASS: all seven M1-M6 golden/reference datasets and manifests regenerate byte-identically.', flush=True)
