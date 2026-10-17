#!/usr/bin/env python3
"""Independent reference frames for the M5 R1 example scenes (healing ritual, orbiting sparkles, moving trident
helix, Astral Prism constellation).

Python standard library only. Written for this project from the scene definitions of docs/m5/M5-TEST-SPECIFICATION.md
section 4; it shares no code with any Java implementation.

Method (independent of the recommended Java algorithm):
- time: M2 semantics re-derived with exact rationals (fractions.Fraction): local ticks, half-open passes, LOOP and
  PING_PONG, AFTER end progress;
- rotations: 3x3 matrices at 40 significant digits (decimal), built from unit quaternions given as inputs and from
  axis-angle via Rodrigues' formula; the Java design uses quaternions;
- trigonometry: Taylor series at 40 digits after exact reduction of the angle in turns;
- easing: the M3 polynomials evaluated at 40 digits;
- poses: parent.then(child) applied as nested maps x -> t + s R x, never composed as quaternions.

Usage: python3 tools/generate_m5_reference_frames.py <output directory>
Writes healing.csv, orbit.csv, trident.csv, constellation.csv and MANIFEST.sha256 (deterministic, byte-identical on
every run).
"""
import hashlib
import os
import sys
from decimal import Decimal as D, getcontext
from fractions import Fraction as F

getcontext().prec = 40

PI = D("3.141592653589793238462643383279502884197169399375105820974944592")


# --------------------------------------------------------------------------------------------- numbers

def dec(x):
    """Exact Decimal of a float or Fraction (fractions rounded to the context precision)."""
    if isinstance(x, F):
        return D(x.numerator) / D(x.denominator)
    if isinstance(x, D):
        return x
    return D(x)  # exact for a Python float


