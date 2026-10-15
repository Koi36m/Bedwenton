#!/usr/bin/env python3
"""Independent boundary vectors for the M2 review (Claude Code).

Unlike tools/generate_m2_golden_vectors.py, this generator does NOT replay the contract's operation sequence (no
fmod, no rint). It evaluates the mathematical definition in exact rational arithmetic:

    u        = fl(local - delay)                 (the contract's single rounded subtraction; an input here)
    n        = floor(U / D)                      exact, U and D the exact rationals of u and the duration
    r        = U - n D                           exact
    forward  = round_to_nearest_even(r / D)      the correctly rounded double
    backward = (D - r) / D                       exact rational; the contract allows two roundings (2 ulp)

Cases sit on and next to cycle boundaries: for each duration d and cycle index k, the elapsed times
nextDown(fl(k d)), fl(k d) and nextUp(fl(k d)), for k up to 2^51 - 1 (the edge of the contract's cycle-exact
domain). Durations include values that are not exact in binary, a subnormal, and Double.MIN_VALUE.

Output columns: duration_hex, mode, cycles, local_hex, stage, cycle, progress_exact_num, progress_exact_den,
forward_rounded_hex. Usage:
    python3 review/m2/generate_boundary_vectors.py src/test/resources/m2/review
Deterministic, standard library only.
"""
import hashlib
import math
import os
import sys
from fractions import Fraction

FOREVER = 2 ** 63 - 1


def oracle(duration, mode, cycles, local):
    u = local - 0.0
    if u == 0.0:
        u = 0.0
    if u < 0:
        return "BEFORE", 0, Fraction(0)
    U, D = Fraction(u), Fraction(duration)
    n = math.floor(U / D)
    if n >= cycles:
        return "AFTER", cycles - 1, Fraction(0) if (mode == "PING_PONG" and cycles % 2 == 0) else Fraction(1)
    r = U - n * D
    backward = mode == "PING_PONG" and n % 2 == 1
    return "ACTIVE", n, (D - r) / D if backward else r / D


def main(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    durations = [1.0, 3.0, 7.0, 40.0, 1200.0, 0.1, 0.15, 1.0 / 3.0, 2.0 ** -20, 1e-6, 123.456,
                 2.0 ** -1060, 5e-324, 2.0 ** 40 + 1.0]
    ks = [0, 1, 2, 3, 7, 1000, 2 ** 20 + 1, 2 ** 31 + 3, 2 ** 40 - 1, 2 ** 50, 2 ** 51 - 1]
    rows = []
    for d in durations:
        for k in ks:
            base = float(Fraction(k) * Fraction(d))  # correctly rounded k*d
            if not math.isfinite(base):
                continue
            for x in (math.nextafter(base, -math.inf), base, math.nextafter(base, math.inf)):
                if x < 0:
                    continue
                if Fraction(x) / Fraction(d) >= 2 ** 51:
                    continue  # outside the contract's cycle-exact domain
                for mode, cycles in (("LOOP", FOREVER), ("PING_PONG", FOREVER), ("LOOP", 3), ("PING_PONG", 4), ("ONCE", 1)):
                    stage, cycle, p = oracle(d, mode, cycles, x)
                    fwd = float(p)  # Fraction -> float is correctly rounded (half-even)
                    rows.append((float.hex(d), mode, cycles, float.hex(x), stage, cycle, p.numerator, p.denominator,
                                 float.hex(fwd)))
    path = os.path.join(out_dir, "boundary_cases.csv")
    with open(path, "w", newline="\n") as f:
        f.write("# Independent exact-rational boundary oracle (no fmod/rint); see review/m2/generate_boundary_vectors.py\n")
        f.write("duration_hex,mode,cycles,local_hex,stage,cycle,progress_num,progress_den,progress_rounded_hex\n")
        for r in rows:
            f.write(",".join(str(c) for c in r) + "\n")
    with open(path, "rb") as g:
        digest = hashlib.sha256(g.read()).hexdigest()
    with open(os.path.join(out_dir, "MANIFEST.sha256"), "w", newline="\n") as f:
        f.write(digest + "  boundary_cases.csv\n")
    print(len(rows), "rows")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "src/test/resources/m2/review")
