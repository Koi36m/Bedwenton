package workshop.vfx.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static workshop.vfx.delivery.M6TestSupport.NOW;
import static workshop.vfx.delivery.M6TestSupport.layer;
import static workshop.vfx.delivery.M6TestSupport.pausedAt;
import static workshop.vfx.delivery.M6TestSupport.request;
import static workshop.vfx.delivery.M6TestSupport.roomy;
import static workshop.vfx.delivery.M6TestSupport.scene;
import static workshop.vfx.delivery.M6TestSupport.styles;
import static workshop.vfx.delivery.M6TestSupport.tiers;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.math.core.Pose;
import workshop.vfx.math.time.Playback;
import workshop.vfx.math.time.TimeDomain;
import workshop.vfx.math.time.TimePoint;
import workshop.vfx.scene.EffectScene;
import workshop.vfx.scene.StyleKey;

/** Contract sections 3 and 4: construction, validation, copies, and the order of the plan input checks. */
class DeliveryValidationTest {
    private static final EffectScene RING = scene(layer("main", 4, "t:dot"));
    private static final EffectRecipe DOT = tiers("dot", EffectPriority.NORMAL, "t:dot", 1, 2, 4);

    private static DeliveryPolicy policy(int maxRequests, int maxViewersPerRequest, long maxViewerEntries) {
        return new DeliveryPolicy(maxRequests, maxViewersPerRequest, maxViewerEntries, 100, 0, 100, 0, 100, 100, 1, 2, 4);
    }

