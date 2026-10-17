package workshop.vfx.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static workshop.vfx.delivery.M6TestSupport.checkInvariants;
import static workshop.vfx.delivery.M6TestSupport.layer;
import static workshop.vfx.delivery.M6TestSupport.request;
import static workshop.vfx.delivery.M6TestSupport.scene;
import static workshop.vfx.delivery.M6TestSupport.styles;
import static workshop.vfx.delivery.M6TestSupport.tiers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.scene.StyleKey;

/** Contract section 9: the ceilings, huge values and large inputs, with every count checked exactly. */
class M6StressTest {
    private static final Set<StyleKey> DOT = styles("t:dot");

    @Test
    @DisplayName("Ceilings: 2^16 requests in one call, maximal budgets, and 2^50 budgets without overflow")
    void manyRequests() {
        EffectRecipe r = tiers("r", EffectPriority.NORMAL, "t:dot", 1, 2, 3);
        List<DeliveryRequest> reqs = new ArrayList<>();
        for (int i = 0; i < DeliveryPolicy.MAX_REQUESTS_CEILING; i++) {
            reqs.add(request(i * 7919L - 1_000_000, r, QualityTier.values()[i % 3], i % 5, i, 1, i % 256));
        }
        long b = DeliveryPolicy.MAX_BUDGET;
        DeliveryPolicy pol = new DeliveryPolicy(DeliveryPolicy.MAX_REQUESTS_CEILING, 1, 1 << 16, b, 0, b, 0, b, b, 1 << 20, 1 << 20, 1 << 20);
        DeliveryPlan p = DeliveryPlanner.plan(Long.MAX_VALUE, reqs, DOT, pol);
        checkInvariants(reqs, DOT, pol, p);
        assertEquals(reqs.size(), p.count(Outcome.DELIVERED));
        long expected = 0;
        for (DeliveryRequest q : reqs) {
            expected += q.tier().ordinal() + 1;
        }
        assertEquals(expected, p.placements(), "1, 2 or 3 points to the single viewer of each request");
        assertEquals(expected, p.keptPoints());
        assertEquals(reqs.size(), p.recipients());
    }

    @Test
    @DisplayName("Viewer entries: 2^20 entries over 256 requests of 4,096 viewers; per-viewer budgets bind across requests")
    void manyViewers() {
        EffectRecipe r = tiers("r", EffectPriority.NORMAL, "t:dot", null, null, 8);
        List<DeliveryRequest> reqs = new ArrayList<>();
        long[] viewers = new long[4096];
        for (int i = 0; i < viewers.length; i++) {
            viewers[i] = 3L * i;
        }
        for (int i = 0; i < 256; i++) {
            reqs.add(request(i, r, QualityTier.HEAVY, i % 4, i, 1, viewers));
        }
        DeliveryPolicy pol = new DeliveryPolicy(256, 4096, 1 << 20, 1 << 20, 0, 1L << 40, 0, 1L << 40, 100, 8, 8, 8);
        DeliveryPlan p = DeliveryPlanner.plan(0, reqs, DOT, pol);
        checkInvariants(reqs, DOT, pol, p);
        assertEquals(4096L * 100, p.placements(), "every viewer receives exactly its budget of 100 points");
        assertEquals(12, p.count(Outcome.DELIVERED), "12 * 8 = 96; the 13th effect fits 4 points per viewer");
        assertEquals(1, p.count(Outcome.THINNED));
        assertEquals(243, p.count(Outcome.SKIPPED_DELIVERY_BUDGET));
    }

    @Test
    @DisplayName("The full M5 frame: 2^20 points, kept in full or thinned to one point, with exact counts and in-range selections")
    void fullFrame() {
        EffectRecipe big = EffectRecipe.of("big", EffectPriority.AMBIENT, Map.of(QualityTier.HEAVY, scene(layer("all", 1 << 20, "t:dot"))), Map.of());
        List<DeliveryRequest> reqs = List.of(request(1, big, QualityTier.HEAVY, 0, -1, 1, 0, Long.MAX_VALUE));
        long b = DeliveryPolicy.MAX_BUDGET;
        DeliveryPolicy all = new DeliveryPolicy(1, 2, 2, 1 << 20, 0, b, 0, b, b, 1 << 20, 1 << 20, 1 << 20);
        DeliveryPlan p = DeliveryPlanner.plan(0, reqs, DOT, all);
        EffectDelivery d = p.deliveries().get(0);
        assertEquals(Outcome.DELIVERED, d.outcome());
        assertEquals(1 << 20, d.keptPoints());
        assertEquals(2L << 20, d.placements());
        assertEquals((1 << 20) - 1, d.selectedIndex(0, (1 << 20) - 1));
        DeliveryPolicy one = new DeliveryPolicy(1, 2, 2, 1 << 20, 0, b, 0, b, b, 1, 1, 1);
        EffectDelivery t = DeliveryPlanner.plan(0, reqs, DOT, one).deliveries().get(0);
        assertEquals(Outcome.THINNED, t.outcome());
        assertEquals(1, t.keptPoints());
        assertEquals((1 << 20) - 1, t.selectedIndex(0, 0), "g = 2^20 and floorMod(-1, g) = 2^20 - 1");
        DeliveryPolicy odd = new DeliveryPolicy(1, 2, 2, 1 << 20, 0, b, 0, b, b, 0, 0, 999_983);
        EffectDelivery o = DeliveryPlanner.plan(0, reqs, DOT, odd).deliveries().get(0);
        int n = 1 << 20, m = 999_983, g = 2;
        for (int j : new int[] {0, 1, 500_000, m - 2, m - 1}) {
            assertEquals((int) (Math.floorMod(-1L, (long) g) + (long) j * n / m), o.selectedIndex(0, j));
        }
        assertTrue(o.selectedIndex(0, m - 1) < n);
    }

