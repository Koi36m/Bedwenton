import math, random
from fractions import Fraction as F
exec(open('mono.py').read().split("random.seed(3)")[0])
exact = {
 "smoothstep": lambda t: t*t*(3-2*t),
 "smootherstep": lambda t: t*t*t*(t*(6*t-15)+10),
}
for n in (2,3,4,5):
    exact[f"in{n}"]=lambda t,n=n: t**n
    exact[f"out{n}"]=lambda t,n=n: 1-(1-t)**n
    exact[f"inout{n}"]=lambda t,n=n: (2**(n-1))*t**n if t<F(1,2) else 1-(2-2*t)**n/2
impl={"smoothstep":ss_b,"smootherstep":sm_b}
for n in (2,3,4,5): impl[f"in{n}"]=cands[f"in{n}"]; impl[f"out{n}"]=cands[f"out{n}"]; impl[f"inout{n}"]=cands[f"inout{n}"]
impl_direct={"smoothstep":ss_a,"smootherstep":sm_a}
random.seed(4)
pts=[random.random() for _ in range(60000)]+[1-random.random()*1e-6 for _ in range(20000)]+[random.random()*1e-6 for _ in range(20000)]
for name in impl:
    worst=0
    for t in pts:
        e=abs(F(impl[name](t))-exact[name](F(t)))
        if e>worst: worst=e
    extra=""
    if name in impl_direct:
        wd=max(abs(F(impl_direct[name](t))-exact[name](F(t))) for t in pts); extra=f"  (direct: {float(wd)/2**-53:.2f})"
    print(f"{name:14s} max abs error = {float(worst):.3g} = {float(worst)/2**-53:.2f} * 2^-53{extra}")
