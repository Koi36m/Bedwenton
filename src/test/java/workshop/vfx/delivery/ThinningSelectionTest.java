package workshop.vfx.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static workshop.vfx.delivery.M6TestSupport.checkInvariants;
import static workshop.vfx.delivery.M6TestSupport.layer;
import static workshop.vfx.delivery.M6TestSupport.policy;
import static workshop.vfx.delivery.M6TestSupport.request;
import static workshop.vfx.delivery.M6TestSupport.roomy;
import static workshop.vfx.delivery.M6TestSupport.scene;
import static workshop.vfx.delivery.M6TestSupport.sequence;
import static workshop.vfx.delivery.M6TestSupport.styles;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.scene.EffectScene;
import workshop.vfx.scene.StyleKey;

/** Contract section 7 steps 6 to 8: role precedence (G7), proportional splitting, strided selection (G5, G6) and prefixes. */
class ThinningSelectionTest {
    private static final Set<StyleKey> DOT = styles("t:dot");

    private static EffectDelivery deliver(EffectRecipe r, long ordinal, int cap, long... viewers) {
        List<DeliveryRequest> reqs = List.of(request(1, r, QualityTier.HEAVY, 0, ordinal, 1, viewers));
        DeliveryPolicy pol = policy(1 << 30, 1L << 40, 1L << 40, 1L << 40, cap);
        DeliveryPlan p = DeliveryPlanner.plan(0, reqs, DOT, pol);
        checkInvariants(reqs, DOT, pol, p);
        return p.deliveries().get(0);
    }

    private static EffectRecipe ring(int n) {
        return EffectRecipe.of("ring", EffectPriority.NORMAL, Map.of(QualityTier.HEAVY, scene(layer("main", n, "t:dot"))), Map.of());
    }

    @Test
    @DisplayName("Selection formula: floorMod(ordinal, g) + floor(j n / m) with g = ceil(n / m), strictly increasing, for extreme ordinals")
    void formula() {
        int n = 10;
        for (int m = 1; m <= n; m++) {
            int g = (n + m - 1) / m;
            for (long ordinal : new long[] {0, 1, -1, 7, Long.MIN_VALUE, Long.MAX_VALUE, -123456789L}) {
                EffectDelivery d = deliver(ring(n), ordinal, m, 1);
                assertEquals(m, d.kept(0));
                int o = (int) Math.floorMod(ordinal, (long) g);
                for (int j = 0; j < m; j++) {
                    assertEquals(o + j * n / m, d.selectedIndex(0, j), "m " + m + " ordinal " + ordinal + " j " + j);
                }
            }
        }
        EffectDelivery all = deliver(ring(7), 12345, 7, 1);
        for (int j = 0; j < 7; j++) {
            assertEquals(j, all.selectedIndex(0, j), "keeping everything selects 0..n-1 in order (g = 1)");
        }
    }

    @Test
    @DisplayName("G6 coverage: any g consecutive frame ordinals together select every point; selected points are spread with gaps <= g")
    void coverage() {
        for (int n : new int[] {1, 2, 3, 5, 7, 24, 37, 60, 73, 100}) {
            for (int m = 1; m <= n; m += Math.max(1, n / 9)) {
                int g = (n + m - 1) / m;
                for (long base : new long[] {0, -5, Long.MAX_VALUE - 200, Long.MIN_VALUE}) {
                    Set<Integer> union = new HashSet<>();
                    for (int t = 0; t < g; t++) {
                        EffectDelivery d = deliver(ring(n), base + t, m, 1);
                        int previous = -1;
                        for (int j = 0; j < m; j++) {
                            union.add(d.selectedIndex(0, j));
                            int unshifted = j * n / m;
                            assertTrue(previous < 0 || unshifted - previous <= g, "gap");
                            previous = unshifted;
                        }
                        assertTrue(n - previous <= g, "wrap gap");
                    }
                    assertEquals(n, union.size(), "n " + n + " m " + m + " base " + base);
                }
            }
        }
    }

    @Test
    @DisplayName("G7: essential layers complete before standard, standard before decorative, whatever the layer order")
    void rolePrecedence() {
        EffectScene s = scene(layer("trail", 40, "t:dot"), layer("link", 20, "t:dot"), layer("star", 5, "t:dot"), layer("link2", 10, "t:dot"));
        EffectRecipe r = EffectRecipe.of("prism", EffectPriority.ACTION, Map.of(QualityTier.HEAVY, s),
                Map.of("trail", LayerRole.DECORATIVE, "star", LayerRole.ESSENTIAL));
        int[][] expected = { // cap -> kept per frame batch index {trail, link, star, link2}
            {3, 0, 0, 3, 0}, {5, 0, 0, 5, 0}, {6, 0, 1, 5, 0}, {20, 0, 10, 5, 5}, {35, 0, 20, 5, 10}, {36, 1, 20, 5, 10}, {75, 40, 20, 5, 10}};
        for (int[] e : expected) {
            EffectDelivery d = deliver(r, 0, e[0], 1);
            int[] kept = new int[4];
            for (int pos = 0; pos < d.orderedBatchCount(); pos++) {
                kept[d.orderedBatch(pos)] = d.kept(pos);
            }
            assertEquals(List.of(e[1], e[2], e[3], e[4]), List.of(kept[0], kept[1], kept[2], kept[3]), "cap " + e[0]);
            assertEquals(2, d.orderedBatch(0), "the essential batch comes first");
        }
    }

