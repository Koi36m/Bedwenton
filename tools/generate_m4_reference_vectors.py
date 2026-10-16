#!/usr/bin/env python3
"""Independent reference vectors for M4 (particle sampling: arc length, equal-distance inversion, uniform parameters,
spacing counts).

Provenance: written for this project by Claude Code; Python 3.9+ standard library only (decimal, fractions). No
third-party library and no code from tutorials or other repositories.

Independence from the recommended production algorithms (docs/m4/M4-IMPLEMENTATION-CONTRACT.md):
  * Arc lengths of lines, circles and helices are exact closed forms. Quadratic Beziers and planar or conical
    spirals use the closed-form antiderivative of sqrt(a t^2 + b t + c) (asinh form), at 60 digits.
  * Cubic Beziers have no elementary closed form. Their oracle is composite 20-point Gauss-Legendre quadrature in
    40-digit Decimal arithmetic, with nodes computed here by Newton iteration on the Legendre polynomial (not the
    5-point rule the contract recommends), refined until two resolutions agree to 1e-28 relative, and split at known
    cusps. The oracle is validated against the closed forms (self-check below).
  * Inversion targets t* solve L(t0, t*) = s by bisection on the oracle antiderivative.
  * Uniform parameters and spacing counts are normative bitwise definitions of the contract. They are computed here
    with Python floats (IEEE binary64, correctly rounded), an independent implementation of the written definition.

Usage: python3 tools/generate_m4_reference_vectors.py src/test/resources/m4/reference
Deterministic: fixed seeds, no clock, no environment input. Output is byte-identical between runs.
"""
import hashlib
import math
import os
import random
import sys
from decimal import Decimal as D, getcontext, localcontext
from fractions import Fraction as F

getcontext().prec = 60


def hx(x):
    assert isinstance(x, float) and math.isfinite(x), x
    return float.hex(x)


# ------------------------------------------------------------------ constants


def _pi():
    with localcontext() as ctx:
        ctx.prec = 80

        def atan_inv(x):
            x = D(x)
            total, term, n = D(0), 1 / x, 0
            while abs(term) > D(10) ** -78:
                total += term / (2 * n + 1) * (1 if n % 2 == 0 else -1)
                term /= x * x
                n += 1
            return total
        return +(16 * atan_inv(5) - 4 * atan_inv(239))


PI = _pi()


def dec(x):
    return D(F(x).numerator) / D(F(x).denominator) if not isinstance(x, D) else x


def exact(x):
    """Exact Decimal of a float (binary64 values are finite decimal fractions)."""
    with localcontext() as ctx:
        ctx.prec = 1200
        return D(x)


def asinh(x):
    return (x + (x * x + 1).sqrt()).ln() if x >= 0 else -asinh(-x)


# ------------------------------------------------------------------ closed form: integral of sqrt(a t^2 + b t + c)


def sqrt_quadratic_integral(a, b, c, t0, t1):
    """Exact integral of sqrt(a t^2 + b t + c) over [t0, t1], for a quadratic that is >= 0 on R (a >= 0)."""
    def prim(t):
        q = a * t * t + b * t + c
        q = q if q > 0 else D(0)
        disc = 4 * a * c - b * b
        if a == 0:  # then b == 0 (non-negative linear function on R is constant)
            return c.sqrt() * t
        if disc > 0:
            return (2 * a * t + b) * q.sqrt() / (4 * a) + disc / (8 * a * a.sqrt()) * asinh((2 * a * t + b) / disc.sqrt())
        # disc == 0: sqrt(a) |t - t*| with t* = -b / 2a; antiderivative sqrt(a) (t - t*) |t - t*| / 2
        ts = -b / (2 * a)
        return a.sqrt() * (t - ts) * abs(t - ts) / 2
    return prim(t1) - prim(t0)


# ------------------------------------------------------------------ independent quadrature oracle (40 digits)


