package workshop.vfx.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Quat;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.curve.Spiral;
import workshop.vfx.math.sample.Endpoints;
import workshop.vfx.math.sample.SampleLimits;
import workshop.vfx.math.sample.Sampler;
import workshop.vfx.math.time.Playback;
import workshop.vfx.math.time.TimeDomain;
import workshop.vfx.math.time.TimePoint;
import workshop.vfx.math.time.Timing;
import workshop.vfx.math.time.TimingSample;
import workshop.vfx.scene.EffectFrame;
import workshop.vfx.scene.EffectScene;
import workshop.vfx.scene.FrameBatch;
import workshop.vfx.scene.Layer;
import workshop.vfx.scene.Motion;
import workshop.vfx.scene.Reveal;
import workshop.vfx.scene.SceneLimits;
import workshop.vfx.scene.Shape;
import workshop.vfx.scene.StyleKey;
import workshop.vfx.scene.Visibility;

/**
 * Shared helpers for the M6 tests: test scenes built only through the accepted M5 API (every layer a static ring with a
 * once(duration) clip, ACTIVE_ONLY, identity motion), requests at a paused playback, and an independent invariant checker
 * (M6-TEST-SPECIFICATION.md section 3) that recomputes every budget from the reported plan alone.
 */
final class M6TestSupport {
    static final SampleLimits SAMPLE_LIMITS = new SampleLimits(1 << 20, 1 << 20);
    static final SceneLimits LIMITS = new SceneLimits(4096, 1 << 20);
    static final TimePoint NOW = TimePoint.gameTick(0);
    /** Overflows on any shape point with x > 0 (M5 throws ArithmeticException for the non-finite position). */
    static final Pose FAILING_ANCHOR = new Pose(new Vec3(Double.MAX_VALUE, 0, 0), Quat.IDENTITY, 1e300);
    private static final Map<Integer, Shape> RINGS = new ConcurrentHashMap<>();

    private M6TestSupport() { }

    // ------------------------------------------------------------------ scenes, recipes, requests, policies

    static Shape ring(int n) {
        if (n == 0) {
            return Shape.points(List.of());
        }
        return RINGS.computeIfAbsent(n, k -> Shape.of(Sampler.uniformParameter(Spiral.circle(1), k, Endpoints.EXCLUDE_END, SAMPLE_LIMITS)));
    }

    static Layer layer(String name, int n, String style, Reveal reveal, double duration) {
        return new Layer(name, Timing.once(duration), Visibility.ACTIVE_ONLY, reveal, Motion.IDENTITY, ring(n), StyleKey.of(style));
    }

    static Layer layer(String name, int n, String style) {
        return layer(name, n, style, Reveal.ALL, 1_000_000);
    }

    static EffectScene scene(double lifetime, Layer... layers) {
        return EffectScene.of(TimeDomain.GAME, lifetime, List.of(layers), LIMITS);
    }

    static EffectScene scene(Layer... layers) {
        return scene(1_000_000, layers);
    }

    /** One single-layer ring per given size, as SIMPLE, BALANCED, HEAVY (null skips the tier). */
    static EffectRecipe tiers(String name, EffectPriority priority, String style, Integer simple, Integer balanced, Integer heavy) {
        Map<QualityTier, EffectScene> v = new EnumMap<>(QualityTier.class);
        Integer[] sizes = {simple, balanced, heavy};
        for (QualityTier t : QualityTier.values()) {
            if (sizes[t.ordinal()] != null) {
                v.put(t, scene(layer("main", sizes[t.ordinal()], style)));
            }
        }
        return EffectRecipe.of(name, priority, v, Map.of());
    }

    static EffectRecipe single(String name, EffectPriority priority, EffectScene scene) {
        return EffectRecipe.of(name, priority, Map.of(QualityTier.HEAVY, scene), Map.of());
    }

    static Playback pausedAt(double local) {
        return new Playback(NOW, local, 1, true);
    }

    static DeliveryRequest request(long id, EffectRecipe recipe, QualityTier tier, int world, long ordinal, double local, long... viewers) {
        return DeliveryRequest.of(id, recipe, tier, world, Pose.IDENTITY, pausedAt(local), NOW, ordinal, viewers);
    }

