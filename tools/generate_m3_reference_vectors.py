#!/usr/bin/env python3
"""Independent reference vectors for M3 (curves, spirals, easing).

Provenance: written for this project by Claude Code; Python 3.9+ standard library only (fractions, decimal). No
third-party library and no code from tutorials or other repositories.

Independence from the production algorithms (contract docs/m3/M3-IMPLEMENTATION-CONTRACT.md):
  * Bezier and line values come from the Bernstein form, evaluated in exact rational arithmetic, not from de
    Casteljau. Each expected double is the correctly rounded exact value.
  * Spiral values come from 60-digit decimal Taylor series for sin and cos, evaluated at the exact rational phase
    startTurn + turns * t. There is no turn-reduction algorithm and no floating-point trigonometry.
  * Easing values come from exact rational polynomials (3t^2 - 2t^3, 6t^5 - 15t^4 + 10t^3, t^n, ...), not from
    the split evaluation the contract recommends.

Row classes:
  EXACT    the exact mathematical value is representable, and every intermediate of any reasonable evaluation order
           is exact (small dyadic inputs). The test compares numerically (==, so -0.0 equals 0.0) with no tolerance.
  ROUNDED  the expected value is the correctly rounded exact value. The test applies the contract's error bound,
           computed from the scale columns of the row.

Usage: python3 tools/generate_m3_reference_vectors.py src/test/resources/m3/reference
Deterministic: fixed seeds, no clock, no environment input. Output is byte-identical between runs.
"""
import hashlib
import math
import os
import random
import sys
from decimal import Decimal, getcontext
from fractions import Fraction as F

getcontext().prec = 60


def hx(x):
    assert math.isfinite(x), x
    return float.hex(x)


def rnd(q):
    """Correctly rounded double of an exact rational (Python's Fraction -> float rounds half-even)."""
    return float(q)


# ------------------------------------------------------------------ exact easing definitions

def ease_exact(name, t):
    if t <= 0:
        return F(0)
    if t >= 1:
        return F(1)
    if name == "LINEAR":
        return t
    if name == "SMOOTHSTEP":
        return 3 * t ** 2 - 2 * t ** 3
    if name == "SMOOTHERSTEP":
        return 6 * t ** 5 - 15 * t ** 4 + 10 * t ** 3
    base, kind = name.rsplit("_", 1) if not name.endswith("IN_OUT") else (name[:-7], "IN_OUT")
    n = {"QUAD": 2, "CUBIC": 3, "QUART": 4, "QUINT": 5}[base]
    if kind == "IN":
        return t ** n
    if kind == "OUT":
        return 1 - (1 - t) ** n
    return 2 ** (n - 1) * t ** n if t < F(1, 2) else 1 - (2 - 2 * t) ** n / 2


EASINGS = ["LINEAR", "SMOOTHSTEP", "SMOOTHERSTEP"] + [f"{b}_{k}" for b in ("QUAD", "CUBIC", "QUART", "QUINT")
                                                      for k in ("IN", "OUT", "IN_OUT")]


def easing_rows(rng):
    rows = []
    specials = [(-1.0, "EXACT"), (-0.0, "EXACT"), (0.0, "EXACT"), (1.0, "EXACT"), (2.0, "EXACT"), (0.5, "EXACT"),
                (5e-324, "ROUNDED"), (1e-300, "ROUNDED"), (1e-8, "ROUNDED"), (math.nextafter(1.0, 0), "ROUNDED"),
                (math.nextafter(0.5, 0), "ROUNDED"), (math.nextafter(0.5, 1), "ROUNDED"), (1 - 1e-8, "ROUNDED")]
    for name in EASINGS:
        pts = list(specials)
        pts += [(k / 256.0, "EXACT") for k in sorted(rng.sample(range(1, 256), 24))]
        pts += [(rng.random(), "ROUNDED") for _ in range(40)]
        for t, cls in pts:
            ex = ease_exact(name, F(t))
            if cls == "EXACT":
                assert rnd(ex) == ex, (name, t)
            rows.append((name, hx(t), cls, hx(rnd(ex))))
    return rows


# ------------------------------------------------------------------ Bezier (Bernstein form, exact)

def bernstein(points, t):
    n = len(points) - 1
    out = []
    for c in range(3):
        out.append(sum(math.comb(n, i) * (1 - t) ** (n - i) * t ** i * points[i][c] for i in range(n + 1)))
    return out


def derivative_points(points):
    n = len(points) - 1
    return [[n * (points[i + 1][c] - points[i][c]) for c in range(3)] for i in range(n)]


def scale(points, t):
    """S = sum_i |B_i(t)| * max_j |P_j|: the magnitude the error bound refers to."""
    n = len(points) - 1
    m = max((abs(c) for p in points for c in p), default=F(0))
    return sum(math.comb(n, i) * abs(1 - t) ** (n - i) * abs(t) ** i for i in range(n + 1)) * m


