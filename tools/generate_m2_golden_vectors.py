#!/usr/bin/env python3
"""Golden reference vectors for M2 (deterministic animation time).

Revision 2 (Amendment P-R1): COSMETIC differences use Java's wrapping signed-long subtraction (System.nanoTime
semantics). This changes exactly one row of timepoint.csv (COSMETIC, Long.MIN_VALUE, 1) and the manifest; see
docs/m2/M2-AMENDMENT-R2.md. Every other row is byte-identical to revision 1.

Independent of the Java code. Python floats are IEEE-754 binary64 with correctly rounded + - * /, `math.fmod` is
the exact remainder (Java `%` on doubles), `float(int)` rounds to nearest-even like Java's long-to-double
conversion, and `round(float)` is round-half-even like `Math.rint`. Mirroring the operation order of
docs/m2/M2-IMPLEMENTATION-CONTRACT.md therefore reproduces the specified results bit for bit.

Two kinds of check run while generating, and generation aborts if either fails:
  1. Mathematical cross-checks in exact rational arithmetic (fractions.Fraction). For integer inputs the
     contract claims exact stages and cycles and once-rounded progress. For all inputs it claims that the
     remainder is exact and that the cycle index is the exact floor. These are checked independently of the
     float pipeline.
  2. Invariants: progress in [0, 1], cycle >= 0, ACTIVE progress < 1 for forward passes.

Every double is written with float.hex(), which Java's Double.parseDouble reads exactly. Usage:
    python3 tools/generate_m2_golden_vectors.py src/test/resources/m2/golden
Deterministic: fixed seeds, no clock, no environment input. Python 3.9 or later, standard library only.
"""
import hashlib
import math
import os
import random
import sys
from fractions import Fraction

NANOS_PER_TICK = 50_000_000
LONG_MIN, LONG_MAX = -(2 ** 63), 2 ** 63 - 1
FOREVER = LONG_MAX


def hx(x):
    assert math.isfinite(x), x
    return float.hex(x)


# ------------------------------------------------------------------ specified operations (contract section 4)

def wrap64(x):
    """Java's wrapping signed 64-bit subtraction result (two's complement)."""
    return ((x + 2 ** 63) % 2 ** 64) - 2 ** 63


def ticks_since(domain, value, origin):
    if domain == "COSMETIC":
        # [R2] System.nanoTime semantics: wrapping subtraction, never an overflow
        return float(wrap64(value - origin)) / 50_000_000.0
    delta = value - origin
    if delta < LONG_MIN or delta > LONG_MAX:
        return None  # ArithmeticException (GAME only)
    return float(delta)


def timing_sample(delay, duration, mode, cycles, local):
    u = local - delay
    if not math.isfinite(u):
        return None
    if u == 0.0:
        u = 0.0
    if u < 0.0:
        return ("BEFORE", 0, 0.0)
    d = duration
    r = math.fmod(u, d)
    n = float(round((u - r) / d))
    if n >= float(cycles):
        end = 0.0 if (mode == "PING_PONG" and cycles % 2 == 0) else 1.0
        return ("AFTER", cycles - 1, end)
    k = int(n)
    p = (d - r) / d if (mode == "PING_PONG" and k % 2 == 1) else r / d
    return ("ACTIVE", k, p)


class Playback:
    def __init__(self, domain, anchor, anchor_ticks, rate, paused):
        assert math.isfinite(anchor_ticks) and math.isfinite(rate)
        self.domain, self.anchor, self.anchor_ticks, self.rate, self.paused = domain, anchor, anchor_ticks, rate, paused

    def local(self, t):
        if self.paused:
            return self.anchor_ticks
        return self.anchor_ticks + self.rate * ticks_since(self.domain, t, self.anchor)

    def _derived(self, at):
        assert at >= self.anchor
        x = self.local(at)
        assert math.isfinite(x)
        return x

    def pause(self, at):
        assert at >= self.anchor
        return self if self.paused else Playback(self.domain, at, self._derived(at), self.rate, True)

    def resume(self, at):
        assert at >= self.anchor
        return Playback(self.domain, at, self.anchor_ticks, self.rate, False) if self.paused else self

    def with_rate(self, at, rate):
        return Playback(self.domain, at, self._derived(at), rate, self.paused)

    def seek(self, at, ticks):
        assert at >= self.anchor
        return Playback(self.domain, at, ticks, self.rate, self.paused)

    def restart(self, at):
        assert at >= self.anchor
        return Playback(self.domain, at, 0.0, self.rate, False)