    static DeliveryRequest failing(long id, EffectRecipe recipe, QualityTier tier, int world, long ordinal, double local, long... viewers) {
        return DeliveryRequest.of(id, recipe, tier, world, FAILING_ANCHOR, pausedAt(local), NOW, ordinal, viewers);
    }

    static DeliveryPolicy policy(long generated, long placements, long world, long viewer, int cap) {
        return new DeliveryPolicy(4096, 4096, 1 << 20, generated, 0, placements, 0, world, viewer, cap, cap, cap);
    }

    static DeliveryPolicy roomy() {
        return policy(1 << 30, 1L << 40, 1L << 40, 1L << 40, 1 << 20);
    }

    static Set<StyleKey> styles(String... values) {
        Set<StyleKey> s = new HashSet<>();
        for (String v : values) {
            s.add(StyleKey.of(v));
        }
        return s;
    }

    // ------------------------------------------------------------------ random scenarios

    record Scenario(long tick, List<DeliveryRequest> requests, Set<StyleKey> styles, DeliveryPolicy policy) {
        DeliveryPlan plan() {
            return DeliveryPlanner.plan(tick, requests, styles, policy);
        }
    }

    private static final String[] STYLE_POOL = {"t:a", "t:b", "t:c", "t:d"};

    /**
     * A random scenario with budgets small enough to bind: 1 to 6 recipes of 1 to 3 tiers and 1 to 5 layers (sizes 0 to
     * 40, random roles, styles, PREFIX clips and lifetimes), 0 to 40 requests over 3 worlds and a pool of 12 viewers,
     * some with a failing anchor, and a random valid policy.
     */
    static Scenario randomScenario(java.util.Random rnd) {
        int recipes = 1 + rnd.nextInt(6);
        List<EffectRecipe> rs = new ArrayList<>();
        for (int r = 0; r < recipes; r++) {
            Map<QualityTier, EffectScene> v = new EnumMap<>(QualityTier.class);
            Map<String, LayerRole> roles = new HashMap<>();
            for (QualityTier t : QualityTier.values()) {
                if ((v.isEmpty() && t == QualityTier.HEAVY) || rnd.nextInt(3) > 0) {
                    int layers = 1 + rnd.nextInt(5);
                    Layer[] ls = new Layer[layers];
                    for (int i = 0; i < layers; i++) {
                        Reveal reveal = rnd.nextInt(4) == 0 ? Reveal.PREFIX : Reveal.ALL;
                        ls[i] = layer("l" + i, rnd.nextInt(41), STYLE_POOL[rnd.nextInt(STYLE_POOL.length)], reveal, 1 + rnd.nextInt(20));
                        if (rnd.nextInt(3) == 0) {
                            roles.put("l" + i, LayerRole.values()[rnd.nextInt(3)]);
                        }
                    }
                    v.put(t, scene(1 + rnd.nextInt(25), ls));
                }
            }
            rs.add(EffectRecipe.of("r" + r, EffectPriority.values()[rnd.nextInt(3)], v, roles));
        }
        int n = rnd.nextInt(41);
        List<DeliveryRequest> reqs = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        while (reqs.size() < n) {
            long id = rnd.nextInt(4) == 0 ? rnd.nextLong() : rnd.nextInt(100);
            if (!ids.add(id)) {
                continue;
            }
            List<Long> vs = new ArrayList<>();
            int pick = rnd.nextInt(6) == 0 ? 0 : rnd.nextInt(7);
            for (long x = 0; x < 12 && vs.size() < pick; x++) {
                if (rnd.nextInt(3) == 0) {
                    vs.add(x);
                }
            }
            long[] viewers = vs.stream().mapToLong(Long::longValue).toArray();
            EffectRecipe recipe = rs.get(rnd.nextInt(rs.size()));
            QualityTier tier = QualityTier.values()[rnd.nextInt(3)];
            double local = rnd.nextInt(10) == 0 ? -1 : rnd.nextInt(26) + (rnd.nextBoolean() ? 0.5 : 0);
            long ordinal = rnd.nextInt(5) == 0 ? rnd.nextLong() : rnd.nextInt(50) - 10;
            int world = rnd.nextInt(3);
            reqs.add(rnd.nextInt(15) == 0 ? failing(id, recipe, tier, world, ordinal, local, viewers)
                    : request(id, recipe, tier, world, ordinal, local, viewers));
        }
        Set<StyleKey> st = new HashSet<>();
        for (String s : STYLE_POOL) {
            if (rnd.nextInt(4) > 0) {
                st.add(StyleKey.of(s));
            }
        }
        long g = rnd.nextInt(400);
        long p = rnd.nextInt(600);
        int c1 = rnd.nextInt(30), c2 = c1 + rnd.nextInt(40), c3 = c2 + rnd.nextInt(80);
        DeliveryPolicy pol = new DeliveryPolicy(64, 16, 512, g, rnd.nextBoolean() ? 0 : rnd.nextLong(g + 1), p,
                rnd.nextBoolean() ? 0 : rnd.nextLong(p + 1), rnd.nextInt(400), rnd.nextInt(120), c1, c2, c3);
        long tick = rnd.nextInt(4) == 0 ? rnd.nextLong() : rnd.nextInt(20) - 5;
        return new Scenario(tick, reqs, st, pol);
    }