def bezier_rows(rng):
    rows = []
    kinds = {"LINE": 2, "QUAD": 3, "CUBIC": 4}

    def emit(kind, pts, t, cls):
        P = [[F(c) for c in p] for p in pts]
        T = F(t)
        val = bernstein(P, T)
        d1 = derivative_points(P)
        der = bernstein(d1, T) if len(d1) > 1 else d1[0]
        if len(d1) > 1:
            d2 = derivative_points(d1)
            sec = bernstein(d2, T) if len(d2) > 1 else d2[0]
            s1, s2 = scale(d1, T), (scale(d2, T) if len(d2) > 1 else max(abs(c) for c in d2[0]))
        else:
            sec, s1, s2 = [F(0)] * 3, max(abs(c) for c in d1[0]), F(0)
        if cls == "EXACT":
            for v in val + der + sec:
                assert rnd(v) == v, (kind, pts, t)
        flat = [hx(c) for p in pts for c in p] + [hx(0.0)] * (3 * (4 - len(pts)))
        rows.append([kind, hx(t), cls] + flat + [hx(rnd(v)) for v in val + der + sec]
                    + [hx(rnd(scale(P, T))), hx(rnd(s1)), hx(rnd(s2))])

    # exact class: coordinates are multiples of 1/16 in [-64, 64], t a multiple of 1/64 in [-2, 3]
    for kind, n in kinds.items():
        for _ in range(60):
            pts = [[rng.randint(-1024, 1024) / 16.0 for _ in range(3)] for _ in range(n)]
            t = rng.randint(-128, 192) / 64.0
            emit(kind, pts, t, "EXACT")
        for t in (0.0, 1.0, 0.5):
            emit(kind, [[rng.randint(-64, 64) / 4.0 for _ in range(3)] for _ in range(n)], t, "EXACT")
    # rounded class: arbitrary doubles across magnitudes, inside [0, 1] and extrapolated
    for kind, n in kinds.items():
        for _ in range(150):
            mag = 10.0 ** rng.choice([-6, -3, 0, 3, 7, 15, 100])
            pts = [[rng.uniform(-1, 1) * mag for _ in range(3)] for _ in range(n)]
            t = rng.random() if rng.random() < 0.7 else rng.uniform(-3, 4)
            emit(kind, pts, t, "ROUNDED")
        # endpoints of arbitrary curves must be exact (contract): t = 0 and t = 1 as EXACT rows
        for t in (0.0, 1.0):
            pts = [[rng.uniform(-1, 1) * 1e3 for _ in range(3)] for _ in range(n)]
            P = [[F(c) for c in p] for p in pts]
            v = bernstein(P, F(t))
            assert all(rnd(c) == c for c in v)
            emit(kind, pts, t, "ROUNDED")
    return rows


# ------------------------------------------------------------------ spiral (decimal Taylor series, exact phase)

def pi_dec():
    def atan_inv(x):
        x = Decimal(x)
        total, term, k, x2 = Decimal(0), 1 / x, 0, x * x
        while True:
            t = term / (2 * k + 1)
            if t == 0 or abs(t) < Decimal(10) ** -65:
                break
            total += t if k % 2 == 0 else -t
            term /= x2
            k += 1
        return total
    return 16 * atan_inv(5) - 4 * atan_inv(239)


PI = pi_dec()


def sin_cos_turns(frac):
    """sin and cos of 2*pi*frac for an exact rational frac in [0, 1), by Taylor series at 60 digits.

    At exact quarter turns the values are known exactly (0, 1, 0, -1), so they are returned exactly rather than
    with the series' ~1e-60 residue."""
    if (frac * 4).denominator == 1:
        q = int(frac * 4)
        return [(Decimal(0), Decimal(1)), (Decimal(1), Decimal(0)), (Decimal(0), Decimal(-1)), (Decimal(-1), Decimal(0))][q]
    x = 2 * PI * (Decimal(frac.numerator) / Decimal(frac.denominator))
    x = x - PI  # in [-pi, pi); sin(x + pi) = -sin x, cos(x + pi) = -cos x
    s, c = Decimal(0), Decimal(0)
    term = x
    k = 1
    while abs(term) > Decimal(10) ** -62:
        s += term
        term = -term * x * x / ((k + 1) * (k + 2))
        k += 2
    term = Decimal(1)
    k = 0
    while abs(term) > Decimal(10) ** -62:
        c += term
        term = -term * x * x / ((k + 1) * (k + 2))
        k += 2
    return -s, -c