# ------------------------------------------------------------------ independent exact checks

def exact_timing(delay, duration, mode, cycles, local):
    """Exact-rational evaluation of the mathematical definition, given the once-rounded elapsed time u."""
    u = local - delay  # the contract defines u as this single rounded subtraction
    if u == 0.0:
        u = 0.0
    if u < 0:
        return ("BEFORE", 0, Fraction(0))
    U, D = Fraction(u), Fraction(duration)
    n = math.floor(U / D)
    if n >= cycles:
        return ("AFTER", cycles - 1, Fraction(0) if (mode == "PING_PONG" and cycles % 2 == 0) else Fraction(1))
    r = U - n * D
    p = (D - r) / D if (mode == "PING_PONG" and n % 2 == 1) else r / D
    return ("ACTIVE", n, p, r)


def check_timing(row_in, out):
    delay, duration, mode, cycles, local = row_in
    ex = exact_timing(*row_in)
    p = out[2]
    assert 0.0 <= p <= 1.0, ("range", row_in, out)
    u = local - delay
    in_domain = u < 0 or Fraction(u) / Fraction(duration) < 2 ** 51
    if not in_domain:
        # outside the guaranteed domain (elapsed / duration >= 2^51) the cycle index is not claimed exact;
        # the remainder, the progress range and the forward rounding are still checked below
        assert out[0] in ("ACTIVE", "AFTER"), ("stage", row_in, out)
        if out[0] == "AFTER":
            return
        ex = ("ACTIVE", out[1], None, Fraction(math.fmod(u, duration)))
        r = ex[3]
        D = Fraction(duration)
        exact_p = (D - r) / D if (mode == "PING_PONG" and out[1] % 2 == 1) else r / D
        ex = ("ACTIVE", out[1], exact_p, r)
    assert out[0] == ex[0] and out[1] == ex[1], ("stage/cycle", row_in, out, ex)
    if ex[0] != "ACTIVE":
        assert Fraction(p) == ex[2], ("end value", row_in, out)
        return
    u = local - delay
    if u == 0.0:
        u = 0.0
    r = math.fmod(u, duration)
    assert Fraction(r) == ex[3], ("remainder must be exact", row_in)
    forward = not (mode == "PING_PONG" and ex[1] % 2 == 1)
    if forward:
        # forward progress is the correctly rounded exact quotient r / d, and strictly below 1
        assert p == float(ex[2]), ("forward progress not correctly rounded", row_in, out, float(ex[2]))
        assert p < 1.0, ("forward progress reached 1", row_in)
    else:
        # backward progress: two roundings, within 2 ulp of 1 and of the exact value
        assert abs(Fraction(p) - ex[2]) <= Fraction(2) * Fraction(2) ** -53, ("backward progress", row_in, out)
    if all(float(x).is_integer() for x in (delay, duration, local)) and forward:
        assert p == float(Fraction(int(local - delay) % int(duration), int(duration))), ("integer exactness", row_in)


# ------------------------------------------------------------------ generators

def tick_seconds_rows():
    ticks = list(range(0, 201)) + [-1, -3, -7, -20, 1199, 1200, 72000, 1728000, 24000, 2 ** 31 - 1, 2 ** 31,
                                   2 ** 40 + 3, 2 ** 52 - 1, 2 ** 52 + 3, 2 ** 53 - 1, 2 ** 53]
    rows = []
    for t in ticks:
        tf = float(t)
        s = tf / 20.0
        assert s == float(Fraction(t, 20)) or abs(t) > 2 ** 53, t  # correctly rounded (exact input below 2^53)
        rows.append((t, hx(s), hx(tf * 0.05), hx(s * 20.0)))
    return rows