def frac_turns(t):
    """Reduce turns (Fraction or Decimal) to [0, 1)."""
    if isinstance(t, F):
        return t - (t.numerator // t.denominator)
    i = t.to_integral_value(rounding="ROUND_FLOOR")
    return t - i


def sin_cos_turns(turns):
    """sin and cos of 2 pi turns at 40 digits (Taylor series after reduction to [-1/8, 1/8] turns)."""
    f = dec(frac_turns(turns))
    # quadrant: nearest quarter
    q = int((f * 4 + D("0.5")).to_integral_value(rounding="ROUND_FLOOR")) % 4
    r = f - D(q) / 4
    if r > D("0.5"):
        r -= 1
    x = 2 * PI * r
    s, c, term_s, term_c, k = D(0), D(0), x, D(1), 0
    while True:
        s += term_s
        c += term_c
        k += 1
        term_s = -term_s * x * x / ((2 * k) * (2 * k + 1))
        term_c = -term_c * x * x / ((2 * k - 1) * (2 * k))
        if abs(term_s) < D("1e-45") and abs(term_c) < D("1e-45"):
            break
    return [(s, c), (c, -s), (-s, -c), (-c, s)][q]


def sqrt(x):
    return dec(x).sqrt()


# --------------------------------------------------------------------------------------------- M2 time (exact)

def ticks_since(domain, value, origin):
    if domain == "GAME":
        return F(value - origin)
    delta = (value - origin + 2 ** 63) % 2 ** 64 - 2 ** 63  # two's-complement wrap, as Java long subtraction
    return F(delta, 50_000_000)


def local_ticks(pb, domain, now):
    anchor, anchor_ticks, rate, paused = pb
    if paused:
        return F(anchor_ticks)
    return F(anchor_ticks) + F(rate) * ticks_since(domain, now, anchor)


def timing_sample(t, L):
    """t = (delay, duration, mode, cycles); returns (stage, cycle, progress Fraction)."""
    delay, dur, mode, cycles = t
    u = L - F(delay)
    if u < 0:
        return ("BEFORE", 0, F(0))
    d = F(dur)
    n = u // d
    r = u - n * d
    if n >= cycles:
        end = F(0) if (mode == "PING_PONG" and cycles % 2 == 0) else F(1)
        return ("AFTER", cycles - 1, end)
    p = (d - r) / d if (mode == "PING_PONG" and n % 2 == 1) else r / d
    return ("ACTIVE", int(n), p)


FOREVER = 2 ** 63 - 1


def once(d, delay=0):
    return (delay, d, "ONCE", 1)


def loop(d, delay=0):
    return (delay, d, "LOOP", FOREVER)


def ping_pong(d, delay=0, cycles=FOREVER):
    return (delay, d, "PING_PONG", cycles)


# --------------------------------------------------------------------------------------------- M3 easing

def ease(name, p):
    p = dec(p)
    if p <= 0:
        return D(0)
    if p >= 1:
        return D(1)
    if name == "LINEAR":
        return p
    if name == "QUAD_OUT":
        return 1 - (1 - p) ** 2
    if name == "SMOOTHSTEP":
        return p * p * (3 - 2 * p)
    raise ValueError(name)


# --------------------------------------------------------------------------------------------- geometry

def mat_from_quat(w, x, y, z):
    w, x, y, z = dec(w), dec(x), dec(y), dec(z)
    n = (w * w + x * x + y * y + z * z).sqrt()
    w, x, y, z = w / n, x / n, y / n, z / n
    return [[1 - 2 * (y * y + z * z), 2 * (x * y - w * z), 2 * (x * z + w * y)],
            [2 * (x * y + w * z), 1 - 2 * (x * x + z * z), 2 * (y * z - w * x)],
            [2 * (x * z - w * y), 2 * (y * z + w * x), 1 - 2 * (x * x + y * y)]]


def mat_axis_angle(axis, turns):
    ax = [dec(a) for a in axis]
    n = (ax[0] ** 2 + ax[1] ** 2 + ax[2] ** 2).sqrt()
    kx, ky, kz = ax[0] / n, ax[1] / n, ax[2] / n
    s, c = sin_cos_turns(turns)
    v = 1 - c
    return [[c + kx * kx * v, kx * ky * v - kz * s, kx * kz * v + ky * s],
            [ky * kx * v + kz * s, c + ky * ky * v, ky * kz * v - kx * s],
            [kz * kx * v - ky * s, kz * ky * v + kx * s, c + kz * kz * v]]


IDENTITY = [[D(1), D(0), D(0)], [D(0), D(1), D(0)], [D(0), D(0), D(1)]]


def apply(pose, p):
    """pose = (t, R, s): x -> t + s R x."""
    t, R, s = pose
    return [dec(t[i]) + s * (R[i][0] * p[0] + R[i][1] * p[1] + R[i][2] * p[2]) for i in range(3)]


def anchor_pose(tx, ty, tz, qw, qx, qy, qz, s):
    return ((tx, ty, tz), mat_from_quat(qw, qx, qy, qz), dec(s))


# --------------------------------------------------------------------------------------------- motions

def step_pose(step, L):
    kind = step[0]
    if kind == "fixed":
        _, t, q, s = step
        return (t, mat_from_quat(*q), dec(s))
    _, drive, easing = step[0], step[-2], step[-1]
    stage, cycle, p = timing_sample(drive, L)
    e = ease(easing, p)
    if kind == "spin":
        _, axis, turns, phase, _, _ = step
        tau = dec(F(phase)) + dec(F(turns)) * e
        return ((0, 0, 0), mat_axis_angle(axis, tau), D(1))
    if kind == "scale":
        _, a, b, _, _ = step
        return ((0, 0, 0), IDENTITY, (1 - e) * dec(a) + e * dec(b))
    if kind == "translate":
        _, a, b, _, _ = step
        return ([(1 - e) * dec(a[i]) + e * dec(b[i]) for i in range(3)], IDENTITY, D(1))
    raise ValueError(kind)


def world_point(anchor, motion, L, p):
    """anchor.then(m_0).then(m_1)...: apply the innermost step first."""
    x = [dec(c) for c in p]
    for step in reversed(motion):
        x = apply(step_pose(step, L), x)
    return apply(anchor, x)


# --------------------------------------------------------------------------------------------- shapes

def circle_points(radius, n):
    """M3 Spiral.circle(radius) sampled at t = k/n (EXCLUDE_END): (r cos 2 pi t, 0, -r sin 2 pi t)."""
    out = []
    for k in range(n):
        s, c = sin_cos_turns(F(k, n))
        out.append([dec(radius) * c, D(0), -dec(radius) * s])
    return out


def helix_points(radius, rise, turns, n):
    """M3 Spiral.helix sampled at equal arc length; constant speed, so t = k/(n-1) (INCLUDE_END)."""
    out = []
    for k in range(n):
        t = F(k, n - 1)
        s, c = sin_cos_turns(F(turns) * t)
        out.append([dec(radius) * c, dec(rise) * dec(t), -dec(radius) * s])
    return out


def line_points(a, b, n):
    return [[dec(a[i]) + (dec(b[i]) - dec(a[i])) * dec(F(k, n - 1)) for i in range(3)] for k in range(n)]


# --------------------------------------------------------------------------------------------- scene evaluation

def prefix(p, n):
    if n == 0:
        return 0
    if p >= 1:
        return n
    k = (p * n).numerator // (p * n).denominator
    return min(n - 1, k)


def evaluate(scene, anchor, pb, now):
    """Returns (stage, local, batches); batches = [(index, name, style, tstage, cycle, progress, [points])]."""
    domain, lifetime, layers = scene
    L = local_ticks(pb, domain, now)
    if L < 0:
        return ("BEFORE", L, [])
    if lifetime is not None and L >= lifetime:
        return ("AFTER", L, [])
    out = []
    for index, (name, timing, visibility, reveal, motion, shape, style) in enumerate(layers):
        stage, cycle, p = timing_sample(timing, L)
        if not (stage == "ACTIVE" or (stage == "AFTER" and visibility == "HOLD_AFTER")):
            continue
        n = len(shape)
        k = n if reveal == "ALL" else prefix(p, n)
        if reveal == "PREFIX" and 0 < p < 1:
            frac = p * n - (p * n).numerator // (p * n).denominator
            assert frac != 0 and min(frac, 1 - frac) > F(1, 1000), ("ambiguous reveal boundary", name, L)
        if k == 0:
            continue
        pts = [world_point(anchor, motion, L, shape[i]) for i in range(k)]
        out.append((index, name, style, stage, cycle, p, pts))
    return ("ACTIVE", L, out)


def fmt(x):
    return repr(float(dec(x)))


def write_rows(path, header, rows):
    with open(path, "w", newline="\n") as f:
        f.write("# M5 R1 independent reference frames; generator tools/generate_m5_reference_frames.py (Python standard library)\n")
        f.write(header + "\n")
        for r in rows:
            f.write(",".join(r) + "\n")


def frame_rows(case, scene, anchor_in, pb, now):
    stage, L, batches = evaluate(scene, anchor_pose(*anchor_in), pb, now)
    a = [repr(float(v)) for v in anchor_in]
    common = [case, str(now), pb_text(pb), *a, stage, fmt(L)]
    if not batches:
        return [common + ["-1", "-", "-", "-", "-1", "0", "-1", "0", "0", "0"]]
    rows = []
    for (index, name, style, tstage, cycle, p, pts) in batches:
        for i, w in enumerate(pts):
            rows.append(common + [str(index), name, style, tstage, str(cycle), fmt(p), str(i), fmt(w[0]), fmt(w[1]), fmt(w[2])])
    return rows


def pb_text(pb):
    anchor, ticks, rate, paused = pb
    return "%d;%s;%s;%s" % (anchor, repr(float(ticks)), repr(float(rate)), "paused" if paused else "running")


HEADER = ("case,now,playback,anchor_tx,anchor_ty,anchor_tz,anchor_qw,anchor_qx,anchor_qy,anchor_qz,anchor_scale,"
          "frame_stage,local_ticks,layer_index,layer,style,timing_stage,cycle,progress,point,x,y,z")


# --------------------------------------------------------------------------------------------- demo scenes
# Every literal below is the double given in docs/m5/M5-TEST-SPECIFICATION.md section 4.

def healing_scene():
    ring = circle_points(1.0, 24)
    layers = []
    for k in range(3):
        t = once(16, delay=20 * k)
        motion = [("translate", (0, 0.1, 0), (0, 1.0, 0), t, "LINEAR"), ("scale", 0.5, 3.0, t, "QUAD_OUT")]
        layers.append(("pulse-%d" % k, t, "ACTIVE_ONLY", "ALL", motion, ring, "bedwenton:heal_ring"))
    return ("GAME", F(60), layers)


def orbit_scene():
    spark = [[D("1.5"), D(0), D(0)]]
    layers = []
    for k in range(4):
        motion = [("spin", (0, 1, 0), 1, F(k, 4), loop(40), "LINEAR")]
        layers.append(("spark-%d" % k, loop(40), "ACTIVE_ONLY", "ALL", motion, spark, "bedwenton:spark"))
    for j in range(1, 4):
        motion = [("spin", (0, 1, 0), 1, F(-3 * j, 100), loop(40), "LINEAR")]
        layers.append(("spark-0-trail-%d" % j, loop(40), "ACTIVE_ONLY", "ALL", motion, spark, "bedwenton:spark_trail"))
    return ("COSMETIC", None, layers)


def trident_scene():
    helix = helix_points(0.6, 2.0, 3, 37)
    t = once(30)
    motion = [("fixed", (0, -1.0, 0), (1, 0, 0, 0), 1), ("spin", (0, 1, 0), 2, 0, t, "LINEAR")]
    return ("GAME", F(30), [("water-helix", t, "ACTIVE_ONLY", "PREFIX", motion, helix, "bedwenton:water")])


def constellation_scene():
    stars = circle_points(2.0, 5)
    layers = []
    spin = ("spin", (0, 1, 0), 1, 0, loop(120), "LINEAR")
    tilt = ("fixed", (0, 0, 0), (0.98, 0.2, 0.0, 0.0), 1)
    layers.append(("stars", once(120), "ACTIVE_ONLY", "ALL", [spin, tilt], stars, "astral:star"))
    for j in range(5):
        a, b = stars[j], stars[(j + 2) % 5]
        layers.append(("link-%d" % j, once(10, delay=10 + 12 * j), "HOLD_AFTER", "PREFIX", [spin, tilt], line_points(a, b, 9), "astral:link"))
    return ("GAME", F(120), layers)


def main(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    files = {}

    # A. healing ritual: playback started at tick 1000, rate 1; then a reversed playback (rate -1 from local 60).
    scene = healing_scene()
    anchor = (100.5, 64.0, -20.5, 1.0, 0.0, 0.0, 0.0, 1.0)
    rows = []
    for now in (999, 1000, 1001, 1008, 1015, 1016, 1019, 1020, 1027, 1036, 1040, 1047, 1055, 1056, 1059, 1060, 1061, 10 ** 12):
        rows += frame_rows("forward", scene, anchor, (1000, 0.0, 1.0, False), now)
    for now in (2000, 2001, 2005, 2012, 2020, 2061):
        rows += frame_rows("reverse", scene, anchor, (2000, 60.0, -1.0, False), now)
    inv = (-12.25, 3.5, 7.0, 0.5, 0.5, 0.5, -0.5, 2.0)  # a rotated, scaled anchor
    for now in (1004, 1028, 1050):
        rows += frame_rows("rotated", scene, inv, (1000, 0.0, 1.0, False), now)
    for now in (1000, 1003, 9999):
        rows += frame_rows("paused", scene, anchor, (1000, 8.0, 1.0, True), now)
    for now in (1000, 1001, 1002, 1031):
        rows += frame_rows("half-rate", scene, anchor, (1000, 0.0, 0.5, False), now)
    files["healing.csv"] = rows

    # B. orbiting sparkles: COSMETIC nanoseconds across the long wrap; moving and turning player anchor.
    scene = orbit_scene()
    t0 = 2 ** 63 - 1 - 400_000_000  # wraps after 8 ticks
    rows = []
    path = [(0, 10.0, 64.0, 10.0), (12_500_000, 10.1, 64.0, 10.0), (500_000_000, 11.0, 64.0, 10.5),
            (1_000_000_000, 12.0, 64.25, 11.0), (1_987_500_000, 13.0, 64.5, 11.0), (2_000_000_000, 13.0, 64.5, 11.0),
            (-25_000_000, 10.0, 64.0, 10.0)]
    yaw = [(1.0, 0.0, 0.0, 0.0), (0.7071067811865476, 0.0, 0.7071067811865476, 0.0), (0.0, 0.0, 1.0, 0.0)]
    for i, (dn, x, y, z) in enumerate(path):
        now = (t0 + dn + 2 ** 63) % 2 ** 64 - 2 ** 63
        q = yaw[i % 3]
        rows += frame_rows("moving", scene, (x, y, z, *q, 1.0), (t0, 0.0, 1.0, False), now)
    files["orbit.csv"] = rows

    # C. moving trident: one anchor snapshot per instant (position and orientation of the trident).
    scene = trident_scene()
    rows = []
    snaps = [(0, 0.0, 70.0, 0.0, 1.0, 0.0, 0.0, 0.0), (1, 0.5, 70.0, 0.2, 0.9238795325112867, 0.3826834323650898, 0.0, 0.0),
             (7, 3.5, 69.0, 1.4, 0.7071067811865476, 0.7071067811865476, 0.0, 0.0),
             (15, 7.5, 67.0, 3.0, 0.5, 0.5, 0.5, 0.5), (22, 11.0, 64.0, 4.4, 0.0, 1.0, 0.0, 0.0),
             (29, 14.5, 61.0, 5.8, -0.2, 0.9, 0.1, 0.3), (30, 15.0, 60.0, 6.0, 1.0, 0.0, 0.0, 0.0)]
    for (dt, x, y, z, qw, qx, qy, qz) in snaps:
        rows += frame_rows("thrown", scene, (x, y, z, qw, qx, qy, qz, 1.0), (500, 0.0, 1.0, False), 500 + dt)
    files["trident.csv"] = rows

    # D. Astral Prism constellation: staged pentagram links (prefix reveal, held after) on a spinning, tilted star ring.
    scene = constellation_scene()
    rows = []
    altar = (-300.5, 80.0, 220.5, 0.9659258262890683, 0.0, 0.25881904510252074, 0.0, 1.5)
    for now in (0, 5, 10, 13, 19, 20, 22, 27, 34, 45, 58, 64, 70, 90, 119, 120):
        rows += frame_rows("staged", scene, altar, (0, 0.0, 1.0, False), now)
    for now in (100, 101):
        rows += frame_rows("seek", scene, altar, (100, 47.0, 1.0, False), now)
    files["constellation.csv"] = rows

    for name, rows in files.items():
        write_rows(os.path.join(out_dir, name), HEADER, rows)
    with open(os.path.join(out_dir, "MANIFEST.sha256"), "w", newline="\n") as m:
        for name in sorted(files):
            with open(os.path.join(out_dir, name), "rb") as f:
                m.write("%s  %s\n" % (hashlib.sha256(f.read()).hexdigest(), name))
    for name in sorted(files):
        print(name, len(files[name]), "rows")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "src/test/resources/m5/reference")