def legendre_nodes(n, prec=50):
    with localcontext() as ctx:
        ctx.prec = prec
        nodes = []
        for i in range(1, n + 1):
            x = D(math.cos(math.pi * (i - 0.25) / (n + 0.5)))
            for _ in range(100):
                p0, p1 = D(1), x
                for k in range(2, n + 1):
                    p0, p1 = p1, ((2 * k - 1) * x * p1 - (k - 1) * p0) / k
                dp = n * (x * p1 - p0) / (x * x - 1)
                dx = p1 / dp
                x -= dx
                if abs(dx) < D(10) ** -(prec - 5):
                    break
            p0, p1 = D(1), x
            for k in range(2, n + 1):
                p0, p1 = p1, ((2 * k - 1) * x * p1 - (k - 1) * p0) / k
            dp = n * (x * p1 - p0) / (x * x - 1)
            nodes.append((x, 2 / ((1 - x * x) * dp * dp)))
        return nodes


GL20 = legendre_nodes(20)


def composite(speed, a, b, panels):
    h = (b - a) / panels
    total = D(0)
    for p in range(panels):
        lo = a + h * p
        c, r = lo + h / 2, h / 2
        total += r * sum(w * speed(c + r * x) for x, w in GL20)
    return total


def oracle_integral(speed, a, b, breaks=()):
    """Composite 20-point Gauss-Legendre, refined until two resolutions agree to 1e-28 relative."""
    with localcontext() as ctx:
        ctx.prec = 40
        pts = [a] + sorted(x for x in breaks if a < x < b) + [b]
        total = D(0)
        for lo, hi in zip(pts, pts[1:]):
            prev, n = composite(speed, lo, hi, 4), 4
            while True:
                n *= 2
                cur = composite(speed, lo, hi, n)
                if abs(cur - prev) <= D(10) ** -28 * (1 + abs(cur)):
                    break
                assert n <= 4096, "oracle did not converge"
                prev = cur
            total += cur
        return +total


# ------------------------------------------------------------------ curves


def bezier_derivative(points, t):
    n = len(points) - 1
    d = [[points[i + 1][c] - points[i][c] for c in range(3)] for i in range(n)]
    s = 1 - t
    if n == 1:
        coeffs = [D(1)]
    elif n == 2:
        coeffs = [s, t]
    else:
        coeffs = [s * s, 2 * s * t, t * t]
    return [n * sum(coeffs[i] * d[i][c] for i in range(n)) for c in range(3)]


def bezier_speed(points):
    def f(t):
        v = bezier_derivative(points, t)
        q = sum(x * x for x in v)
        return q.sqrt() if q > 0 else D(0)
    return f


def quad_coefficients(points):
    """|B'(t)|^2 = a t^2 + b t + c for a quadratic Bezier (B' = 2 (d0 + e t), e = d1 - d0)."""
    d0 = [points[1][c] - points[0][c] for c in range(3)]
    d1 = [points[2][c] - points[1][c] for c in range(3)]
    e = [d1[c] - d0[c] for c in range(3)]
    a = 4 * sum(x * x for x in e)
    b = 8 * sum(d0[c] * e[c] for c in range(3))
    c = 4 * sum(x * x for x in d0)
    return a, b, c


def spiral_coefficients(r0, r1, rise, turns):
    """|P'(t)|^2 for the M3 spiral: dr^2 + rise^2 + (w r(t))^2 with r(t) = r0 + dr t, w = 2 pi turns."""
    dr = r1 - r0
    w = 2 * PI * turns
    return w * w * dr * dr, 2 * w * w * dr * r0, dr * dr + rise * rise + w * w * r0 * r0


def length_exact(kind, params, t0, t1, breaks=()):
    T0, T1 = exact(t0), exact(t1)
    with localcontext() as ctx:
        ctx.prec = 60
        if kind == "LINE":
            p0, p1 = [exact(v) for v in params[0:3]], [exact(v) for v in params[3:6]]
            return (T1 - T0) * sum((p1[c] - p0[c]) ** 2 for c in range(3)).sqrt()
        if kind == "QUAD":
            pts = [[exact(v) for v in params[3 * i:3 * i + 3]] for i in range(3)]
            a, b, c = quad_coefficients(pts)
            return sqrt_quadratic_integral(a, b, c, T0, T1)
        if kind == "SPIRAL":
            r0, r1, rise, turns = (exact(v) for v in params[0:4])
            a, b, c = spiral_coefficients(r0, r1, rise, turns)
            return sqrt_quadratic_integral(a, b, c, T0, T1)
        pts = [[exact(v) for v in params[3 * i:3 * i + 3]] for i in range(4)]
        return oracle_integral(bezier_speed(pts), T0, T1, [exact(x) for x in breaks])