    @Test
    @DisplayName("Extremes: ids and ordinals at Long bounds, world Integer.MAX_VALUE, viewer Long.MAX_VALUE, zero budgets")
    void extremes() {
        EffectRecipe r = tiers("r", EffectPriority.ACTION, "t:dot", 1, 2, 4);
        List<DeliveryRequest> reqs = List.of(request(Long.MAX_VALUE, r, QualityTier.HEAVY, Integer.MAX_VALUE, Long.MAX_VALUE, 1, Long.MAX_VALUE),
                request(Long.MIN_VALUE, r, QualityTier.HEAVY, 0, Long.MIN_VALUE, 1, 0));
        DeliveryPolicy pol = M6TestSupport.roomy();
        DeliveryPlan p = DeliveryPlanner.plan(Long.MIN_VALUE, reqs, DOT, pol);
        checkInvariants(reqs, DOT, pol, p);
        assertEquals(1, p.deliveries().get(0).allocationRank(), "MIN < MAX; floorMod(MIN, 2) = 0");
        assertEquals(2, p.count(Outcome.DELIVERED));
        DeliveryPolicy zero = new DeliveryPolicy(2, 1, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        DeliveryPlan z = DeliveryPlanner.plan(0, reqs, DOT, zero);
        checkInvariants(reqs, DOT, zero, z);
        assertEquals(2, z.count(Outcome.SKIPPED_GENERATION_BUDGET));
        DeliveryPolicy noPlacement = new DeliveryPolicy(2, 1, 2, 100, 0, 0, 0, 0, 0, 4, 4, 4);
        assertEquals(2, DeliveryPlanner.plan(0, reqs, DOT, noPlacement).count(Outcome.SKIPPED_DELIVERY_BUDGET));
        DeliveryPolicy noCap = new DeliveryPolicy(2, 1, 2, 100, 0, 100, 0, 100, 100, 0, 0, 0);
        assertEquals(2, DeliveryPlanner.plan(0, reqs, DOT, noCap).count(Outcome.SKIPPED_DELIVERY_BUDGET), "a kept cap of 0 delivers nothing");
    }

    @Test
    @DisplayName("Denied preflight allocates nothing proportional to the frame: a 2^20-point scene over budget costs under 64 KiB per plan")
    void deniedPreflightAllocation() {
        EffectRecipe big = EffectRecipe.of("big", EffectPriority.ACTION, Map.of(QualityTier.HEAVY, scene(layer("all", 1 << 20, "t:dot"))), Map.of());
        List<DeliveryRequest> reqs = List.of(request(1, big, QualityTier.HEAVY, 0, 0, 1, 1, 2, 3), request(2, big, QualityTier.HEAVY, 0, 0, 1));
        DeliveryPolicy pol = new DeliveryPolicy(2, 3, 3, (1 << 20) - 1, 0, 1L << 40, 0, 1L << 40, 1L << 40, 1 << 20, 1 << 20, 1 << 20);
        com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        for (int warm = 0; warm < 200; warm++) {
            DeliveryPlanner.plan(warm, reqs, DOT, pol);
        }
        long before = mx.getCurrentThreadAllocatedBytes();
        DeliveryPlan p = null;
        for (int i = 0; i < 100; i++) {
            p = DeliveryPlanner.plan(i, reqs, DOT, pol);
        }
        long perPlan = (mx.getCurrentThreadAllocatedBytes() - before) / 100;
        assertEquals(Outcome.SKIPPED_GENERATION_BUDGET, p.deliveries().get(0).outcome());
        assertEquals(Outcome.SKIPPED_NO_VIEWERS, p.deliveries().get(1).outcome());
        assertTrue(perPlan < 64 * 1024, "bytes per denied plan: " + perPlan + " (one evaluation would allocate 24 MiB)");
    }

    @Test
    @DisplayName("Many layers: 4,096 one-point layers in three roles, thinned to 1, 100 and 4,095 points with exact role precedence")
    void manyLayers() {
        workshop.vfx.scene.Layer[] layers = new workshop.vfx.scene.Layer[4096];
        Map<String, LayerRole> roles = new java.util.HashMap<>();
        for (int i = 0; i < layers.length; i++) {
            layers[i] = layer("l" + i, 1, "t:dot");
            roles.put("l" + i, LayerRole.values()[(i * 7) % 3]);
        }
        EffectRecipe r = EffectRecipe.of("many", EffectPriority.NORMAL, Map.of(QualityTier.HEAVY, scene(layers)), roles);
        List<DeliveryRequest> reqs = List.of(request(1, r, QualityTier.HEAVY, 0, 0, 1, 1));
        for (int cap : new int[] {1, 100, 1366, 1367, 4095}) {
            DeliveryPolicy pol = new DeliveryPolicy(1, 1, 1, 1 << 20, 0, 1L << 40, 0, 1L << 40, 1L << 40, cap, cap, cap);
            DeliveryPlan p = DeliveryPlanner.plan(0, reqs, DOT, pol);
            checkInvariants(reqs, DOT, pol, p);
            EffectDelivery d = p.deliveries().get(0);
            assertEquals(cap, d.orderedBatchCount());
            // essential layers are those with (7 i) mod 3 == 0, i.e. i mod 3 == 0: 1,366 of them, in layer order
            for (int pos = 0; pos < Math.min(cap, 1366); pos++) {
                assertEquals(3 * pos, d.orderedBatch(pos));
            }
        }
    }
}