    /** Every reported field of one delivery as text. */
    static String line(EffectDelivery d) {
        StringBuilder sb = new StringBuilder();
        sb.append(d.requestId()).append(' ').append(d.allocationRank()).append(' ').append(d.outcome()).append(' ').append(d.tier())
                .append(' ').append(d.reservedGeneratedPoints()).append(' ').append(d.sourcePoints()).append(' ').append(d.unsupportedPoints())
                .append(' ').append(d.keptPoints()).append(' ').append(d.placements()).append(" [");
        for (int pos = 0; pos < d.orderedBatchCount(); pos++) {
            sb.append(d.orderedBatch(pos)).append(':').append(d.kept(pos)).append(':');
            for (int j = 0; j < d.kept(pos); j++) {
                sb.append(d.selectedIndex(pos, j)).append(',');
            }
            sb.append(';');
        }
        sb.append("] {");
        for (int r = 0; r < d.recipientCount(); r++) {
            sb.append(d.recipient(r)).append(':').append(d.recipientPoints(r)).append(';');
        }
        return sb.append("}\n").toString();
    }

    /** A textual fingerprint of every reported field of a plan, for determinism and mutation comparisons. */
    static String fingerprint(DeliveryPlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append(plan.tickOrdinal()).append('|').append(plan.reservedGeneratedPoints()).append('|').append(plan.sourcePoints()).append('|')
                .append(plan.keptPoints()).append('|').append(plan.recipients()).append('|').append(plan.placements()).append('|')
                .append(plan.unsupportedPoints()).append('\n');
        for (EffectDelivery d : plan.deliveries()) {
            sb.append(line(d));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ independent invariant checks

    /**
     * Recomputes every guarantee that can be stated from the inputs and the reported plan (G1 to G5, G7, G9, G10 and
     * the totals), replaying the reported recipients in allocation-rank order to rebuild the remaining budgets. It
     * shares no code with the planner.
     */
    static void checkInvariants(List<DeliveryRequest> requests, Set<StyleKey> styles, DeliveryPolicy policy, DeliveryPlan plan) {
        List<EffectDelivery> ds = plan.deliveries();
        assertEquals(requests.size(), ds.size(), "one delivery per request");
        int n = requests.size();
        DeliveryRequest[] byRank = new DeliveryRequest[n];
        EffectDelivery[] dByRank = new EffectDelivery[n];
        for (int i = 0; i < n; i++) {
            EffectDelivery d = ds.get(i);
            DeliveryRequest q = requests.get(i);
            assertEquals(q.id(), d.requestId(), "deliveries in input order");
            assertTrue(d.allocationRank() >= 0 && d.allocationRank() < n);
            assertNull(byRank[d.allocationRank()], "allocation ranks are a permutation");
            byRank[d.allocationRank()] = q;
            dByRank[d.allocationRank()] = d;
        }
        // section 5: priority groups in order, ids ascending up to rotation
        for (int r = 1; r < n; r++) {
            assertTrue(byRank[r - 1].recipe().priority().ordinal() <= byRank[r].recipe().priority().ordinal(), "priority order");
        }
        int groupStart = 0;
        for (EffectPriority pr : EffectPriority.values()) {
            List<Long> group = new ArrayList<>();
            for (DeliveryRequest q : requests) {
                if (q.recipe().priority() == pr) {
                    group.add(q.id());
                }
            }
            group.sort(null);
            int m = group.size();
            for (int j = 0; j < m; j++) {
                long expectedId = group.get((int) ((Math.floorMod(plan.tickOrdinal(), (long) m) + j) % m));
                assertEquals(expectedId, byRank[groupStart + j].id(), "rotation within " + pr);
            }
            groupStart += m;
        }
        long g = policy.generatedPointBudget(), rg = policy.ambientGeneratedReserve();
        long p = policy.placementBudget(), rp = policy.ambientPlacementReserve();
        long usedG = 0, usedGNonAmbient = 0, usedP = 0, usedPNonAmbient = 0;
        Map<Integer, Long> world = new HashMap<>();
        Map<Long, Long> viewer = new HashMap<>();
        Map<Outcome, Integer> counts = new EnumMap<>(Outcome.class);
        long tReserved = 0, tSource = 0, tKept = 0, tRecipients = 0, tPlacements = 0, tUnsupported = 0;
        for (int r = 0; r < n; r++) {
            DeliveryRequest q = byRank[r];
            EffectDelivery d = dByRank[r];
            boolean ambient = q.recipe().priority() == EffectPriority.AMBIENT;
            if (ambient && (r == 0 || byRank[r - 1].recipe().priority() != EffectPriority.AMBIENT)) {
                // G10: when the AMBIENT class starts, its reserved capacity is still unspent
                assertTrue(g - usedG >= rg, "G10: ambient generation reserve available");
                assertTrue(p - usedP >= rp, "G10: ambient placement reserve available");
            }
            counts.merge(d.outcome(), 1, Integer::sum);
            tReserved += d.reservedGeneratedPoints();
            tSource += d.sourcePoints();
            tKept += d.keptPoints();
            tRecipients += d.recipientCount();
            tPlacements += d.placements();
            tUnsupported += d.unsupportedPoints();
            Outcome o = d.outcome();
            // phase 1 replay (section 6): the admission decision follows from the inputs and earlier reservations
            Outcome expectedEarly = null;
            QualityTier expectedTier = null;
            if (q.viewerCount() == 0) {
                expectedEarly = Outcome.SKIPPED_NO_VIEWERS;
            } else {
                List<QualityTier> candidates = new ArrayList<>();
                for (QualityTier t : List.of(QualityTier.HEAVY, QualityTier.BALANCED, QualityTier.SIMPLE)) {
                    if (t.ordinal() <= q.tier().ordinal() && q.recipe().tiers().contains(t)) {
                        candidates.add(t);
                    }
                }
                // G11: the requested tier is a strict ceiling; no candidate -> SKIPPED_NO_ELIGIBLE_TIER
                long availG = ambient ? g - usedG : Math.max(0, g - rg - usedG);
                boolean anyDeliverable = false;
                for (QualityTier t : candidates) {
                    EffectScene s = q.recipe().variant(t);
                    boolean deliverable = false;
                    for (Layer l : s.layers()) {
                        deliverable |= l.shape().size() > 0 && styles.contains(l.style());
                    }
                    if (deliverable) {
                        anyDeliverable = true;
                        if (s.maxPointsPerFrame() <= availG) {
                            expectedTier = t;
                            break;
                        }
                    }
                }
                if (candidates.isEmpty()) {
                    expectedEarly = Outcome.SKIPPED_NO_ELIGIBLE_TIER;
                } else if (!anyDeliverable) {
                    expectedEarly = Outcome.SKIPPED_UNSUPPORTED;
                } else if (expectedTier == null) {
                    expectedEarly = Outcome.SKIPPED_GENERATION_BUDGET;
                }
            }
            if (expectedEarly != null) {
                assertEquals(expectedEarly, o, q.id() + " phase 1 outcome");
            } else {
                assertEquals(expectedTier, d.tier(), q.id() + " phase 1 tier");
                assertTrue(o != Outcome.SKIPPED_NO_VIEWERS && o != Outcome.SKIPPED_GENERATION_BUDGET, q.id() + " admitted");
            }
            boolean admitted = o == Outcome.DELIVERED || o == Outcome.THINNED || o == Outcome.SKIPPED_INACTIVE
                    || o == Outcome.SKIPPED_DELIVERY_BUDGET || o == Outcome.FAILED_EVALUATION
                    || (o == Outcome.SKIPPED_UNSUPPORTED && d.tier() != null);
            if (!admitted) {
                // never admitted: no tier, no reservation, no evaluation (G2)
                assertNull(d.tier(), q.id() + " tier");
                assertNull(d.frame(), q.id() + " frame");
                assertEquals(0, d.reservedGeneratedPoints());
                assertEquals(0, d.sourcePoints());
            } else {
                assertNotNull(d.tier());
                assertTrue(d.tier().ordinal() <= q.tier().ordinal(), q.id() + " G11: tier above the request");
                assertTrue(q.recipe().tiers().contains(d.tier()));
                EffectScene s = q.recipe().variant(d.tier());
                assertEquals(s.maxPointsPerFrame(), d.reservedGeneratedPoints(), "reservation = maxPointsPerFrame");
                usedG += d.reservedGeneratedPoints();
                if (!ambient) {
                    usedGNonAmbient += d.reservedGeneratedPoints();
                }
                if (o == Outcome.FAILED_EVALUATION) {
                    assertNull(d.frame());
                    assertEquals(0, d.sourcePoints());
                } else {
                    assertNotNull(d.frame());
                    assertEquals(d.frame().pointCount(), d.sourcePoints());
                    assertTrue(d.frame().pointCount() <= d.reservedGeneratedPoints(), "G2");
                }
            }
            if (o == Outcome.SKIPPED_NO_VIEWERS) {
                assertEquals(0, q.viewerCount());
            }
            if (o == Outcome.SKIPPED_INACTIVE) {
                assertTrue(d.frame().stage() != TimingSample.Stage.ACTIVE || d.frame().isEmpty());
            }
            if (o != Outcome.DELIVERED && o != Outcome.THINNED) {
                assertEquals(0, d.keptPoints());
                assertEquals(0, d.placements());
                assertEquals(0, d.recipientCount());
                assertEquals(0, d.orderedBatchCount());
                continue;
            }
            // supported/unsupported split of the evaluated frame
            EffectFrame f = d.frame();
            long supported = 0, unsupported = 0;
            for (FrameBatch b : f.batches()) {
                if (styles.contains(b.style())) {
                    supported += b.size();
                } else {
                    unsupported += b.size();
                }
            }
            assertEquals(unsupported, d.unsupportedPoints());
            assertTrue(supported > 0);
            int k = d.keptPoints();
            // G5: per batch selection
            long keptSum = 0;
            Set<Integer> seenBatch = new HashSet<>();
            int lastRole = -1;
            Map<LayerRole, Long> keptByRole = new EnumMap<>(LayerRole.class);
            Map<LayerRole, Long> sizeByRole = new EnumMap<>(LayerRole.class);
            for (FrameBatch b : f.batches()) {
                if (styles.contains(b.style())) {
                    sizeByRole.merge(q.recipe().role(b.layer()), (long) b.size(), Long::sum);
                }
            }
            for (int pos = 0; pos < d.orderedBatchCount(); pos++) {
                int bi = d.orderedBatch(pos);
                assertTrue(seenBatch.add(bi), "batch listed once");
                FrameBatch b = f.batches().get(bi);
                assertTrue(styles.contains(b.style()), "only supported batches are kept");
                int role = q.recipe().role(b.layer()).ordinal();
                assertTrue(role >= lastRole, "ordered by role");
                lastRole = role;
                int m = d.kept(pos);
                assertTrue(m >= 1 && m <= b.size());
                Set<Integer> idx = new HashSet<>();
                for (int j = 0; j < m; j++) {
                    int s = d.selectedIndex(pos, j);
                    assertTrue(s >= 0 && s < b.size(), "G5 range");
                    assertTrue(idx.add(s), "G5 distinct");
                    assertTrue(j == 0 || s > d.selectedIndex(pos, j - 1), "selected indices strictly increase (no wrap)");
                }
                keptSum += m;
                keptByRole.merge(q.recipe().role(b.layer()), (long) m, Long::sum);
            }
            assertEquals(k, keptSum, "G5 sum");
            // G7: a lower role keeps anything only if every higher role is complete
            for (LayerRole lower : LayerRole.values()) {
                if (keptByRole.getOrDefault(lower, 0L) == 0) {
                    continue;
                }
                for (LayerRole higher : LayerRole.values()) {
                    if (higher.ordinal() < lower.ordinal()) {
                        assertEquals(sizeByRole.getOrDefault(higher, 0L), keptByRole.getOrDefault(higher, 0L), "G7");
                    }
                }
            }
            // G4 and recipients: ascending viewers of q, c = min(k, V[v]) > 0
            long availP = ambient ? p - usedP : Math.max(0, p - rp - usedP);
            long w = world.getOrDefault(q.worldId(), policy.worldPlacementBudget());
            long budget = Math.min(availP, w);
            long[] vrem = new long[q.viewerCount()];
            for (int x = 0; x < vrem.length; x++) {
                vrem[x] = viewer.getOrDefault(q.viewer(x), policy.viewerPlacementBudget());
            }
            long maxV = 0;
            for (long x : vrem) {
                maxV = Math.max(maxV, x);
            }
            long kmax = Math.min(Math.min(supported, policy.keptCap(d.tier())), maxV);
            assertTrue(k >= 1 && k <= kmax, "G4 cap");
            long cost = cost(k, vrem);
            assertTrue(cost <= budget, "G3 budget");
            if (k < kmax) {
                assertTrue(cost(k + 1, vrem) > budget, "G9 maximality");
            }
            int rc = 0;
            long placed = 0;
            for (int x = 0; x < vrem.length; x++) {
                long c = Math.min(k, vrem[x]);
                if (c > 0) {
                    assertEquals(q.viewer(x), d.recipient(rc));
                    assertEquals(c, d.recipientPoints(rc));
                    assertTrue(d.recipientPoints(rc) <= k);
                    rc++;
                    placed += c;
                    viewer.put(q.viewer(x), vrem[x] - c);
                }
            }
            assertEquals(rc, d.recipientCount());
            long maxPoints = 0;
            boolean everyone = rc == q.viewerCount();
            for (int x = 0; x < rc; x++) {
                maxPoints = Math.max(maxPoints, d.recipientPoints(x));
                everyone &= d.recipientPoints(x) == k;
            }
            assertEquals(k, maxPoints, "G4 keptPoints = max recipientPoints");
            assertEquals(k == supported && unsupported == 0 && everyone ? Outcome.DELIVERED : Outcome.THINNED, o, q.id() + " outcome");
            assertEquals(placed, d.placements(), "G4 placements = sum of recipient points");
            assertEquals(cost, placed);
            world.put(q.worldId(), w - placed);
            usedP += placed;
            if (!ambient) {
                usedPNonAmbient += placed;
            }
        }
        // G1, G3 totals
        assertTrue(usedG <= g, "G1");
        assertTrue(usedGNonAmbient <= g - rg, "G1 non-ambient");
        assertTrue(usedP <= p, "G3");
        assertTrue(usedPNonAmbient <= p - rp, "G3 non-ambient");
        for (long left : world.values()) {
            assertTrue(left >= 0, "G3 world");
        }
        for (long left : viewer.values()) {
            assertTrue(left >= 0, "G3 viewer");
        }
        for (Outcome o : Outcome.values()) {
            assertEquals((int) counts.getOrDefault(o, 0), plan.count(o), "count " + o);
        }
        assertEquals(tReserved, plan.reservedGeneratedPoints());
        assertEquals(tSource, plan.sourcePoints());
        assertEquals(tKept, plan.keptPoints());
        assertEquals(tRecipients, plan.recipients());
        assertEquals(tPlacements, plan.placements());
        assertEquals(tUnsupported, plan.unsupportedPoints());
    }

    static long cost(long k, long[] remaining) {
        long c = 0;
        for (long v : remaining) {
            c += Math.min(k, v);
        }
        return c;
    }

    /** The selection sequence of a delivery (ordered batches, j ascending) as (batch index, point index) pairs. */
    static List<int[]> sequence(EffectDelivery d) {
        List<int[]> out = new ArrayList<>();
        for (int pos = 0; pos < d.orderedBatchCount(); pos++) {
            for (int j = 0; j < d.kept(pos); j++) {
                out.add(new int[] {d.orderedBatch(pos), d.selectedIndex(pos, j)});
            }
        }
        return out;
    }

    /** Insertion-ordered map helper for building variant maps in tests. */
    static Map<QualityTier, EffectScene> variants(Object... tierScenePairs) {
        Map<QualityTier, EffectScene> m = new LinkedHashMap<>();
        for (int i = 0; i < tierScenePairs.length; i += 2) {
            m.put((QualityTier) tierScenePairs[i], (EffectScene) tierScenePairs[i + 1]);
        }
        return m;
    }
}
