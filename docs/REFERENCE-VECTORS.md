# Reference vectors

Two independent sources agree on every value below: (a) derivation by hand from the formulas in
`CONVENTIONS.md`, (b) a Python 3 computation with Rodrigues' formula and Hamilton products
(`tools/generate_golden_vectors.py`, standard library only, no Java, no JOML). The eight vectors in section 1
were also reproduced in a Python scratch check before this handoff.

## 1. Hand-derived table

| Case | Expected |
|---|---|
| Rotate (1,0,0) by +90 degrees about +Y | (0, 0, -1) |
| Rotate (1,0,0) by -90 degrees about +Y (clockwise from above) | (0, 0, 1) |
| Rotate (0,1,0) by +90 degrees about +X | (0, 0, 1) |
| Rotate (1,0,0) by +90 degrees about +Z | (0, 1, 0) |
| v = (1,0,0): `qz90.mul(qx90).rotate(v)` (X applied first) | (0, 1, 0) |
| v = (1,0,0): `qx90.mul(qz90).rotate(v)` (Z applied first) | (0, 0, 1) |
| Pose t = (10,0,0), +90 about +Y, scale 2: `toWorld((1,0,0))` | (10, 0, -2) |
| `Frame.alignY((0,1,0), hint (1,0,0))` | x=(1,0,0) y=(0,1,0) z=(0,0,1) |
| `Frame.alignY((0,0,1), hint (1,0,0))` | x=(1,0,0) y=(0,0,1) z=(0,-1,0) |
| `Frame.alignY((2,2,0), hint (0,1,0))` | x=(-s,s,0) y=(s,s,0) z=(0,0,-1), s = sqrt(1/2) |
| `Frame.alignZ((0,0,1), hint (0,1,0))` | WORLD |
| `Frame.alignZ((1,0,0), hint (0,1,0))` | x=(0,0,-1) y=(0,1,0) z=(1,0,0) |
| `Frame.alignY((0,1,0), hint parallel or antiparallel)` | WORLD (fallback hint +X) |
| `Frame.alignY((1,0,0), hint (1,0,0))` | x=(0,1,0) y=(1,0,0) z=(0,0,-1) (fallback hint +Y) |
| `Frame.fromHeading(forward (0,0,1), up (0,1,0))` | x=(-1,0,0) y=(0,1,0) z=(0,0,-1) |
| `Frame.fromHeading(forward (1,0,0), up (0,1,0))` | x=(0,0,1) y=(0,1,0) z=(-1,0,0) |
| `Frame.fromHeading(forward (0,0,-1), up (0,1,0))` | x=(1,0,0) y=(0,1,0) z=(0,0,1) = WORLD |
| `Frame.fromHeading(forward (-1,0,0), up (0,1,0))` | x=(0,0,-1) y=(0,1,0) z=(1,0,0) |
| `Frame.fromHeading(forward (0,1,0), up (0,1,0))` (looking straight up) | x=(0,0,-1) y=(1,0,0) z=(0,-1,0) |

## 2. Golden CSV files (`src/test/resources/golden/`)

Comment lines start with `#`; the first non-comment line is the header; remaining lines are comma-separated
doubles in Python `repr` form. Integrity: `MANIFEST.sha256`.

| File | Rows | Columns | Meaning |
|---|---|---|---|
| `rotation_cases.csv` | 2,042 | `ax,ay,az,angle,vx,vy,vz,ex,ey,ez` | `axisAngle(axis, angle).rotate(v) = e` |
| `compose_cases.csv` | 1,500 | `a_ax..a_ang, b_ax..b_ang, vx,vy,vz, ex,ey,ez` | `qa.mul(qb).rotate(v) = e` (b first) |
| `pose_cases.csv` | 1,500 | parent `t,axis,angle,scale`, child `t,axis,angle,scale`, `p`, `e` | `parent.then(child).toWorld(p) = e` |
| `pose_large_cases.csv` | 500 | same | parent translations near +/-3e7 |
| `slerp_cases.csv` | 1,500 | `q0(w,x,y,z), q1(w,x,y,z), t, v, e` | action of `slerp(q0,q1,t)` on v is e |

Regenerate with `python3 tools/generate_golden_vectors.py` (seed 20261014). A fresh run can differ in the last
bit on another platform; tests use tolerances, so that is harmless. Do not regenerate to make a failing test pass.