def speed_exact(kind, params, t):
    T = exact(t)
    with localcontext() as ctx:
        ctx.prec = 40
        if kind == "SPIRAL":
            a, b, c = spiral_coefficients(*(exact(v) for v in params[0:4]))
            q = a * T * T + b * T + c
        elif kind == "LINE":
            p0, p1 = [exact(v) for v in params[0:3]], [exact(v) for v in params[3:6]]
            q = sum((p1[c] - p0[c]) ** 2 for c in range(3))
        else:
            n = 3 if kind == "QUAD" else 4
            q = sum(x * x for x in bezier_derivative([[exact(v) for v in params[3 * i:3 * i + 3]] for i in range(n)], T))
        return q.sqrt() if q > 0 else D(0)


# ------------------------------------------------------------------ rows

PARAM_COLS = 12


def pad(params):
    return list(params) + [0.0] * (PARAM_COLS - len(params))


def random_point(rng, mag):
    return [rng.uniform(-1, 1) * mag for _ in range(3)]


def arclength_rows(rng):
    rows = []

    def emit(kind, params, t0, t1, breaks=(), note="", min_speed=None):
        L = length_exact(kind, params, t0, t1, breaks)
        Lf = float(L)
        assert Lf >= 0 and math.isfinite(Lf)
        if min_speed is None:
            grid = [speed_exact(kind, params, t0 + (t1 - t0) * i / 400) for i in range(401)] if t1 > t0 else [D(0)]
            min_speed = float(min(grid)) * 0.5  # conservative lower bound for the inversion tests
        rows.append([kind] + [hx(float(v)) for v in pad(params)] + [hx(t0), hx(t1), hx(Lf), hx(min_speed), note or "-"])
        return L

    # lines: exact, including extrapolated ranges and wide magnitudes
    for _ in range(60):
        mag = 10.0 ** rng.choice([-6, 0, 2, 7, 100])
        t0 = rng.choice([0.0, rng.uniform(-3, 0)])
        t1 = rng.choice([1.0, rng.uniform(1, 4), t0 + rng.uniform(0, 1)])
        emit("LINE", random_point(rng, mag) + random_point(rng, mag), t0, t1, note="exact")
    emit("LINE", [0.0, 0.0, 0.0, 3.0, 4.0, 12.0], 0.0, 1.0, note="exact 13")
    emit("LINE", [1.0, 2.0, 3.0, 1.0, 2.0, 3.0], 0.0, 1.0, note="collapsed", min_speed=0.0)
    emit("LINE", [0.0, 0.0, 0.0, 1.0, 0.0, 0.0], -1000.0, 1000.0, note="extrapolated")
    # circles and helices (constant speed, exact) and planar/conical spirals (closed form)
    for _ in range(100):
        r0 = rng.uniform(0.05, 1) * 10.0 ** rng.choice([0, 1, 2])
        r1 = rng.choice([r0, rng.uniform(0, 1) * 10.0 ** rng.choice([0, 1, 2])])
        rise = rng.choice([0.0, rng.uniform(-40, 40)])
        turns = rng.choice([1.0, -1.0, rng.uniform(-12, 12), float(rng.randint(-50, 50)), 0.25])
        start = rng.choice([0.0, rng.uniform(-3, 3)])
        t0, t1 = rng.choice([(0.0, 1.0), (0.0, 1.0), (rng.uniform(-1, 0), rng.uniform(1, 2)), (0.25, 0.75)])
        rmin = min((1 - t) * r0 + t * r1 for t in (t0, t1))
        if rmin < 0:
            t0, t1 = 0.0, 1.0
        emit("SPIRAL", [r0, r1, rise, turns, start], t0, t1, note="closed form")
    emit("SPIRAL", [5.0, 5.0, 0.0, 1.0, 0.0], 0.0, 1.0, note="circle 10 pi")
    emit("SPIRAL", [2.0, 2.0, 10.0, 8.0, 0.0], 0.0, 1.0, note="helix")
    emit("SPIRAL", [1.0, 6.0, 4.0, 10.0, 0.0], 0.0, 1.0, note="conical")
    emit("SPIRAL", [3.0, 3.0, 0.0, 0.0, 0.0], 0.0, 1.0, note="point", min_speed=0.0)
    emit("SPIRAL", [0.0, 0.0, 7.0, 3.0, 0.0], 0.0, 1.0, note="axis segment")
    # quadratic Beziers: closed form (disc > 0), collinear through a stationary point (disc = 0), straight uniform
    for _ in range(100):
        mag = 10.0 ** rng.choice([-3, 0, 1, 3])
        t0, t1 = rng.choice([(0.0, 1.0), (0.0, 1.0), (rng.uniform(-2, 0), rng.uniform(1, 3))])
        emit("QUAD", random_point(rng, mag) + random_point(rng, mag) + random_point(rng, mag), t0, t1, note="closed form")
    emit("QUAD", [0.0, 0.0, 0.0, 2.0, 0.0, 0.0, 1.0, 0.0, 0.0], 0.0, 1.0, note="collinear reversal at t=2/3", min_speed=0.0)
    emit("QUAD", [0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 2.0, 2.0, 2.0], 0.0, 1.0, note="straight uniform")
    emit("QUAD", [5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0, 5.0], 0.0, 1.0, note="collapsed", min_speed=0.0)
    # cubic Beziers: independent quadrature oracle
    for _ in range(60):
        mag = 10.0 ** rng.choice([0, 1, 2])
        t0, t1 = rng.choice([(0.0, 1.0), (0.0, 1.0), (-0.5, 1.5)])
        emit("CUBIC", random_point(rng, mag) + random_point(rng, mag) + random_point(rng, mag) + random_point(rng, mag), t0, t1, note="quadrature")
    # cusps: B'(t) = (t - c)(alpha + beta t) vanishes at t = c (Bernstein control points from the values at 0, 1/2, 1)
    # dyadic cusp parameters (exact control points); 5/32, 13/64 and 21/32 are not initial panel nodes k/16
    for c, alpha, beta in [(F(5, 32), (1, 2, 0), (3, -1, 2)), (F(1, 2), (2, 0, 1), (-1, 4, 0)), (F(21, 32), (0, 3, -2), (2, 1, 1)),
                           (F(13, 64), (1, 1, 1), (0, -3, 2))]:
        pts = cusp_cubic(c, alpha, beta)
        emit("CUBIC", [float(v) for p in pts for v in p], 0.0, 1.0, breaks=(float(c),), note="cusp at %s" % c, min_speed=0.0)
    emit("CUBIC", [0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0], 0.0, 1.0, breaks=(0.5,), note="stationary point at 1/2", min_speed=0.0)
    return rows