def timepoint_rows(rng):
    cases = [("GAME", 0, 0), ("GAME", 5, 2), ("GAME", -3, 4), ("GAME", 2 ** 53 + 1, 0), ("GAME", 2 ** 53, -1),
             ("GAME", LONG_MAX, 0), ("GAME", LONG_MAX, LONG_MAX - 1), ("GAME", LONG_MIN, LONG_MIN + 7),
             ("GAME", LONG_MAX, -1), ("GAME", LONG_MIN, 1), ("GAME", LONG_MAX, LONG_MIN),
             ("COSMETIC", 50_000_000, 0), ("COSMETIC", 1, 0), ("COSMETIC", 0, 1), ("COSMETIC", 123_456_789, 0),
             ("COSMETIC", 1_000_000_000, 0), ("COSMETIC", 2 ** 53 + 1, 0), ("COSMETIC", LONG_MAX, 0),
             ("COSMETIC", -50_000_001, 0), ("COSMETIC", LONG_MIN, 1), ("COSMETIC", 7_000_000_000_000_000_000, -7)]
    for _ in range(150):
        dom = rng.choice(["GAME", "COSMETIC"])
        a = rng.randint(-2 ** 62, 2 ** 62)
        b = a - rng.choice([rng.randint(-10 ** 6, 10 ** 6), rng.randint(-2 ** 55, 2 ** 55), rng.randint(0, 10 ** 12)])
        cases.append((dom, a, b))
    rows = []
    for dom, v, o in cases:
        res = ticks_since(dom, v, o)
        if res is not None and abs(v - o) <= 2 ** 53:
            exact = Fraction(v - o) if dom == "GAME" else Fraction(v - o, NANOS_PER_TICK)
            assert res == float(exact), (dom, v, o)
        rows.append((dom, v, o, "OVERFLOW" if res is None else hx(res)))
    return rows


def timing_rows(rng):
    cases = []
    once40 = (0.0, 40.0, "ONCE", 1)
    for l in [-1.0, -0.0, 0.0, 0.5, 1.0, 39.0, math.nextafter(40.0, 0), 40.0, 41.0, 1e15]:
        cases.append(once40 + (l,))
    loop40 = (0.0, 40.0, "LOOP", FOREVER)
    for l in [0.0, 1.0, 39.0, 40.0, 41.0, 79.0, 80.0, 1e6, 2.0 ** 50, 2.0 ** 50 + 39.0]:
        cases.append(loop40 + (l,))
    for cyc in (FOREVER, 1, 2, 3):
        for l in [0.0, 20.0, 40.0, 60.0, 80.0, 100.0, 119.0, 120.0, 121.0, 160.0, 1e9 + 7.0]:
            cases.append((0.0, 40.0, "PING_PONG", cyc, l))
    for cyc in (1, 2, 3, 5):
        for l in [0.0, 39.0, 40.0, 79.0, 80.0, 119.0, 120.0, 199.0, 200.0, 201.0]:
            cases.append((0.0, 40.0, "LOOP", cyc, l))
    for delay in (10.0, -10.0, 0.5, -1e6):
        for l in [-1e6 - 1.0, -10.0, 0.0, 9.0, 10.0, 10.5, 49.0, 50.0, 51.0]:
            cases.append((delay, 40.0, "LOOP", 2, l))
            cases.append((delay, 40.0, "ONCE", 1, l))
    # precision at large elapsed time: naive frac(u / d) loses ~4e-4 here, the specified remainder keeps 1e-17
    for l in [2.0 ** 45 + 0.5, 2.0 ** 45 + 6.5, 2.0 ** 50 + 0.25, 1e15 + 0.125]:
        cases.append((0.0, 7.0, "LOOP", FOREVER, l))
        cases.append((0.0, 7.0, "PING_PONG", FOREVER, l))
    # non-integer durations, including values that are not exact in binary
    for d in (0.1, 0.15, 3.0, 2.0 ** -20, 1.0 / 3.0, 2.0 ** 52):
        for l in (0.0, 0.05, 0.1, 0.3, 1.0, 7.25, 1e3, 1e9, 2.0 ** 52):
            cases.append((0.0, d, "LOOP", FOREVER, l))
            cases.append((0.0, d, "PING_PONG", 4, l))
    # random integer configurations (the gameplay-authoritative case) and random real configurations
    for _ in range(700):
        delay = float(rng.randint(-200, 200))
        d = float(rng.randint(1, 400))
        mode = rng.choice(["ONCE", "LOOP", "PING_PONG"])
        cyc = 1 if mode == "ONCE" else rng.choice([1, 2, 3, 7, 1000, FOREVER])
        l = float(rng.choice([rng.randint(-500, 5000), rng.randint(-2 ** 20, 2 ** 40)]))
        cases.append((delay, d, mode, cyc, l))
    for _ in range(700):
        delay = rng.uniform(-1e3, 1e3) * rng.choice([1.0, 1e-3, 1e3])
        d = rng.uniform(0.01, 100.0) * rng.choice([1.0, 1e-3, 1e3])
        mode = rng.choice(["ONCE", "LOOP", "PING_PONG"])
        cyc = 1 if mode == "ONCE" else rng.choice([1, 2, 5, 64, FOREVER])
        l = rng.uniform(-1e4, 1e9) * rng.choice([1.0, 1e-3, 1e3])
        cases.append((delay, d, mode, cyc, l))
    rows = []
    for c in cases:
        out = timing_sample(*c)
        assert out is not None
        check_timing(c, out)
        rows.append((hx(c[0]), hx(c[1]), c[2], c[3], hx(c[4]), out[0], out[1], hx(out[2])))
    return rows


