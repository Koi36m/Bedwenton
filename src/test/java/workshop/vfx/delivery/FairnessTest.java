package workshop.vfx.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static workshop.vfx.delivery.M6TestSupport.checkInvariants;
import static workshop.vfx.delivery.M6TestSupport.policy;
import static workshop.vfx.delivery.M6TestSupport.request;
import static workshop.vfx.delivery.M6TestSupport.styles;
import static workshop.vfx.delivery.M6TestSupport.tiers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.scene.StyleKey;

/** Contract sections 5 and 8 (DM6-5): allocation order, rotation within a priority group, and strict priorities. */
class FairnessTest {
    private static final Set<StyleKey> DOT = styles("t:dot");
    private static final EffectRecipe ACTION = tiers("action", EffectPriority.ACTION, "t:dot", null, null, 10);
    private static final EffectRecipe NORMAL = tiers("normal", EffectPriority.NORMAL, "t:dot", null, null, 10);
    private static final EffectRecipe AMBIENT = tiers("ambient", EffectPriority.AMBIENT, "t:dot", null, null, 10);

    @Test
    @DisplayName("Allocation order: ACTION, NORMAL, AMBIENT; ids ascending, rotated by floorMod(tick, m) within each group")
    void order() {
        List<DeliveryRequest> reqs = List.of(request(30, AMBIENT, QualityTier.HEAVY, 0, 0, 1, 1), request(5, NORMAL, QualityTier.HEAVY, 0, 0, 1, 1),
                request(-4, ACTION, QualityTier.HEAVY, 0, 0, 1, 1), request(9, NORMAL, QualityTier.HEAVY, 0, 0, 1, 1),
                request(Long.MIN_VALUE, AMBIENT, QualityTier.HEAVY, 0, 0, 1, 1), request(7, NORMAL, QualityTier.HEAVY, 0, 0, 1, 1));
        Map<Long, long[]> expected = new HashMap<>(); // tick -> ids in allocation order
        expected.put(0L, new long[] {-4, 5, 7, 9, Long.MIN_VALUE, 30});
        expected.put(1L, new long[] {-4, 7, 9, 5, 30, Long.MIN_VALUE});
        expected.put(2L, new long[] {-4, 9, 5, 7, Long.MIN_VALUE, 30});
        expected.put(-1L, new long[] {-4, 9, 5, 7, 30, Long.MIN_VALUE});
        expected.put(Long.MIN_VALUE, new long[] {-4, 7, 9, 5, Long.MIN_VALUE, 30}); // floorMod(MIN, 3) = 1, floorMod(MIN, 2) = 0
        expected.put(Long.MAX_VALUE, new long[] {-4, 7, 9, 5, 30, Long.MIN_VALUE}); // floorMod(MAX, 3) = 1, floorMod(MAX, 2) = 1
        for (Map.Entry<Long, long[]> e : expected.entrySet()) {
            DeliveryPlan p = DeliveryPlanner.plan(e.getKey(), reqs, DOT, M6TestSupport.roomy());
            long[] byRank = new long[reqs.size()];
            for (EffectDelivery d : p.deliveries()) {
                byRank[d.allocationRank()] = d.requestId();
            }
            assertEquals(java.util.Arrays.toString(e.getValue()), java.util.Arrays.toString(byRank), "tick " + e.getKey());
        }
    }

    @Test
    @DisplayName("Rotation: with the same m requests for m consecutive ticks, each is first in its group exactly once and served once")
    void rotation() {
        for (long base : new long[] {0, 17, -9, Long.MIN_VALUE, Long.MAX_VALUE - 10}) {
            List<DeliveryRequest> reqs = new ArrayList<>();
            for (int i = 0; i < 7; i++) {
                reqs.add(request(100 + 3L * i, NORMAL, QualityTier.HEAVY, 0, 0, 1, 1));
            }
            // a budget for one effect only: the first in the group gets it
            DeliveryPolicy pol = policy(1000, 10, 1000, 1000, 100);
            Map<Long, Integer> firsts = new HashMap<>();
            for (int t = 0; t < 7; t++) {
                DeliveryPlan p = DeliveryPlanner.plan(base + t, reqs, DOT, pol);
                checkInvariants(reqs, DOT, pol, p);
                for (EffectDelivery d : p.deliveries()) {
                    if (d.allocationRank() == 0) {
                        firsts.merge(d.requestId(), 1, Integer::sum);
                        assertEquals(Outcome.DELIVERED, d.outcome());
                    } else {
                        assertEquals(Outcome.SKIPPED_DELIVERY_BUDGET, d.outcome());
                    }
                }
            }
            assertEquals(7, firsts.size(), "base " + base);
            assertTrue(firsts.values().stream().allMatch(c -> c == 1));
        }
    }

    @Test
    @DisplayName("Strict priority: ACTION is served before NORMAL whatever the ids, input order or tick; NORMAL has no reserve")
    void strictPriority() {
        DeliveryPolicy pol = policy(1000, 30, 1000, 1000, 100);
        for (long tick = -3; tick < 4; tick++) {
            List<DeliveryRequest> reqs = List.of(request(1, NORMAL, QualityTier.HEAVY, 0, 0, 1, 1), request(2, NORMAL, QualityTier.HEAVY, 0, 0, 1, 2),
                    request(50, ACTION, QualityTier.HEAVY, 0, 0, 1, 3), request(60, ACTION, QualityTier.HEAVY, 0, 0, 1, 4),
                    request(70, ACTION, QualityTier.HEAVY, 0, 0, 1, 5));
            DeliveryPlan p = DeliveryPlanner.plan(tick, reqs, DOT, pol);
            checkInvariants(reqs, DOT, pol, p);
            assertEquals(Outcome.SKIPPED_DELIVERY_BUDGET, p.deliveries().get(0).outcome());
            assertEquals(Outcome.SKIPPED_DELIVERY_BUDGET, p.deliveries().get(1).outcome());
            assertEquals(30, p.placements(), "the three action effects use the whole budget");
        }
    }

    @Test
    @DisplayName("Generation and placement are both decided in allocation order: a lower group never displaces a higher one")
    void phasesShareOrder() {
        EffectRecipe big = tiers("big", EffectPriority.AMBIENT, "t:dot", null, null, 50);
        EffectRecipe small = tiers("small", EffectPriority.ACTION, "t:dot", null, null, 50);
        List<DeliveryRequest> reqs = List.of(request(1, big, QualityTier.HEAVY, 0, 0, 1, 1), request(2, small, QualityTier.HEAVY, 0, 0, 1, 1));
        DeliveryPolicy pol = policy(50, 1000, 1000, 1000, 100);
        DeliveryPlan p = DeliveryPlanner.plan(0, reqs, DOT, pol);
        checkInvariants(reqs, DOT, pol, p);
        assertEquals(Outcome.SKIPPED_GENERATION_BUDGET, p.deliveries().get(0).outcome());
        assertEquals(Outcome.DELIVERED, p.deliveries().get(1).outcome());
        assertEquals(1, p.deliveries().get(0).allocationRank());
    }
}
