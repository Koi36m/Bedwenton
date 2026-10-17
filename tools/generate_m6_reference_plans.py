#!/usr/bin/env python3
"""Independent reference delivery plans for the M6 R1 contract (docs/m6/M6-IMPLEMENTATION-CONTRACT.md).

Python standard library only; written for this project from the contract text, sharing no code with any Java
implementation. Arithmetic is exact (Python integers and fractions.Fraction); there is no floating point.

Each case describes recipes (variants as lists of layers with point counts, styles, reveal and clip duration),
requests (viewers, worlds, priorities, frame ordinals, local ticks) and a policy, and records the expected plan:
per request the allocation rank, outcome, tier, reserved generated points, source, unsupported and kept points,
placements, ordered batches (frame batch index, kept count, phase offset) and recipients; and the plan totals.

The Java test (DeliveryReferenceTest) rebuilds the same scenes with the M5 API (a static ring per layer, identity
motion, a paused playback at the given local ticks) and compares the planner's output field by field.

Usage: python3 tools/generate_m6_reference_plans.py <output directory>
Writes bloom.txt, cloudstep.txt, healer.txt, trident.txt, astral.txt, stress.txt and MANIFEST.sha256.
"""
import hashlib
import os
import sys
from fractions import Fraction as F

TIERS = ["SIMPLE", "BALANCED", "HEAVY"]
PRIOS = ["ACTION", "NORMAL", "AMBIENT"]
ROLES = ["ESSENTIAL", "STANDARD", "DECORATIVE"]


# ------------------------------------------------------------------------------------------------ model

class Layer:
    def __init__(self, name, n, style, reveal="ALL", dur=1_000_000):
        self.name, self.n, self.style, self.reveal, self.dur = name, n, style, reveal, dur


class Variant:
    def __init__(self, tier, layers, lifetime=1_000_000):
        self.tier, self.layers, self.lifetime = tier, layers, lifetime


class Recipe:
    def __init__(self, name, prio, variants, roles=None):
        self.name, self.prio, self.variants, self.roles = name, prio, {v.tier: v for v in variants}, roles or {}

    def role(self, layer):
        return self.roles.get(layer, "STANDARD")


class Request:
    def __init__(self, rid, recipe, tier, world, ordinal, local, viewers, fail=False):
        self.id, self.recipe, self.tier, self.world, self.ordinal = rid, recipe, tier, world, ordinal
        self.local, self.viewers, self.fail = F(local), list(viewers), fail


class Policy:
    FIELDS = ["maxRequests", "maxViewersPerRequest", "maxViewerEntries", "generatedPointBudget",
              "ambientGeneratedReserve", "placementBudget", "ambientPlacementReserve", "worldPlacementBudget",
              "viewerPlacementBudget", "simpleKeptCap", "balancedKeptCap", "heavyKeptCap"]

    def __init__(self, **kw):
        for f in self.FIELDS:
            setattr(self, f, kw[f])
        # contract section 4.3: every fixture policy must be constructible
        assert 1 <= self.maxRequests <= 1 << 16 and 0 <= self.maxViewersPerRequest <= 1 << 16
        assert 0 <= self.maxViewerEntries <= 1 << 24
        for b in (self.generatedPointBudget, self.placementBudget, self.worldPlacementBudget, self.viewerPlacementBudget):
            assert 0 <= b <= 1 << 50
        assert 0 <= self.ambientGeneratedReserve <= self.generatedPointBudget
        assert 0 <= self.ambientPlacementReserve <= self.placementBudget
        assert 0 <= self.simpleKeptCap <= self.balancedKeptCap <= self.heavyKeptCap <= 1 << 20

    def kept_cap(self, tier):
        return [self.simpleKeptCap, self.balancedKeptCap, self.heavyKeptCap][TIERS.index(tier)]


def max_points(variant):
    return sum(l.n for l in variant.layers)


def frame(variant, local):
    """M5 frame of a test scene: every layer is a static ring with a once(dur) clip, ACTIVE_ONLY, at local ticks."""
    if local < 0:
        return ("BEFORE", [])
    if local >= variant.lifetime:
        return ("AFTER", [])
    batches = []
    for idx, l in enumerate(variant.layers):
        if not (0 <= local < l.dur):
            continue
        k = l.n
        if l.reveal == "PREFIX":
            p = local / F(l.dur)
            k = (p * l.n).numerator // (p * l.n).denominator
        if k == 0:
            continue
        batches.append((idx, l.name, l.style, k))
    return ("ACTIVE", batches)