    @Test
    @DisplayName("EffectRecipe.of: name pattern, nonnull priority and maps, 1 to 3 variants, one time domain, roles naming existing layers")
    void recipe() {
        Map<QualityTier, EffectScene> one = Map.of(QualityTier.HEAVY, RING);
        for (String bad : new String[] {null, "", "Upper", "has space", "a".repeat(65), "x:y"}) {
            assertThrows(IllegalArgumentException.class, () -> EffectRecipe.of(bad, EffectPriority.ACTION, one, Map.of()), String.valueOf(bad));
        }
        EffectRecipe.of("a".repeat(64), EffectPriority.ACTION, one, Map.of());
        EffectRecipe.of("a_b.c-9", EffectPriority.ACTION, one, Map.of());
        assertThrows(IllegalArgumentException.class, () -> EffectRecipe.of("r", null, one, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> EffectRecipe.of("r", EffectPriority.ACTION, null, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> EffectRecipe.of("r", EffectPriority.ACTION, one, null));
        assertThrows(IllegalArgumentException.class, () -> EffectRecipe.of("r", EffectPriority.ACTION, Map.of(), Map.of()));
        Map<QualityTier, EffectScene> nullValue = new HashMap<>();
        nullValue.put(QualityTier.HEAVY, null);
        assertThrows(IllegalArgumentException.class, () -> EffectRecipe.of("r", EffectPriority.ACTION, nullValue, Map.of()));
        Map<QualityTier, EffectScene> nullKey = new HashMap<>();
        nullKey.put(null, RING);
        assertThrows(IllegalArgumentException.class, () -> EffectRecipe.of("r", EffectPriority.ACTION, nullKey, Map.of()));
        EffectScene cosmetic = EffectScene.of(TimeDomain.COSMETIC, 10, List.of(layer("main", 2, "t:dot")), M6TestSupport.LIMITS);
        assertThrows(IllegalArgumentException.class, () -> EffectRecipe.of("r", EffectPriority.ACTION,
                Map.of(QualityTier.SIMPLE, cosmetic, QualityTier.HEAVY, RING), Map.of()), "mixed time domains");
        assertThrows(IllegalArgumentException.class, () -> EffectRecipe.of("r", EffectPriority.ACTION, one, Map.of("typo", LayerRole.ESSENTIAL)));
        Map<String, LayerRole> nullRole = new HashMap<>();
        nullRole.put("main", null);
        assertThrows(IllegalArgumentException.class, () -> EffectRecipe.of("r", EffectPriority.ACTION, one, nullRole));
        Map<String, LayerRole> nullName = new HashMap<>();
        nullName.put(null, LayerRole.ESSENTIAL);
        assertThrows(IllegalArgumentException.class, () -> EffectRecipe.of("r", EffectPriority.ACTION, one, nullName));
        Map<String, LayerRole> tooMany = new HashMap<>();
        for (int i = 0; i <= EffectRecipe.MAX_ROLES; i++) {
            tooMany.put("l" + i, LayerRole.STANDARD);
        }
        assertThrows(IllegalArgumentException.class, () -> EffectRecipe.of("r", EffectPriority.ACTION, one, tooMany));
        // a role may name a layer present in only one variant
        EffectScene two = scene(layer("main", 2, "t:dot"), layer("extra", 2, "t:dot"));
        EffectRecipe r = EffectRecipe.of("r", EffectPriority.AMBIENT, Map.of(QualityTier.SIMPLE, RING, QualityTier.HEAVY, two),
                Map.of("extra", LayerRole.DECORATIVE));
        assertEquals(LayerRole.DECORATIVE, r.role("extra"));
        assertEquals(LayerRole.STANDARD, r.role("main"), "unmapped layers are STANDARD");
        assertEquals(LayerRole.STANDARD, r.role("nowhere"));
        assertThrows(IllegalArgumentException.class, () -> r.role(null));
        assertEquals(TimeDomain.GAME, r.domain());
        assertEquals("r", r.name());
        assertEquals(EffectPriority.AMBIENT, r.priority());
    }

    @Test
    @DisplayName("EffectRecipe: maps are copied; tiers() is ascending and unmodifiable; variant() of a missing tier is IAE")
    void recipeCopies() {
        Map<QualityTier, EffectScene> v = new EnumMap<>(QualityTier.class);
        v.put(QualityTier.HEAVY, RING);
        v.put(QualityTier.SIMPLE, RING);
        Map<String, LayerRole> roles = new HashMap<>(Map.of("main", LayerRole.ESSENTIAL));
        EffectRecipe r = EffectRecipe.of("r", EffectPriority.NORMAL, v, roles);
        v.put(QualityTier.BALANCED, RING);
        roles.put("main", LayerRole.DECORATIVE);
        assertEquals(List.of(QualityTier.SIMPLE, QualityTier.HEAVY), new ArrayList<>(r.tiers()));
        assertEquals(LayerRole.ESSENTIAL, r.role("main"));
        assertThrows(UnsupportedOperationException.class, () -> r.tiers().add(QualityTier.BALANCED));
        assertThrows(IllegalArgumentException.class, () -> r.variant(QualityTier.BALANCED));
        assertThrows(IllegalArgumentException.class, () -> r.variant(null));
        assertEquals(RING, r.variant(QualityTier.HEAVY));
    }

    @Test
    @DisplayName("DeliveryRequest.of: nonnull arguments, recipe domain, world >= 0, viewers bounded before copying, >= 0 and strictly increasing, copied")
    void requestValidation() {
        Playback p = pausedAt(1);
        long[] v = {1, 2};
        assertThrows(IllegalArgumentException.class, () -> DeliveryRequest.of(1, null, QualityTier.HEAVY, 0, Pose.IDENTITY, p, NOW, 0, v));
        assertThrows(IllegalArgumentException.class, () -> DeliveryRequest.of(1, DOT, null, 0, Pose.IDENTITY, p, NOW, 0, v));
        assertThrows(IllegalArgumentException.class, () -> DeliveryRequest.of(1, DOT, QualityTier.HEAVY, 0, null, p, NOW, 0, v));
        assertThrows(IllegalArgumentException.class, () -> DeliveryRequest.of(1, DOT, QualityTier.HEAVY, 0, Pose.IDENTITY, null, NOW, 0, v));
        assertThrows(IllegalArgumentException.class, () -> DeliveryRequest.of(1, DOT, QualityTier.HEAVY, 0, Pose.IDENTITY, p, null, 0, v));
        assertThrows(IllegalArgumentException.class, () -> DeliveryRequest.of(1, DOT, QualityTier.HEAVY, 0, Pose.IDENTITY, p, NOW, 0, null));
        assertThrows(IllegalArgumentException.class, () -> DeliveryRequest.of(1, DOT, QualityTier.HEAVY, -1, Pose.IDENTITY, p, NOW, 0, v));
        TimePoint cosmetic = TimePoint.cosmeticNanos(0);
        assertThrows(IllegalArgumentException.class, () -> DeliveryRequest.of(1, DOT, QualityTier.HEAVY, 0, Pose.IDENTITY,
                new Playback(cosmetic, 0, 1, true), NOW, 0, v), "playback domain");
        assertThrows(IllegalArgumentException.class, () -> DeliveryRequest.of(1, DOT, QualityTier.HEAVY, 0, Pose.IDENTITY, p, cosmetic, 0, v),
                "instant domain");
        for (long[] bad : new long[][] {{-1}, {2, 1}, {3, 3}, {0, 5, 4}, {Long.MIN_VALUE}}) {
            assertThrows(IllegalArgumentException.class, () -> DeliveryRequest.of(1, DOT, QualityTier.HEAVY, 0, Pose.IDENTITY, p, NOW, 0, bad),
                    Arrays.toString(bad));
        }
        long[] max = new long[DeliveryRequest.MAX_VIEWERS];
        Arrays.setAll(max, i -> i);
        assertEquals(DeliveryRequest.MAX_VIEWERS, DeliveryRequest.of(1, DOT, QualityTier.HEAVY, 0, Pose.IDENTITY, p, NOW, 0, max).viewerCount());
        long[] over = new long[DeliveryRequest.MAX_VIEWERS + 1];
        Arrays.setAll(over, i -> i);
        assertThrows(IllegalArgumentException.class, () -> DeliveryRequest.of(1, DOT, QualityTier.HEAVY, 0, Pose.IDENTITY, p, NOW, 0, over));
        // extremes are accepted for id, frame ordinal, world and viewer ids
        long[] mine = {0, Long.MAX_VALUE};
        DeliveryRequest q = DeliveryRequest.of(Long.MIN_VALUE, DOT, QualityTier.SIMPLE, Integer.MAX_VALUE, Pose.IDENTITY, p, NOW, Long.MIN_VALUE, mine);
        mine[1] = 7;
        assertEquals(Long.MAX_VALUE, q.viewer(1), "viewer ids are copied");
        assertEquals(Long.MIN_VALUE, q.id());
        assertEquals(Long.MIN_VALUE, q.frameOrdinal());
        assertEquals(Integer.MAX_VALUE, q.worldId());
        assertEquals(QualityTier.SIMPLE, q.tier());
        assertEquals(DOT, q.recipe());
        assertThrows(IndexOutOfBoundsException.class, () -> q.viewer(2));
        assertThrows(IndexOutOfBoundsException.class, () -> q.viewer(-1));
        assertEquals(0, DeliveryRequest.of(1, DOT, QualityTier.HEAVY, 0, Pose.IDENTITY, p, NOW, 0, new long[0]).viewerCount());
    }

    @Test
    @DisplayName("DeliveryPolicy: every field bound of section 4.3, inclusive at both ends, and keptCap per tier")
    void policyBounds() {
        long b = DeliveryPolicy.MAX_BUDGET;
        DeliveryPolicy max = new DeliveryPolicy(DeliveryPolicy.MAX_REQUESTS_CEILING, DeliveryRequest.MAX_VIEWERS, DeliveryPolicy.MAX_VIEWER_ENTRIES_CEILING,
                b, b, b, b, b, b, 1 << 20, 1 << 20, 1 << 20);
        assertEquals(1 << 20, max.keptCap(QualityTier.HEAVY));
        DeliveryPolicy min = new DeliveryPolicy(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        assertEquals(0, min.keptCap(QualityTier.SIMPLE));
        DeliveryPolicy caps = new DeliveryPolicy(1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3);
        assertEquals(1, caps.keptCap(QualityTier.SIMPLE));
        assertEquals(2, caps.keptCap(QualityTier.BALANCED));
        assertEquals(3, caps.keptCap(QualityTier.HEAVY));
        assertThrows(IllegalArgumentException.class, () -> caps.keptCap(null));
        long[][] bad = {
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {(1 << 16) + 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {1, (1 << 16) + 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {1, 0, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {1, 0, (1L << 24) + 1, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {1, 0, 0, -1, 0, 0, 0, 0, 0, 0, 0, 0},
            {1, 0, 0, b + 1, 0, 0, 0, 0, 0, 0, 0, 0},
            {1, 0, 0, 5, 6, 0, 0, 0, 0, 0, 0, 0},
            {1, 0, 0, 5, -1, 0, 0, 0, 0, 0, 0, 0},
            {1, 0, 0, 0, 0, -1, 0, 0, 0, 0, 0, 0},
            {1, 0, 0, 0, 0, b + 1, 0, 0, 0, 0, 0, 0},
            {1, 0, 0, 0, 0, 5, 6, 0, 0, 0, 0, 0},
            {1, 0, 0, 0, 0, 5, -1, 0, 0, 0, 0, 0},
            {1, 0, 0, 0, 0, 0, 0, -1, 0, 0, 0, 0},
            {1, 0, 0, 0, 0, 0, 0, b + 1, 0, 0, 0, 0},
            {1, 0, 0, 0, 0, 0, 0, 0, -1, 0, 0, 0},
            {1, 0, 0, 0, 0, 0, 0, 0, b + 1, 0, 0, 0},
            {1, 0, 0, 0, 0, 0, 0, 0, 0, -1, 0, 0},
            {1, 0, 0, 0, 0, 0, 0, 0, 0, 2, 1, 3},
            {1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 3, 2},
            {1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (1 << 20) + 1},
        };
        for (long[] f : bad) {
            assertThrows(IllegalArgumentException.class, () -> new DeliveryPolicy((int) f[0], (int) f[1], f[2], f[3], f[4], f[5], f[6], f[7], f[8],
                    (int) f[9], (int) f[10], (int) f[11]), Arrays.toString(f));
        }
    }

    @Test
    @DisplayName("plan: null arguments and elements, size checked before copying, unique ids, viewers per request and total viewer entries")
    void planInputs() {
        DeliveryPolicy pol = policy(4, 3, 5);
        Set<StyleKey> st = styles("t:dot");
        List<DeliveryRequest> ok = List.of(request(1, DOT, QualityTier.HEAVY, 0, 0, 1, 1, 2, 3), request(2, DOT, QualityTier.HEAVY, 0, 0, 1, 1, 2));
        assertEquals(2, DeliveryPlanner.plan(0, ok, st, pol).deliveries().size(), "3 + 2 = 5 viewer entries is allowed");
        assertThrows(IllegalArgumentException.class, () -> DeliveryPlanner.plan(0, null, st, pol));
        assertThrows(IllegalArgumentException.class, () -> DeliveryPlanner.plan(0, ok, null, pol));
        assertThrows(IllegalArgumentException.class, () -> DeliveryPlanner.plan(0, ok, st, null));
        List<DeliveryRequest> withNull = new ArrayList<>(ok);
        withNull.add(null);
        assertThrows(IllegalArgumentException.class, () -> DeliveryPlanner.plan(0, withNull, st, pol));
        Set<StyleKey> nullStyle = new HashSet<>(st);
        nullStyle.add(null);
        assertThrows(IllegalArgumentException.class, () -> DeliveryPlanner.plan(0, ok, nullStyle, pol));
        assertThrows(IllegalArgumentException.class, () -> DeliveryPlanner.plan(0,
                List.of(request(7, DOT, QualityTier.HEAVY, 0, 0, 1, 1), request(7, DOT, QualityTier.HEAVY, 1, 0, 1, 2)), st, pol), "duplicate id");
        assertThrows(IllegalArgumentException.class, () -> DeliveryPlanner.plan(0,
                List.of(request(1, DOT, QualityTier.HEAVY, 0, 0, 1, 1, 2, 3, 4)), st, pol), "4 viewers > 3");
        assertThrows(IllegalArgumentException.class, () -> DeliveryPlanner.plan(0,
                List.of(request(1, DOT, QualityTier.HEAVY, 0, 0, 1, 1, 2, 3), request(2, DOT, QualityTier.HEAVY, 0, 0, 1, 1, 2, 3)), st, pol),
                "6 viewer entries > 5");
        List<DeliveryRequest> five = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            five.add(request(i, DOT, QualityTier.HEAVY, 0, 0, 1));
        }
        assertThrows(IllegalArgumentException.class, () -> DeliveryPlanner.plan(0, five, st, pol), "5 requests > 4");
        assertEquals(4, DeliveryPlanner.plan(0, five.subList(0, 4), st, pol).deliveries().size());
        // the size is checked before anything is read from the list: a hostile list that cannot be read
        List<DeliveryRequest> huge = new AbstractList<>() {
            @Override
            public DeliveryRequest get(int index) {
                throw new AssertionError("read before the size check");
            }

            @Override
            public int size() {
                return Integer.MAX_VALUE;
            }
        };
        assertThrows(IllegalArgumentException.class, () -> DeliveryPlanner.plan(0, huge, st, pol));
    }

    @Test
    @DisplayName("plan: the request list and style set are copied; mutating them afterwards does not change the plan")
    void planCopies() {
        List<DeliveryRequest> reqs = new ArrayList<>(List.of(request(1, DOT, QualityTier.HEAVY, 0, 0, 1, 1)));
        Set<StyleKey> st = new HashSet<>(styles("t:dot"));
        DeliveryPlan plan = DeliveryPlanner.plan(0, reqs, st, roomy());
        reqs.clear();
        st.clear();
        assertEquals(1, plan.deliveries().size());
        assertEquals(Outcome.DELIVERED, plan.deliveries().get(0).outcome());
        assertThrows(UnsupportedOperationException.class, () -> plan.deliveries().clear());
        assertThrows(IllegalArgumentException.class, () -> plan.count(null));
        EffectDelivery d = plan.deliveries().get(0);
        assertThrows(IndexOutOfBoundsException.class, () -> d.orderedBatch(1));
        assertThrows(IndexOutOfBoundsException.class, () -> d.kept(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> d.selectedIndex(0, 4));
        assertThrows(IndexOutOfBoundsException.class, () -> d.selectedIndex(0, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> d.recipient(1));
        assertThrows(IndexOutOfBoundsException.class, () -> d.recipientPoints(-1));
    }

    @Test
    @DisplayName("plan: an empty request list gives an empty plan with zero totals")
    void empty() {
        DeliveryPlan plan = DeliveryPlanner.plan(Long.MIN_VALUE, List.of(), Set.of(), roomy());
        assertEquals(0, plan.deliveries().size());
        assertEquals(Long.MIN_VALUE, plan.tickOrdinal());
        assertEquals(0, plan.placements() + plan.keptPoints() + plan.recipients() + plan.sourcePoints() + plan.reservedGeneratedPoints()
                + plan.unsupportedPoints());
        for (Outcome o : Outcome.values()) {
            assertEquals(0, plan.count(o));
        }
    }
}