def cusp_cubic(c, alpha, beta):
    """Cubic Bezier with B'(t) = 3 (t - c)(alpha + beta t): first differences from the quadratic's values."""
    def q(t):
        return [3 * (t - c) * (F(alpha[k]) + F(beta[k]) * t) for k in range(3)]
    # B'(t) = 3 [(1-t)^2 d0 + 2 (1-t) t d1 + t^2 d2] with d0 = B'(0)/3, d2 = B'(1)/3, d1 from B'(1/2)
    d0 = [x / 3 for x in q(F(0))]
    d2 = [x / 3 for x in q(F(1))]
    mid = [x / 3 for x in q(F(1, 2))]
    d1 = [2 * mid[k] - (d0[k] + d2[k]) / 2 for k in range(3)]
    p0 = [F(0)] * 3
    p1 = [p0[k] + d0[k] for k in range(3)]
    p2 = [p1[k] + d1[k] for k in range(3)]
    p3 = [p2[k] + d2[k] for k in range(3)]
    for p in (p1, p2, p3):
        for v in p:
            assert float(v) == v, "cusp control points must be exactly representable"
    return [p0, p1, p2, p3]


def inverse_rows(arc_rows, rng):
    """For selected curves: distance s = L * k / 4 and the parameter t* with L(t0, t*) = s (bisection, 200 steps)."""
    rows = []
    pick = [r for r in arc_rows if r[0] in ("QUAD", "SPIRAL", "CUBIC") and float.fromhex(r[PARAM_COLS + 4]) > 0
            and r[-1] in ("closed form", "quadrature") and float.fromhex(r[1 + PARAM_COLS]) < float.fromhex(r[2 + PARAM_COLS])]
    chosen = [r for r in pick if r[0] == "SPIRAL"][:30] + [r for r in pick if r[0] == "QUAD"][:30] + [r for r in pick if r[0] == "CUBIC"][:12]
    for r in chosen:
        kind = r[0]
        params = [float.fromhex(v) for v in r[1:1 + PARAM_COLS]]
        t0, t1 = float.fromhex(r[1 + PARAM_COLS]), float.fromhex(r[2 + PARAM_COLS])
        params = params[:{"QUAD": 9, "SPIRAL": 5, "CUBIC": 12}[kind]]
        L = length_exact(kind, params, t0, t1)
        for k in (1, 2, 3):
            s = L * k / 4
            lo, hi = exact(t0), exact(t1)
            for _ in range(200 if kind != "CUBIC" else 70):
                mid = (lo + hi) / 2
                if length_exact_dec(kind, params, t0, mid) < s:
                    lo = mid
                else:
                    hi = mid
            ts = float((lo + hi) / 2)
            rows.append([kind] + [hx(float(v)) for v in pad(params)] + [hx(t0), hx(t1), str(k) + "/4", hx(float(s)), hx(ts), r[4 + PARAM_COLS]])
    return rows