def playback_scripts(rng):
    """Each script: list of (op, instant, a, b). START carries (initialTicks, rate)."""
    s = {}
    s["game-basic"] = ("GAME", [("START", 100, 0.0, 1.0), ("EVAL", 100), ("EVAL", 101), ("EVAL", 140), ("EVAL", 99),
                                ("PAUSE", 120), ("EVAL", 200), ("PAUSE", 210), ("RESUME", 300), ("EVAL", 310),
                                ("RESUME", 311), ("RATE", 310, -1.0), ("EVAL", 320), ("SEEK", 330, 5.0),
                                ("EVAL", 335), ("RESTART", 400), ("EVAL", 440), ("RATE", 440, 2.0), ("EVAL", 450)])
    s["game-delayed-start"] = ("GAME", [("START", 0, -20.0, 1.0), ("EVAL", 0), ("EVAL", 19), ("EVAL", 20), ("EVAL", 60)])
    s["game-large-instants"] = ("GAME", [("START", 2 ** 50, 0.0, 1.0), ("EVAL", 2 ** 50 + 72000),
                                         ("PAUSE", 2 ** 50 + 100), ("RESUME", 2 ** 52), ("EVAL", 2 ** 52 + 1),
                                         ("EVAL", 2 ** 53)])
    s["game-dyadic-rate"] = ("GAME", [("START", 7, 3.0, 0.5), ("EVAL", 8), ("EVAL", 9), ("RATE", 9, 0.25), ("EVAL", 13),
                                      ("RATE", 13, -0.125), ("EVAL", 21), ("EVAL", 10 ** 9)])
    s["game-fractional-rate"] = ("GAME", [("START", 0, 0.0, 0.3), ("EVAL", 1), ("EVAL", 3), ("EVAL", 10),
                                          ("RATE", 10, 0.7), ("EVAL", 11), ("RATE", 11, 1.0 / 3.0), ("EVAL", 20)])
    s["cosmetic-basic"] = ("COSMETIC", [("START", 1_000_000_000, 0.0, 1.0), ("EVAL", 1_050_000_000), ("EVAL", 1_000_000_001),
                                        ("EVAL", 1_123_456_789), ("PAUSE", 1_100_000_000), ("EVAL", 9_000_000_000),
                                        ("RESUME", 9_000_000_000), ("EVAL", 9_050_000_000), ("RATE", 9_100_000_000, 0.37),
                                        ("EVAL", 9_200_000_000), ("SEEK", 9_300_000_000, -2.5), ("EVAL", 9_350_000_000)])
    s["cosmetic-huge-nanos"] = ("COSMETIC", [("START", 2 ** 62, 0.0, 1.0), ("EVAL", 2 ** 62 + 50_000_000),
                                             ("EVAL", 2 ** 62 + 2 ** 53 + 1), ("EVAL", LONG_MAX)])
    # long pause/resume and rate toggling chains: re-anchoring error stays per control event, never per frame
    ops = [("START", 0, 0.0, 1.0)]
    t = 0
    for i in range(400):
        t += rng.randint(1, 50)
        ops.append(("PAUSE", t) if i % 2 == 0 else ("RESUME", t))
        if i % 25 == 0:
            ops.append(("EVAL", t + rng.randint(0, 10)))
    ops.append(("EVAL", t + 1000))
    s["game-pause-chain-integer"] = ("GAME", ops)
    ops = [("START", 0, 0.0, 0.3)]
    t = 0
    for i in range(400):
        t += rng.randint(1, 50)
        ops.append(("RATE", t, 0.3 if i % 2 else 0.7))
        if i % 25 == 0:
            ops.append(("EVAL", t + rng.randint(0, 10)))
    ops.append(("EVAL", t + 1000))
    s["game-rate-chain-fractional"] = ("GAME", ops)
    for k in range(20):
        dom = rng.choice(["GAME", "COSMETIC"])
        scale = 1 if dom == "GAME" else rng.choice([1, 1_000, 50_000_000, 16_666_667])
        t = rng.randint(-10 ** 6, 10 ** 6) * scale
        ops = [("START", t, float(rng.randint(-100, 100)), rng.choice([1.0, -1.0, 0.5, 2.0, 0.37, -1.75, 0.0]))]
        for _ in range(rng.randint(3, 15)):
            t += rng.randint(0, 200) * scale
            op = rng.choice(["EVAL", "EVAL", "PAUSE", "RESUME", "RATE", "SEEK", "RESTART"])
            if op == "RATE":
                ops.append((op, t, rng.choice([1.0, -1.0, 0.5, 2.0, 0.37, -1.75, 0.0, 3.0])))
            elif op == "SEEK":
                ops.append((op, t, rng.choice([0.0, 10.0, -5.0, 12.5, 1e6])))
            elif op == "EVAL":
                ops.append((op, t + rng.randint(-5, 50) * scale))
            else:
                ops.append((op, t))
        s["random-%02d" % k] = (dom, ops)
    return s