def spiral_exact(r0, r1, rise, turns, start, t):
    R0, R1, RISE, TU, S, T = (F(v) for v in (r0, r1, rise, turns, start, t))
    phase = S + TU * T
    frac = phase - math.floor(phase)
    sn, cs = sin_cos_turns(frac)
    r = (1 - T) * R0 + T * R1
    dr = R1 - R0
    w = 2 * PI * Decimal(TU.numerator) / Decimal(TU.denominator)

    def d(q):
        return Decimal(q.numerator) / Decimal(q.denominator)
    rD, drD, riseD, tD = d(r), d(dr), d(RISE), d(T)
    p = (rD * cs, riseD * tD, -(rD * sn))
    der = (drD * cs - w * rD * sn, riseD, -drD * sn - w * rD * cs)
    k = 2 * drD * w
    w2r = w * w * rD
    sec = (-k * sn - w2r * cs, Decimal(0), -k * cs + w2r * sn)
    return p, der, sec, frac


def spiral_rows(rng):
    rows = []

    def emit(params, t, cls):
        r0, r1, rise, turns, start = params
        p, der, sec, frac = spiral_exact(r0, r1, rise, turns, start, t)
        vals = [float(v) for v in p + der + sec]  # Decimal -> float: correctly rounded from 60 digits
        if cls == "EXACT":
            for v, ex in zip(vals[:3], p):
                assert Decimal(v) == ex, (params, t)
        rows.append([hx(r0), hx(r1), hx(rise), hx(turns), hx(start), hx(t), cls] + [hx(v) for v in vals])

    # exact class: phases at quarter turns, dyadic radii and rise
    for _ in range(80):
        turns = rng.choice([1.0, -1.0, 2.0, 0.25, -0.75, 3.0, 0.5, 8.0])
        start = rng.choice([0.0, 0.25, 0.5, -0.25, 1.75, -3.0])
        k = rng.randint(-8, 16)
        t = k / 4.0 / abs(turns) if abs(turns) >= 1 else k / 4.0
        phase = F(start) + F(turns) * F(t)
        if (phase * 4).denominator != 1:
            continue
        r0 = rng.randint(0, 64) / 4.0
        r1 = rng.choice([r0, rng.randint(0, 64) / 4.0])
        rise = rng.randint(-32, 32) / 4.0
        if r0 != r1 and ((1 - F(t)) * F(r0) + F(t) * F(r1)) < 0:
            continue
        emit((r0, r1, rise, turns, start), t, "EXACT")
    # rounded class: arbitrary parameters, including large turn counts and extrapolated t
    for _ in range(500):
        r0 = rng.uniform(0, 1) * 10.0 ** rng.choice([-3, 0, 1, 3, 6])
        r1 = rng.choice([r0, rng.uniform(0, 1) * 10.0 ** rng.choice([-3, 0, 1, 3])])
        rise = rng.choice([0.0, rng.uniform(-50, 50)])
        turns = rng.choice([1.0, -1.0, rng.uniform(-10, 10), rng.uniform(-1e4, 1e4), 0.0])
        start = rng.choice([0.0, rng.uniform(-3, 3), 0.1])
        t = rng.random() if rng.random() < 0.7 else rng.uniform(-5, 5)
        R = (1 - F(t)) * F(r0) + F(t) * F(r1)
        if R < 0:
            t = rng.random()
        emit((r0, r1, rise, turns, start), t, "ROUNDED")
    for t in (0.0, 0.125, 0.3, 0.999):  # a million turns
        emit((2.0, 2.0, 0.0, 1048576.0, 0.0), t, "ROUNDED")
    return rows


def write(path, header, rows, comment):
    with open(path, "w", newline="\n") as f:
        f.write("# " + comment + "\n")
        f.write(",".join(header) + "\n")
        for r in rows:
            f.write(",".join(str(x) for x in r) + "\n")


def main(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    rng = random.Random(20261016)
    pts_header = [f"p{i}{c}" for i in range(4) for c in "xyz"]
    files = {
        "easing.csv": (["easing", "t_hex", "class", "expected_hex"], easing_rows(rng),
                       "Easing reference: correctly rounded exact rational value; EXACT rows compare with =="),
        "bezier.csv": (["kind", "t_hex", "class"] + pts_header
                       + ["px", "py", "pz", "dx", "dy", "dz", "sx", "sy", "sz", "scale_p", "scale_d", "scale_s"],
                       bezier_rows(rng), "Line/Bezier reference from the exact Bernstein form; unused control points are 0"),
        "spiral.csv": (["r0", "r1", "rise", "turns", "start_turn", "t_hex", "class",
                        "px", "py", "pz", "dx", "dy", "dz", "sx", "sy", "sz"], spiral_rows(rng),
                       "Spiral reference from 60-digit Taylor series at the exact phase start_turn + turns * t"),
    }
    for name, (header, rows, comment) in files.items():
        write(os.path.join(out_dir, name), header, rows, comment)
    with open(os.path.join(out_dir, "MANIFEST.sha256"), "w", newline="\n") as f:
        for name in sorted(files):
            with open(os.path.join(out_dir, name), "rb") as g:
                f.write(hashlib.sha256(g.read()).hexdigest() + "  " + name + "\n")
    print({name: len(v[1]) for name, v in files.items()})


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "src/test/resources/m3/reference")