def length_exact_dec(kind, params, t0, t1dec):
    """Length from t0 to an exact Decimal parameter."""
    T0 = exact(t0)
    with localcontext() as ctx:
        ctx.prec = 60
        if kind == "QUAD":
            a, b, c = quad_coefficients([[exact(v) for v in params[3 * i:3 * i + 3]] for i in range(3)])
            return sqrt_quadratic_integral(a, b, c, T0, t1dec)
        if kind == "SPIRAL":
            a, b, c = spiral_coefficients(*(exact(v) for v in params[0:4]))
            return sqrt_quadratic_integral(a, b, c, T0, t1dec)
        pts = [[exact(v) for v in params[3 * i:3 * i + 3]] for i in range(4)]
        with localcontext() as c2:
            c2.prec = 40
            speed = bezier_speed(pts)
            return composite(speed, T0, t1dec, 64)


def uniform_rows(rng):
    """Normative uniform parameters: t_k = t0 + fl(fl(t1 - t0) * fl(k / den)), first = t0, last (INCLUDE_END) = t1."""
    rows = []
    ranges = [(0.0, 1.0), (-0.0, 1.0), (0.0, 0.0), (-3.0, 4.0), (-1000.0, 1000.0), (0.1, 0.7), (1e-300, 3e-300), (5e-324, 1.5e-323),
              (-1e100, 1e100), (2.0 ** 52, 2.0 ** 52 + 100), (0.3, 0.3000000000000001),
              # t0 + (t1 - t0) != t1 here: the forced last parameter t1 matters
              (float.fromhex("-0x1.42e1666827ddap+11"), float.fromhex("-0x1.277a09a57982bp-46")),
              (float.fromhex("-0x1.20f4e183d0816p+20"), float.fromhex("-0x1.7d55c80c789aep+11"))]
    for _ in range(12):
        a = rng.uniform(-50, 50)
        ranges.append((a, a + rng.uniform(0, 100)))
    for t0, t1 in ranges:
        for policy in ("INCLUDE_END", "EXCLUDE_END"):
            for n in (1, 2, 3, 7, 10, 64, 1000, 1 << 20):
                ks = sorted(set([0, 1, n // 3, n // 2, n - 2, n - 1] + [rng.randrange(n) for _ in range(3)]))
                for k in ks:
                    if not 0 <= k < n:
                        continue
                    den = n - 1 if policy == "INCLUDE_END" else n
                    if k == 0:
                        t = t0
                    elif policy == "INCLUDE_END" and k == n - 1:
                        t = t1
                    else:
                        t = t0 + (t1 - t0) * (k / den)
                    rows.append([hx(t0), hx(t1), policy, str(n), str(k), hx(t)])
    return rows


def count_rows(rng):
    """Normative spacing counts: q = fl(length / spacing); m = floor(q), plus 1 if q - m >= 1/2 (saturating)."""
    rows = []

    def count(length, spacing, policy):
        q = length / spacing
        if not q < 2.0 ** 62:
            return 2 ** 63 - 1
        m = math.floor(q)
        if q - m >= 0.5:
            m += 1
        return m + 1 if policy == "INCLUDE_END" else max(1, m)
    cases = [(0.0, 1.0), (0.4, 1.0), (0.5, 1.0), (math.nextafter(0.5, 0), 1.0), (1.5, 1.0), (math.nextafter(1.5, 0), 1.0),
             (2.5, 1.0), (10.0, 0.1), (31.41592653589793, 0.25), (1.0, 1e-300), (1e300, 1e-10), (5e-324, 1.0), (3.0, 3.0),
             (1e6, 1.0), (7.0, 2.0), (1.0, 3.0), (2.0, 3.0), (0.25, 0.5), (0.75, 0.5), (2.0 ** 62, 1.0), (2.0 ** 61, 1.0)]
    for _ in range(80):
        cases.append((rng.uniform(0, 100) * 10.0 ** rng.choice([-3, 0, 2]), rng.choice([0.05, 0.1, 0.25, 0.5, 1.0, rng.uniform(0.01, 3)])))
    for length, spacing in cases:
        for policy in ("INCLUDE_END", "EXCLUDE_END"):
            rows.append([hx(length), hx(spacing), policy, str(count(length, spacing, policy))])
    return rows


def write(path, header, rows, comment):
    with open(path, "w", newline="\n") as f:
        f.write("# " + comment + "\n")
        f.write(",".join(header) + "\n")
        for r in rows:
            f.write(",".join(str(x) for x in r) + "\n")


def self_check():
    """The quadrature oracle agrees with the closed forms (quadratic Bezier and conical spiral) to 1e-25."""
    pts = [[D(0), D(0), D(0)], [D(3), D(1), D(-2)], [D(1), D(4), D(1)]]
    a, b, c = quad_coefficients(pts)
    closed = sqrt_quadratic_integral(a, b, c, D(0), D(1))

    def speed(t):
        v = bezier_derivative(pts, t)
        return sum(x * x for x in v).sqrt()
    quad = oracle_integral(speed, D(0), D(1))
    assert abs(closed - quad) < D(10) ** -25, (closed, quad)
    a, b, c = spiral_coefficients(D(1), D(6), D(4), D(10))
    closed = sqrt_quadratic_integral(a, b, c, D(0), D(1))
    quad = oracle_integral(lambda t: (a * t * t + b * t + c).sqrt(), D(0), D(1))
    assert abs(closed - quad) < D(10) ** -25 * closed, (closed, quad)
    assert abs(closed - D("220.028322236818885190454071766589914642")) < D(10) ** -35


def main(out_dir):
    self_check()
    os.makedirs(out_dir, exist_ok=True)
    rng = random.Random(20261016)
    pcols = ["p%d" % i for i in range(PARAM_COLS)]
    arc = arclength_rows(rng)
    files = {
        "arclength.csv": (["kind"] + pcols + ["t0_hex", "t1_hex", "length_hex", "min_speed_hex", "note"], arc,
                          "Arc length L(t0, t1) of M3 curves; LINE p0..p5, QUAD and CUBIC control points, SPIRAL r0 r1 rise turns startTurn; exact or 1e-28 oracle, correctly rounded; min_speed is half the minimum speed on a 401-point grid (0 if the speed vanishes)"),
        "inverse.csv": (["kind"] + pcols + ["t0_hex", "t1_hex", "fraction", "distance_hex", "parameter_hex", "min_speed_hex"], inverse_rows(arc, rng),
                        "Inversion: parameter t* with L(t0, t*) = distance = fraction * L(t0, t1); bisection on the oracle antiderivative"),
        "uniform.csv": (["t0_hex", "t1_hex", "endpoints", "count", "k", "parameter_hex"], uniform_rows(rng),
                        "Normative uniform parameters: t0, then t0 + fl(fl(t1 - t0) * fl(k / den)), last t1 for INCLUDE_END"),
        "counts.csv": (["length_hex", "spacing_hex", "endpoints", "count"], count_rows(rng),
                       "Normative spacing counts: m = round-half-up of fl(length / spacing); INCLUDE_END m + 1, EXCLUDE_END max(1, m); saturates at 2^63 - 1"),
    }
    for name, (header, rows, comment) in files.items():
        write(os.path.join(out_dir, name), header, rows, comment)
    with open(os.path.join(out_dir, "MANIFEST.sha256"), "w", newline="\n") as f:
        for name in sorted(files):
            with open(os.path.join(out_dir, name), "rb") as g:
                f.write(hashlib.sha256(g.read()).hexdigest() + "  " + name + "\n")
    print({name: len(v[1]) for name, v in files.items()})


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "src/test/resources/m4/reference")
