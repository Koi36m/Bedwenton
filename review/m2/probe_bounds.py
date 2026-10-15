import math, random
from fractions import Fraction as F
random.seed(5)
# A1: single re-anchoring error ratio err / (2^-52 * M), M = max(|anchor|, |result|), COSMETIC vs GAME
def worst(domain, N=400000):
    best=(0,None)
    for _ in range(N):
        A = random.uniform(-1,1)*10**random.randint(0,6)
        rate = random.uniform(-3,3)
        if domain=="GAME":
            delta=random.randint(1,10**7); dt=float(delta); exact_dt=F(delta)
        else:
            delta=random.randint(1,10**15); dt=float(delta)/50_000_000.0; exact_dt=F(delta,50_000_000)
        res = A + rate*dt
        exact = F(A)+F(rate)*exact_dt
        M=max(abs(A),abs(res),1e-300)
        ratio=float(abs(F(res)-exact)/(F(2)**-52*F(M)))
        if ratio>best[0]: best=(ratio,(A,rate,delta))
    return best
for d in ("GAME","COSMETIC"): print("A1", d, "worst err/(2^-52 M) =", worst(d))
# cycle exactness beyond 2^51: first failures of rint((u-r)/d) vs exact floor
def cyc(u,d):
    r=math.fmod(u,d); return float(round((u-r)/d))
fails={}
for k in (50,51,52,53):
    cnt=0
    for _ in range(200000):
        d=random.uniform(0.01,100); n=random.randint(2**k,2**(k+1)-1)
        u=float(F(n)*F(d))  # rounded
        if u-0>=0 and int(cyc(u,d))!=math.floor(F(u)/F(d)): cnt+=1
    fails[k]=cnt
print("cycle mismatches for u/d in [2^k,2^(k+1)):", fails)