    @Test
    @DisplayName("Largest remainder: floor shares, then one point each to the largest (r n_b) mod T, ties to the earlier layer")
    void largestRemainder() {
        EffectScene s = scene(layer("a", 3, "t:dot"), layer("b", 7, "t:dot"));
        EffectRecipe r = EffectRecipe.of("lr", EffectPriority.NORMAL, Map.of(QualityTier.HEAVY, s), Map.of());
        // r = 5, T = 10: 15/10 and 35/10 have equal remainders 5: the earlier layer gets the extra point
        EffectDelivery d = deliver(r, 0, 5, 1);
        assertEquals(2, d.kept(0));
        assertEquals(3, d.kept(1));
        // r = 4: 12/10 rem 2, 28/10 rem 8: shares 1 and 2, the extra point to b
        d = deliver(r, 0, 4, 1);
        assertEquals(1, d.kept(0));
        assertEquals(3, d.kept(1));
        // r = 1: shares 0 and 0; remainders 3 and 7: b gets the only point and a is dropped from the list
        d = deliver(r, 0, 1, 1);
        assertEquals(1, d.orderedBatchCount());
        assertEquals(1, d.orderedBatch(0));
        // three equal layers, r = 2: ties go to the first two
        EffectScene three = scene(layer("x", 4, "t:dot"), layer("y", 4, "t:dot"), layer("z", 4, "t:dot"));
        d = deliver(EffectRecipe.of("eq", EffectPriority.NORMAL, Map.of(QualityTier.HEAVY, three), Map.of()), 0, 2, 1);
        assertEquals(2, d.orderedBatchCount());
        assertEquals(0, d.orderedBatch(0));
        assertEquals(1, d.orderedBatch(1));
    }

    @Test
    @DisplayName("Recipients receive a prefix of the selection sequence: a saturated viewer gets the essential points first")
    void prefixes() {
        EffectScene s = scene(layer("ring", 24, "t:dot"), layer("marker", 3, "t:dot"));
        EffectRecipe r = EffectRecipe.of("heal", EffectPriority.ACTION, Map.of(QualityTier.HEAVY, s), Map.of("marker", LayerRole.ESSENTIAL));
        EffectRecipe filler = EffectRecipe.of("filler", EffectPriority.ACTION, Map.of(QualityTier.HEAVY, scene(layer("main", 9, "t:dot"))), Map.of());
        // viewer 1 already received 9 points of a budget of 12: 3 left, exactly the markers
        List<DeliveryRequest> reqs = List.of(request(1, filler, QualityTier.HEAVY, 0, 0, 1, 1), request(2, r, QualityTier.HEAVY, 0, 0, 1, 1, 2));
        DeliveryPolicy pol = policy(1000, 1000, 1000, 12, 100);
        DeliveryPlan p = DeliveryPlanner.plan(0, reqs, DOT, pol);
        checkInvariants(reqs, DOT, pol, p);
        EffectDelivery d = p.deliveries().get(1);
        assertEquals(12, d.keptPoints(), "k is capped by the largest remaining viewer budget");
        assertEquals(3, d.recipientPoints(0));
        assertEquals(12, d.recipientPoints(1));
        assertEquals(15, d.placements());
        List<int[]> seq = sequence(d);
        for (int i = 0; i < 3; i++) {
            assertEquals(1, seq.get(i)[0], "the first three selections are the marker batch");
        }
        assertEquals(12, seq.size());
        assertEquals(Outcome.THINNED, d.outcome());
    }

    @Test
    @DisplayName("Thinning does not read or copy coordinates: the frame of a delivery is the M5 frame, and selections index into it")
    void indicesOnly() {
        EffectDelivery d = deliver(ring(50), 3, 7, 1);
        assertEquals(50, d.frame().batches().get(d.orderedBatch(0)).size());
        for (int j = 0; j < 7; j++) {
            d.frame().batches().get(d.orderedBatch(0)).point(d.selectedIndex(0, j)); // in range for the M5 accessor
        }
        assertEquals(Outcome.THINNED, d.outcome());
        assertEquals(Outcome.DELIVERED, DeliveryPlanner.plan(0, List.of(request(1, ring(50), QualityTier.HEAVY, 0, 3, 1, 1)), DOT, roomy())
                .deliveries().get(0).outcome());
    }
}