# ------------------------------------------------------------------------------------------------ the contract

def plan(tick, requests, styles, pol):
    groups = []
    for pr in PRIOS:
        g = sorted([q for q in requests if q.recipe.prio == pr], key=lambda q: q.id)
        if g:
            s = tick % len(g)  # Python % is floorMod for a positive modulus
            groups += g[s:] + g[:s]
    order = groups
    res = {}
    used_g = 0
    G, RG = pol.generatedPointBudget, pol.ambientGeneratedReserve
    state = {}
    for rank, q in enumerate(order):
        d = dict(rank=rank, outcome=None, tier=None, reserved=0, source=0, unsupported=0, kept=0, placements=0,
                 batches=[], recipients=[])
        res[q.id] = d
        if not q.viewers:
            d["outcome"] = "SKIPPED_NO_VIEWERS"
            continue
        cands = [t for t in reversed(TIERS[:TIERS.index(q.tier) + 1]) if t in q.recipe.variants]
        if not cands:  # the requested tier is a strict ceiling (contract R2)
            d["outcome"] = "SKIPPED_NO_ELIGIBLE_TIER"
            continue
        avail = G - used_g if q.recipe.prio == "AMBIENT" else max(0, G - RG - used_g)
        deliverable = [t for t in cands if any(l.n > 0 and l.style in styles for l in q.recipe.variants[t].layers)]
        if not deliverable:
            d["outcome"] = "SKIPPED_UNSUPPORTED"
            continue
        chosen = next((t for t in deliverable if max_points(q.recipe.variants[t]) <= avail), None)
        if chosen is None:
            d["outcome"] = "SKIPPED_GENERATION_BUDGET"
            continue
        v = q.recipe.variants[chosen]
        d["tier"], d["reserved"] = chosen, max_points(v)
        used_g += d["reserved"]
        if q.fail:
            d["outcome"] = "FAILED_EVALUATION"
            continue
        stage, batches = frame(v, q.local)
        d["source"] = sum(b[3] for b in batches)
        if stage != "ACTIVE" or not batches:
            d["outcome"] = "SKIPPED_INACTIVE"
            continue
        state[q.id] = batches
    P, RP = pol.placementBudget, pol.ambientPlacementReserve
    used_p, world, viewer = 0, {}, {}
    for q in order:
        d = res[q.id]
        if d["outcome"] is not None:
            continue
        batches = state[q.id]
        sup = [(pos, b) for pos, b in enumerate(batches) if b[2] in styles]
        d["unsupported"] = sum(b[3] for b in batches if b[2] not in styles)
        N = sum(b[3] for _, b in sup)
        if N == 0:
            d["outcome"] = "SKIPPED_UNSUPPORTED"
            continue
        availp = P - used_p if q.recipe.prio == "AMBIENT" else max(0, P - RP - used_p)
        w = world.get(q.world, pol.worldPlacementBudget)
        B = min(availp, w)
        vrem = [viewer.get(v, pol.viewerPlacementBudget) for v in q.viewers]
        kmax = min(N, pol.kept_cap(d["tier"]), max(vrem))
        cost = lambda k: sum(min(k, x) for x in vrem)
        k = 0
        for cand in range(kmax, -1, -1):  # linear scan from the top: a different method from binary search
            if cost(cand) <= B:
                k = cand
                break
        if k == 0:
            d["outcome"] = "SKIPPED_DELIVERY_BUDGET"
            continue
        sup.sort(key=lambda e: (ROLES.index(q.recipe.role(e[1][1])), e[1][0]))
        kept = [0] * len(sup)
        r = k
        i = 0
        while i < len(sup) and r > 0:
            role = q.recipe.role(sup[i][1][1])
            j = i
            while j < len(sup) and q.recipe.role(sup[j][1][1]) == role:
                j += 1
            T = sum(sup[x][1][3] for x in range(i, j))
            if T <= r:
                for x in range(i, j):
                    kept[x] = sup[x][1][3]
                r -= T
            else:
                rems = []
                for x in range(i, j):
                    nb = sup[x][1][3]
                    kept[x] = r * nb // T
                    rems.append((-(r * nb % T), x))
                left = r - sum(kept[i:j])
                for _, x in sorted(rems)[:left]:
                    kept[x] += 1
                r = 0
            i = j
        out = []
        for x, (pos, b) in enumerate(sup):
            m = kept[x]
            if m == 0:
                continue
            n = b[3]
            g = -(-n // m)
            out.append((pos, m, q.ordinal % g, n))
        d["batches"] = out
        placed = 0
        for v, x in zip(q.viewers, vrem):
            c = min(k, x)
            if c > 0:
                d["recipients"].append((v, c))
                viewer[v] = x - c
                placed += c
        world[q.world] = w - placed
        used_p += placed
        d["kept"], d["placements"] = k, placed
        everyone = all(c == k for _, c in d["recipients"]) and len(d["recipients"]) == len(q.viewers)
        d["outcome"] = "DELIVERED" if k == N and d["unsupported"] == 0 and everyone else "THINNED"
    return res


# ------------------------------------------------------------------------------------------------ cases

def policy(**over):
    base = dict(maxRequests=256, maxViewersPerRequest=1024, maxViewerEntries=65536, generatedPointBudget=10_000,
                ambientGeneratedReserve=0, placementBudget=100_000, ambientPlacementReserve=0,
                worldPlacementBudget=100_000, viewerPlacementBudget=100_000, simpleKeptCap=64, balancedKeptCap=256,
                heavyKeptCap=1024)
    base.update(over)
    return Policy(**base)


def ring_recipe(name, prio, sizes, style, roles=None, extra=None):
    """Three tiers of a single-layer (or multi-layer) recipe; sizes = (simple, balanced, heavy)."""
    vs = []
    for tier, n in zip(TIERS, sizes):
        if n is None:
            continue
        layers = [Layer("main", n, style)] + [Layer(nm, sz[TIERS.index(tier)], st) for nm, sz, st in (extra or [])]
        vs.append(Variant(tier, layers))
    return Recipe(name, prio, vs, roles)


def bloom():
    """Many small ambient flowers near moving players: low-priority bursts, reserve and rotation."""
    flower = ring_recipe("bloom-flower", "AMBIENT", (6, 12, 24), "bloom:petal")
    action = ring_recipe("bloom-strike", "ACTION", (16, 32, 64), "bloom:burst")
    cases = []
    for tick in (0, 1, 2, 3, 7):
        reqs = [Request(100 + i, flower, "BALANCED", 0, i, 5, [i % 4, 4 + i % 3]) for i in range(8)]
        reqs.append(Request(1, action, "HEAVY", 0, tick, 5, [0, 1, 2, 3]))
        cases.append(("bloom-reserve-t%d" % tick, tick, reqs, {"bloom:petal", "bloom:burst"},
                      policy(generatedPointBudget=200, ambientGeneratedReserve=48, placementBudget=300,
                             ambientPlacementReserve=60, viewerPlacementBudget=80)))
    reqs = [Request(100 + i, flower, "HEAVY", 0, 0, 5, [i]) for i in range(12)]
    cases.append(("bloom-burst-tiers", 4, reqs, {"bloom:petal"}, policy(generatedPointBudget=170)))
    return cases


def cloudstep():
    """Walking and sprinting clouds: overlapping activations, per-viewer limits, worlds."""
    cloud = Recipe("cloudstep", "NORMAL", [
        Variant("SIMPLE", [Layer("puff", 8, "cloud:puff")]),
        Variant("BALANCED", [Layer("puff", 20, "cloud:puff"), Layer("wisp", 12, "cloud:wisp")]),
        Variant("HEAVY", [Layer("puff", 40, "cloud:puff"), Layer("wisp", 30, "cloud:wisp")])],
        {"puff": "ESSENTIAL", "wisp": "DECORATIVE"})
    cases = []
    reqs = [Request(10 + i, cloud, "HEAVY", i % 2, 3 * i, 2, [0, 1, 2, 3, 4, 5][: 2 + i % 5]) for i in range(10)]
    cases.append(("cloudstep-overlap", 11, reqs, {"cloud:puff", "cloud:wisp"},
                  policy(viewerPlacementBudget=90, worldPlacementBudget=500, simpleKeptCap=50, balancedKeptCap=50, heavyKeptCap=50)))
    cases.append(("cloudstep-no-wisp-support", 11, reqs, {"cloud:puff"},
                  policy(viewerPlacementBudget=90, worldPlacementBudget=500, simpleKeptCap=50, balancedKeptCap=50, heavyKeptCap=50)))
    reqs2 = [Request(5, cloud, "BALANCED", 0, 0, 2, [7]), Request(6, cloud, "BALANCED", 0, 1, 2, [7]),
             Request(7, cloud, "BALANCED", 1, 2, 2, [7])]
    cases.append(("cloudstep-one-viewer-many-effects", 0, reqs2, {"cloud:puff", "cloud:wisp"},
                  policy(viewerPlacementBudget=45)))
    return cases


def healer():
    """Three-pulse healer: preserve pulse markers (essential) while thinning decorative sparkles."""
    heal = Recipe("healer", "ACTION", [
        Variant("BALANCED", [Layer("pulse-0", 24, "heal:ring"), Layer("pulse-1", 24, "heal:ring"),
                             Layer("pulse-2", 24, "heal:ring"), Layer("marker", 3, "heal:marker"),
                             Layer("sparkle", 60, "heal:sparkle")], lifetime=60)],
        {"marker": "ESSENTIAL", "sparkle": "DECORATIVE"})
    cases = []
    for cap in (3, 10, 75, 80, 135, 200):
        reqs = [Request(1, heal, "BALANCED", 0, cap, 10, [1, 2, 3])]
        cases.append(("healer-cap-%d" % cap, 0, reqs, {"heal:ring", "heal:marker", "heal:sparkle"},
                      policy(simpleKeptCap=min(cap, 64), balancedKeptCap=cap)))
    reqs = [Request(1, heal, "BALANCED", 0, 5, 60, [1])]
    cases.append(("healer-ended", 0, reqs, {"heal:ring", "heal:marker", "heal:sparkle"}, policy()))
    reqs = [Request(1, heal, "HEAVY", 0, 5, 10, [1, 2])]
    cases.append(("healer-placement-limited", 0, reqs, {"heal:ring", "heal:marker", "heal:sparkle"},
                  policy(placementBudget=40)))
    return cases


def trident():
    """Moving trident helix: viewer counts vary, world switch, self-drawing helix (PREFIX)."""
    helix = Recipe("trident-helix", "NORMAL", [
        Variant("SIMPLE", [Layer("helix", 18, "trident:water", "PREFIX", 30)], lifetime=30),
        Variant("BALANCED", [Layer("helix", 37, "trident:water", "PREFIX", 30)], lifetime=30),
        Variant("HEAVY", [Layer("helix", 73, "trident:water", "PREFIX", 30)], lifetime=30)])
    cases = []
    for t, viewers, world in ((1, [], 0), (7, [3], 0), (15, list(range(40)), 0), (22, list(range(5)), 1),
                              (29, list(range(300)), 1), (30, [1, 2], 1)):
        reqs = [Request(9, helix, "HEAVY", world, t, t, viewers)]
        cases.append(("trident-t%d" % t, t, reqs, {"trident:water"},
                      policy(maxViewersPerRequest=1024, placementBudget=2000, worldPlacementBudget=1500)))
    return cases


def astral():
    """Astral Prism: essential nodes, standard connections, decorative trails; trails degrade first."""
    prism = Recipe("astral-prism", "ACTION", [
        Variant("SIMPLE", [Layer("stars", 5, "astral:star"), Layer("links", 20, "astral:link")]),
        Variant("BALANCED", [Layer("stars", 5, "astral:star"), Layer("links", 45, "astral:link"),
                             Layer("trail-a", 40, "astral:trail"), Layer("trail-b", 40, "astral:trail")]),
        Variant("HEAVY", [Layer("stars", 5, "astral:star"), Layer("links", 90, "astral:link"),
                          Layer("trail-a", 120, "astral:trail"), Layer("trail-b", 120, "astral:trail")])],
        {"stars": "ESSENTIAL", "trail-a": "DECORATIVE", "trail-b": "DECORATIVE"})
    cases = []
    for gen, cap, ordinal in ((400, 1024, 0), (400, 200, 1), (400, 60, 2), (130, 1024, 3), (40, 1024, 4),
                              (24, 1024, 5), (335, 100, 17)):
        reqs = [Request(3, prism, "HEAVY", 0, ordinal, 1, [1, 2])]
        cases.append(("astral-gen%d-cap%d" % (gen, cap), 0, reqs, {"astral:star", "astral:link", "astral:trail"},
                      policy(generatedPointBudget=gen, heavyKeptCap=cap, balancedKeptCap=min(cap, 256),
                             simpleKeptCap=min(cap, 64))))
    reqs = [Request(3, prism, "HEAVY", 0, 0, 1, [1])]
    cases.append(("astral-unsupported-trails", 0, reqs, {"astral:star", "astral:link"}, policy()))
    cases.append(("astral-nothing-supported", 0, reqs, {"other:style"}, policy()))
    return cases


def stress():
    """Zero/one/many viewers, simultaneous effects, exact boundaries, huge values, evaluation failures."""
    small = ring_recipe("dot", "NORMAL", (1, 2, 4), "stress:dot")
    big = Recipe("big", "AMBIENT", [Variant("HEAVY", [Layer("all", 1 << 20, "stress:dot")])])
    cases = []
    reqs = [Request(i, small, "HEAVY", i % 3, i, 1, list(range(i % 4))) for i in range(30)]
    cases.append(("stress-many", 5, reqs, {"stress:dot"},
                  policy(placementBudget=97, worldPlacementBudget=40, viewerPlacementBudget=9)))
    # exact boundaries: budget equal to the demand, and one below
    reqs = [Request(1, small, "HEAVY", 0, 0, 1, [1, 2, 3])]
    cases.append(("stress-exact-budget", 0, reqs, {"stress:dot"}, policy(placementBudget=12, generatedPointBudget=4)))
    cases.append(("stress-one-below", 0, reqs, {"stress:dot"}, policy(placementBudget=11, generatedPointBudget=3)))
    # the full M5 frame ceiling: 2^20 points, admitted only with a generation budget of at least 2^20
    reqs = [Request(1, big, "HEAVY", 0, 0, 1, [1, 2])]
    cases.append(("stress-full-frame-denied", 0, reqs, {"stress:dot"},
                  policy(generatedPointBudget=(1 << 20) - 1, ambientGeneratedReserve=(1 << 20) - 1)))
    cases.append(("stress-full-frame-admitted", 0, reqs, {"stress:dot"},
                  policy(generatedPointBudget=1 << 20, heavyKeptCap=1 << 20, balancedKeptCap=1024, placementBudget=1 << 50,
                         worldPlacementBudget=1 << 50, viewerPlacementBudget=1 << 50)))
    # huge tick ordinals and ids, negative ordinals
    reqs = [Request((1 << 62) + i, small, "HEAVY", 0, -(1 << 62) - i, 1, [i]) for i in range(5)]
    cases.append(("stress-huge-ids", -(1 << 63), reqs, {"stress:dot"}, policy(placementBudget=7)))
    # evaluation failure isolated; zero viewers never evaluated
    reqs = [Request(1, small, "HEAVY", 0, 0, 1, [1], fail=True), Request(2, small, "HEAVY", 0, 0, 1, []),
            Request(3, small, "HEAVY", 0, 0, 1, [1])]
    cases.append(("stress-failure-isolated", 0, reqs, {"stress:dot"}, policy()))
    # zero budgets everywhere
    cases.append(("stress-zero-budgets", 0, reqs[2:], {"stress:dot"},
                  policy(generatedPointBudget=0, placementBudget=0, worldPlacementBudget=0, viewerPlacementBudget=0)))
    # R2: the requested tier is a strict ceiling; no tier at or below it -> SKIPPED_NO_ELIGIBLE_TIER, never evaluated
    upper = ring_recipe("upper", "NORMAL", (None, 5, 9), "stress:dot")
    reqs = [Request(1, upper, "SIMPLE", 0, 0, 1, [1], fail=True), Request(2, upper, "BALANCED", 0, 0, 1, [1]),
            Request(3, upper, "HEAVY", 0, 0, 1, [1])]
    cases.append(("stress-no-eligible-tier", 0, reqs, {"stress:dot"}, policy()))
    # R2: G10 counterexamples (capacity, not allocation). Generation reservations are indivisible.
    act70 = ring_recipe("act70", "ACTION", (None, None, 70), "stress:dot")
    amb20 = ring_recipe("amb20", "AMBIENT", (None, None, 20), "stress:dot")
    amb31 = ring_recipe("amb31", "AMBIENT", (None, None, 31), "stress:dot")
    reqs = [Request(1, act70, "HEAVY", 0, 0, 1, [1]), Request(2, amb20, "HEAVY", 0, 0, 1, [2]),
            Request(3, amb20, "HEAVY", 0, 0, 1, [3])]
    cases.append(("stress-reserve-generation-indivisible", 0, reqs, {"stress:dot"},
                  policy(generatedPointBudget=100, ambientGeneratedReserve=30)))
    reqs = [Request(1, act70, "HEAVY", 0, 0, 1, [1]), Request(2, amb31, "HEAVY", 0, 0, 1, [2])]
    cases.append(("stress-reserve-generation-too-large", 0, reqs, {"stress:dot"},
                  policy(generatedPointBudget=100, ambientGeneratedReserve=30)))
    # Placement cost is per viewer: 3 viewers get k = 1 (3 placements < 5); 6 viewers get k = 0.
    act95 = ring_recipe("act95", "ACTION", (None, None, 95), "stress:dot")
    amb10 = ring_recipe("amb10", "AMBIENT", (None, None, 10), "stress:dot")
    for viewers in (3, 6):
        reqs = [Request(1, act95, "HEAVY", 0, 0, 1, [100]), Request(2, amb10, "HEAVY", 0, 0, 1, list(range(viewers)))]
        cases.append(("stress-reserve-placement-%d-viewers" % viewers, 0, reqs, {"stress:dot"},
                      policy(placementBudget=100, ambientPlacementReserve=5)))
    return cases


# ------------------------------------------------------------------------------------------------ output

def write(path, family, cases):
    lines = ["# M6 R1 independent reference plans; generator tools/generate_m6_reference_plans.py (Python standard library)",
             "# family " + family]
    for name, tick, reqs, styles, pol in cases:
        res = plan(tick, reqs, styles, pol)
        lines.append("case %s tick=%d" % (name, tick))
        lines.append("policy " + " ".join(str(getattr(pol, f)) for f in Policy.FIELDS))
        lines.append("styles " + ";".join(sorted(styles)))
        recipes = []
        for q in reqs:
            if q.recipe not in recipes:
                recipes.append(q.recipe)
        for rc in recipes:
            lines.append("recipe %s %s roles=%s" % (rc.name, rc.prio, ";".join("%s:%s" % kv for kv in sorted(rc.roles.items())) or "-"))
            for t in TIERS:
                if t in rc.variants:
                    v = rc.variants[t]
                    lines.append("variant %s %s lifetime=%d layers=%s" % (rc.name, t, v.lifetime, ";".join(
                        "%s:%d:%s:%s:%d" % (l.name, l.n, l.style, l.reveal, l.dur) for l in v.layers)))
        for q in reqs:
            lines.append("request %d recipe=%s tier=%s world=%d ordinal=%d local=%s viewers=%s fail=%d" % (
                q.id, q.recipe.name, q.tier, q.world, q.ordinal, q.local, ";".join(map(str, q.viewers)) or "-", int(q.fail)))
        tot = dict(reserved=0, source=0, kept=0, recipients=0, placements=0, unsupported=0)
        counts = {}
        for q in reqs:
            d = res[q.id]
            counts[d["outcome"]] = counts.get(d["outcome"], 0) + 1
            for key in ("reserved", "source", "kept", "placements", "unsupported"):
                tot[key] += d[key]
            tot["recipients"] += len(d["recipients"])
            lines.append("expect %d rank=%d outcome=%s tier=%s reserved=%d source=%d unsupported=%d kept=%d placements=%d batches=%s recipients=%s" % (
                q.id, d["rank"], d["outcome"], d["tier"] or "-", d["reserved"], d["source"], d["unsupported"], d["kept"],
                d["placements"], ";".join("%d:%d:%d:%d" % b for b in d["batches"]) or "-",
                ";".join("%d:%d" % rv for rv in d["recipients"]) or "-"))
        lines.append("totals " + " ".join("%s=%d" % (k, tot[k]) for k in ("reserved", "source", "kept", "recipients", "placements", "unsupported"))
                     + " counts=" + ";".join("%s:%d" % kv for kv in sorted(counts.items())))
        lines.append("end")
    with open(path, "w", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    return len(cases)


def main(out):
    os.makedirs(out, exist_ok=True)
    fams = [("bloom", bloom()), ("cloudstep", cloudstep()), ("healer", healer()), ("trident", trident()),
            ("astral", astral()), ("stress", stress())]
    names = []
    for fam, cases in fams:
        n = write(os.path.join(out, fam + ".txt"), fam, cases)
        names.append(fam + ".txt")
        print(fam, n, "cases")
    with open(os.path.join(out, "MANIFEST.sha256"), "w", newline="\n") as m:
        for name in sorted(names):
            with open(os.path.join(out, name), "rb") as f:
                m.write("%s  %s\n" % (hashlib.sha256(f.read()).hexdigest(), name))


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "src/test/resources/m6/reference")
