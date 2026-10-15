#!/usr/bin/env python3
"""Independent oracle for the M1 golden vectors (pure Python 3, standard library only).

Every expected value is produced with Rodrigues' rotation formula and explicit
Hamilton quaternion products written here. No code from the Java implementation
and no JOML is involved, so agreement between this file and the Java engine is
evidence from two independent derivations.

Usage:  python3 tools/generate_golden_vectors.py [output_dir]
Default output_dir: src/test/resources/golden

The shipped CSV files are the reference. Re-running may differ in the last bit
on another platform's libm; Java tests therefore compare with tolerances and
never with exact equality. MANIFEST.sha256 guards the shipped files only.
"""
import hashlib
import math
import os
import random
import sys

SEED = 20261014


def add(a, b): return (a[0] + b[0], a[1] + b[1], a[2] + b[2])
def scale(a, s): return (a[0] * s, a[1] * s, a[2] * s)
def dot(a, b): return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def unit(a):
    n = math.sqrt(dot(a, a))
    return (a[0] / n, a[1] / n, a[2] / n)


def rodrigues(axis, angle, v):
    """Active right-handed rotation of v about `axis` by `angle` (counter-clockwise looking down the axis)."""
    k = unit(axis)
    c, s = math.cos(angle), math.sin(angle)
    kxv = cross(k, v)
    kdv = dot(k, v)
    return (v[0] * c + kxv[0] * s + k[0] * kdv * (1 - c),
            v[1] * c + kxv[1] * s + k[1] * kdv * (1 - c),
            v[2] * c + kxv[2] * s + k[2] * kdv * (1 - c))


def quat_axis_angle(axis, angle):
    k = unit(axis)
    s = math.sin(angle / 2)
    return (math.cos(angle / 2), k[0] * s, k[1] * s, k[2] * s)


def quat_mul(a, b):  # Hamilton product a*b: b is applied first
    w1, x1, y1, z1 = a
    w2, x2, y2, z2 = b
    return (w1 * w2 - x1 * x2 - y1 * y2 - z1 * z2,
            w1 * x2 + x1 * w2 + y1 * z2 - z1 * y2,
            w1 * y2 - x1 * z2 + y1 * w2 + z1 * x2,
            w1 * z2 + x1 * y2 - y1 * x2 + z1 * w2)


def rand_unit(rng):
    while True:
        v = (rng.gauss(0, 1), rng.gauss(0, 1), rng.gauss(0, 1))
        if dot(v, v) > 1e-6:
            return unit(v)


def rand_vec(rng, r):
    return (rng.uniform(-r, r), rng.uniform(-r, r), rng.uniform(-r, r))


def fmt(values):
    return ",".join(repr(float(x)) for x in values)


def write(path, comment, header, rows):
    with open(path, "w", newline="\n") as f:
        for line in comment:
            f.write("# " + line + "\n")
        f.write(header + "\n")
        for r in rows:
            f.write(fmt(r) + "\n")


def gen_rotation(rng):
    rows = []
    axes = [(1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)]
    angles = [0.0, math.pi / 2, math.pi, -math.pi / 2, -math.pi, 2 * math.pi, 1e-9]
    for ax in axes:
        for ang in angles:
            v = (1.0, 2.0, 3.0)
            rows.append(list(ax) + [ang] + list(v) + list(rodrigues(ax, ang, v)))
    for _ in range(2000):
        ax = rand_unit(rng)
        ang = rng.uniform(-2 * math.pi, 2 * math.pi)
        v = rand_vec(rng, 10.0)
        rows.append(list(ax) + [ang] + list(v) + list(rodrigues(ax, ang, v)))
    return rows


def gen_compose(rng):
    rows = []
    for _ in range(1500):
        a, b = rand_unit(rng), rand_unit(rng)
        aa, ab = rng.uniform(-math.pi, math.pi), rng.uniform(-math.pi, math.pi)
        v = rand_vec(rng, 10.0)
        e = rodrigues(a, aa, rodrigues(b, ab, v))  # b first, then a  == a.mul(b)
        rows.append(list(a) + [aa] + list(b) + [ab] + list(v) + list(e))
    return rows


def pose_world(pt, pa, pang, ps, ct, ca, cang, cs, p):
    inner = add(ct, scale(rodrigues(ca, cang, p), cs))
    return add(pt, scale(rodrigues(pa, pang, inner), ps))


