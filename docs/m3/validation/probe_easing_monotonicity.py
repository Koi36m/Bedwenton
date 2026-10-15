import math, random
nu = math.nextafter
def ss_a(t): return t*t*(3.0-2.0*t)
def ss_b(t):
    if t <= 0.5: return t*t*(3.0-2.0*t)
    s=1.0-t; return 1.0-s*s*(3.0-2.0*s)
def sm_a(t): return t*t*t*(t*(t*6.0-15.0)+10.0)
def sm_b(t):
    if t <= 0.5: return t*t*t*(t*(t*6.0-15.0)+10.0)
    s=1.0-t; return 1.0-s*s*s*(s*(s*6.0-15.0)+10.0)
def pin(n): return lambda t: t**0 * _p(t,n)
def _p(t,n):
    r=t
    for _ in range(n-1): r=r*t
    return r
def pout(n): return lambda t: 1.0-_p(1.0-t,n)
def pinout(n):
    def f(t):
        if t < 0.5: return float(2**(n-1))*_p(t,n)
        return 1.0-_p(2.0-2.0*t,n)/2.0
    return f
cands={"smoothstep_direct":ss_a,"smoothstep_split":ss_b,"smootherstep_direct":sm_a,"smootherstep_split":sm_b}
for n in (2,3,4,5): cands[f"in{n}"]=lambda t,n=n:_p(t,n); cands[f"out{n}"]=pout(n); cands[f"inout{n}"]=pinout(n)
random.seed(3)
starts=[0.0, 5e-324, 1e-300, 1e-160, 1e-20, 1e-8, 0.001, 0.25, 0.5, math.nextafter(0.5,0)-1e-13, 0.75, 0.999, 1-1e-8, 1-1e-12]+[random.random() for _ in range(300)]
for name,f in cands.items():
    viol=0; worst=0; rng=0; endpoints=(f(0.0),f(1.0),f(0.5))
    for s in starts:
        x=s; prev=f(x)
        for i in range(4000):
            x=nu(x,2.0)
            if x>1.0: break
            y=f(x)
            if y<prev: viol+=1; worst=max(worst,prev-y)
            if y<0 or y>1: rng+=1
            prev=y
    # also descending window ending at 1
    x=1.0; prev=f(x)
    for i in range(20000):
        x=nu(x,0.0); y=f(x)
        if y>prev: viol+=1; worst=max(worst,y-prev)
        prev=y
    print(f"{name:22s} violations={viol:5d} worst={worst:.3g} out_of_range={rng} f(0),f(1),f(.5)={endpoints}")