def playback_rows(scripts):
    rows = []
    for name, (dom, ops) in scripts.items():
        pb = None
        integer_script = True
        for step, op in enumerate(ops):
            kind, at = op[0], op[1]
            a = op[2] if len(op) > 2 else 0.0
            b = op[3] if len(op) > 3 else 0.0
            if kind == "START":
                pb = Playback(dom, at, a, b, False)
                out = pb.anchor_ticks
            elif kind == "EVAL":
                out = pb.local(at)
            elif kind == "PAUSE":
                pb = pb.pause(at); out = pb.anchor_ticks
            elif kind == "RESUME":
                pb = pb.resume(at); out = pb.anchor_ticks
            elif kind == "RATE":
                pb = pb.with_rate(at, a); out = pb.anchor_ticks
            elif kind == "SEEK":
                pb = pb.seek(at, a); out = pb.anchor_ticks
            elif kind == "RESTART":
                pb = pb.restart(at); out = pb.anchor_ticks
            else:
                raise ValueError(kind)
            if kind in ("START", "RATE", "SEEK"):
                integer_script = integer_script and float(a).is_integer() and (kind != "START" or float(b).is_integer())
            rows.append((name, step, kind, dom, at, hx(a), hx(b), hx(out)))
        if dom == "GAME" and integer_script:
            # exactness theorem: integer anchors, offsets and rates give exact integer local times
            for r in rows:
                if r[0] == name:
                    assert float.fromhex(r[7]).is_integer() or abs(float.fromhex(r[7])) > 2 ** 53, (name, r)
    return rows


def write_csv(path, header, rows, comment):
    with open(path, "w", newline="\n") as f:
        f.write("# " + comment + "\n")
        f.write(",".join(header) + "\n")
        for r in rows:
            f.write(",".join(str(x) for x in r) + "\n")


def main(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    rng = random.Random(20261015)
    files = {
        "tick_seconds.csv": (("ticks", "seconds_hex", "times_0_05_hex", "seconds_times_20_hex"), tick_seconds_rows(),
                             "TickTime: seconds = ticks / 20.0 (correctly rounded); ticks * 0.05 shown as the rejected alternative"),
        "timepoint.csv": (("domain", "value", "origin", "ticks_since_hex"), timepoint_rows(rng),
                          "TimePoint.ticksSince: OVERFLOW means ArithmeticException"),
        "timing.csv": (("delay_hex", "duration_hex", "mode", "cycles", "local_hex", "stage", "cycle", "progress_hex"),
                       timing_rows(rng), "Timing.sample: bitwise expected values"),
        "playback.csv": (("script", "step", "op", "domain", "instant", "a_hex", "b_hex", "expected_hex"),
                         playback_rows(playback_scripts(rng)),
                         "Playback scripts: EVAL expects localTicks, every other op expects the new anchorTicks"),
    }
    for name, (header, rows, comment) in files.items():
        write_csv(os.path.join(out_dir, name), header, rows, comment)
    with open(os.path.join(out_dir, "MANIFEST.sha256"), "w", newline="\n") as f:
        for name in sorted(files):
            with open(os.path.join(out_dir, name), "rb") as g:
                f.write(hashlib.sha256(g.read()).hexdigest() + "  " + name + "\n")
    print({name: len(v[1]) for name, v in files.items()})


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "src/test/resources/m2/golden")