def gen_pose(rng, large):
    rows = []
    n = 500 if large else 1500
    for _ in range(n):
        if large:
            pt = (rng.choice([-1, 1]) * rng.uniform(2.5e7, 3.0e7), rng.uniform(-64, 320),
                  rng.choice([-1, 1]) * rng.uniform(2.5e7, 3.0e7))
            ct = rand_vec(rng, 100.0)
            p = rand_vec(rng, 100.0)
            ps, cs = rng.uniform(0.25, 4.0), rng.uniform(0.25, 4.0)
        else:
            pt, ct = rand_vec(rng, 100.0), rand_vec(rng, 100.0)
            p = rand_vec(rng, 50.0)
            ps, cs = rng.uniform(0.1, 4.0), rng.uniform(0.1, 4.0)
        pa, ca = rand_unit(rng), rand_unit(rng)
        pang, cang = rng.uniform(-math.pi, math.pi), rng.uniform(-math.pi, math.pi)
        e = pose_world(pt, pa, pang, ps, ct, ca, cang, cs, p)
        rows.append(list(pt) + list(pa) + [pang, ps] + list(ct) + list(ca) + [cang, cs] + list(p) + list(e))
    return rows


def gen_slerp(rng):
    rows = []
    for _ in range(1500):
        a0 = rand_unit(rng)
        ang0 = rng.uniform(-math.pi, math.pi)
        ra = rand_unit(rng)
        phi = rng.uniform(0.01, math.pi - 0.05) * rng.choice([-1, 1])  # relative angle, |phi| < pi
        t = rng.uniform(0.0, 1.0)
        v = rand_vec(rng, 10.0)
        q0 = quat_axis_angle(a0, ang0)
        q1 = quat_mul(q0, quat_axis_angle(ra, phi))
        # slerp(q0, q1, t) = q0 * rel(t*phi); applied to v: rel(t*phi) first, then q0
        e = rodrigues(a0, ang0, rodrigues(ra, t * phi, v))
        rows.append(list(q0) + list(q1) + [t] + list(v) + list(e))
    return rows


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join("src", "test", "resources", "golden")
    os.makedirs(out, exist_ok=True)
    rng = random.Random(SEED)
    common = ["Generated by tools/generate_golden_vectors.py, seed %d." % SEED,
              "Conventions: right-handed, active rotations, +X east, +Y up, +Z south (Minecraft world axes)."]
    write(os.path.join(out, "rotation_cases.csv"),
          common + ["expected = Rodrigues(axis, angle, v). Java: Quat.axisAngle(axis, angle).rotate(v)."],
          "ax,ay,az,angle,vx,vy,vz,ex,ey,ez", gen_rotation(rng))
    write(os.path.join(out, "compose_cases.csv"),
          common + ["expected = Rodrigues(a, Rodrigues(b, v)). Java: qa.mul(qb).rotate(v)  (b is applied first)."],
          "a_ax,a_ay,a_az,a_ang,b_ax,b_ay,b_az,b_ang,vx,vy,vz,ex,ey,ez", gen_compose(rng))
    pose_header = ("ptx,pty,ptz,pax,pay,paz,pang,ps,ctx,cty,ctz,cax,cay,caz,cang,cs,px,py,pz,ex,ey,ez")
    pose_comment = ["expected = Tp + sp*R(parent, Tc + sc*R(child, p)). Java: parent.then(child).toWorld(p)."]
    write(os.path.join(out, "pose_cases.csv"), common + pose_comment, pose_header, gen_pose(rng, False))
    write(os.path.join(out, "pose_large_cases.csv"),
          common + pose_comment + ["Parent translations near +/-3e7 blocks (Minecraft world-border scale)."],
          pose_header, gen_pose(rng, True))
    write(os.path.join(out, "slerp_cases.csv"),
          common + ["q0,q1 are unit quaternions (w,x,y,z) with relative angle |phi| < pi, so dot(q0,q1) > 0.",
                    "expected = action of slerp(q0,q1,t) on v = Rodrigues(q0, Rodrigues(relAxis, t*phi, v))."],
          "q0w,q0x,q0y,q0z,q1w,q1x,q1y,q1z,t,vx,vy,vz,ex,ey,ez", gen_slerp(rng))
    names = sorted(n for n in os.listdir(out) if n.endswith(".csv"))
    with open(os.path.join(out, "MANIFEST.sha256"), "w", newline="\n") as m:
        for n in names:
            with open(os.path.join(out, n), "rb") as f:
                m.write("%s  %s\n" % (hashlib.sha256(f.read()).hexdigest(), n))
    print("wrote", ", ".join(names), "to", out)


if __name__ == "__main__":
    main()
