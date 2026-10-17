package workshop.vfx.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static workshop.vfx.delivery.M6TestSupport.checkInvariants;
import static workshop.vfx.delivery.M6TestSupport.fingerprint;
import static workshop.vfx.delivery.M6TestSupport.policy;
import static workshop.vfx.delivery.M6TestSupport.request;
import static workshop.vfx.delivery.M6TestSupport.styles;
import static workshop.vfx.delivery.M6TestSupport.tiers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.scene.StyleKey;

/** Contract sections 6 to 8: budget guarantees G1 to G4, G9 and G10, checked by independent recomputation. */
class BudgetPropertyTest {
    private static final Set<StyleKey> DOT = styles("t:dot");

    @Test
    @DisplayName("G1 to G5, G7, G9 and the phase-1 decisions hold for 3,000 random scenarios with binding budgets")
    void randomInvariants() {
        Random rnd = new Random(0x6d36L);
        int[] outcomes = new int[Outcome.values().length];
        for (int i = 0; i < 3000; i++) {
            M6TestSupport.Scenario s = M6TestSupport.randomScenario(rnd);
            DeliveryPlan plan = s.plan();
            checkInvariants(s.requests(), s.styles(), s.policy(), plan);
            for (Outcome o : Outcome.values()) {
                outcomes[o.ordinal()] += plan.count(o);
            }
        }
        for (Outcome o : Outcome.values()) {
            assertTrue(outcomes[o.ordinal()] >= 20, "every outcome exercised: " + o + " " + outcomes[o.ordinal()]);
        }
    }

    @Test
    @DisplayName("Input order is irrelevant: shuffling the request list permutes the deliveries and changes nothing else")
    void inputOrder() {
        Random rnd = new Random(77);
        for (int i = 0; i < 300; i++) {
            M6TestSupport.Scenario s = M6TestSupport.randomScenario(rnd);
            List<DeliveryRequest> shuffled = new ArrayList<>(s.requests());
            Collections.shuffle(shuffled, rnd);
            DeliveryPlan a = s.plan();
            DeliveryPlan b = DeliveryPlanner.plan(s.tick(), shuffled, s.styles(), s.policy());
            Map<Long, String> byId = new HashMap<>();
            for (EffectDelivery d : b.deliveries()) {
                byId.put(d.requestId(), one(d));
            }
            for (int k = 0; k < shuffled.size(); k++) {
                assertEquals(shuffled.get(k).id(), b.deliveries().get(k).requestId(), "input order kept");
            }
            for (EffectDelivery d : a.deliveries()) {
                assertEquals(one(d), byId.get(d.requestId()));
            }
            assertEquals(a.placements(), b.placements());
        }
    }

    private static String one(EffectDelivery d) {
        return M6TestSupport.line(d);
    }

    @Test
    @DisplayName("G3: the global, world and viewer budgets bind exactly; one viewer shared by three worlds is limited across worlds")
    void exactBudgets() {
        EffectRecipe r = tiers("r", EffectPriority.NORMAL, "t:dot", null, null, 30);
        // viewer 7 sees three effects in three worlds with a per-viewer budget of 45: 30 + 15 + 0
        List<DeliveryRequest> reqs = List.of(request(1, r, QualityTier.HEAVY, 0, 0, 1, 7), request(2, r, QualityTier.HEAVY, 1, 0, 1, 7),
                request(3, r, QualityTier.HEAVY, 2, 0, 1, 7));
        DeliveryPlan p = DeliveryPlanner.plan(0, reqs, DOT, policy(1000, 1000, 1000, 45, 100));
        checkInvariants(reqs, DOT, policy(1000, 1000, 1000, 45, 100), p);
        assertEquals(30, p.deliveries().get(0).placements());
        assertEquals(15, p.deliveries().get(1).placements());
        assertEquals(Outcome.SKIPPED_DELIVERY_BUDGET, p.deliveries().get(2).outcome());
        // world budget: 50 per world; two effects in world 0 with 2 viewers each: k = 25 then 0
        List<DeliveryRequest> w = List.of(request(1, r, QualityTier.HEAVY, 0, 0, 1, 1, 2), request(2, r, QualityTier.HEAVY, 0, 0, 1, 3, 4),
                request(3, r, QualityTier.HEAVY, 1, 0, 1, 5, 6));
        DeliveryPlan pw = DeliveryPlanner.plan(0, w, DOT, policy(1000, 1000, 50, 1000, 100));
        assertEquals(25, pw.deliveries().get(0).keptPoints());
        assertEquals(Outcome.SKIPPED_DELIVERY_BUDGET, pw.deliveries().get(1).outcome());
        assertEquals(25, pw.deliveries().get(2).keptPoints(), "a separate world has its own budget");
        // global budget: exactly equal to the demand, and one below
        List<DeliveryRequest> g = List.of(request(1, r, QualityTier.HEAVY, 0, 0, 1, 1, 2, 3));
        assertEquals(Outcome.DELIVERED, DeliveryPlanner.plan(0, g, DOT, policy(1000, 90, 1000, 1000, 100)).deliveries().get(0).outcome());
        EffectDelivery below = DeliveryPlanner.plan(0, g, DOT, policy(1000, 89, 1000, 1000, 100)).deliveries().get(0);
        assertEquals(Outcome.THINNED, below.outcome());
        assertEquals(29, below.keptPoints(), "3 * 29 = 87 <= 89 < 90");
        assertEquals(87, below.placements());
    }

    @Test
    @DisplayName("G9: a saturated viewer does not hold back the others; k grows until the next unit of cost exceeds the budget")
    void saturatedViewers() {
        EffectRecipe r = tiers("r", EffectPriority.NORMAL, "t:dot", null, null, 40);
        // viewer 1 has 5 left after a first effect; the second effect then costs min(k,5) + k + k
        List<DeliveryRequest> reqs = List.of(request(1, r, QualityTier.HEAVY, 0, 0, 1, 1), request(2, r, QualityTier.HEAVY, 1, 0, 1, 1, 2, 3));
        DeliveryPolicy pol = policy(1000, 1000, 1000, 45, 100);
        DeliveryPlan p = DeliveryPlanner.plan(0, reqs, DOT, pol);
        checkInvariants(reqs, DOT, pol, p);
        EffectDelivery d = p.deliveries().get(1);
        assertEquals(40, d.keptPoints());
        assertEquals(5, d.recipientPoints(0), "viewer 1 receives the first 5 selections only");
        assertEquals(40, d.recipientPoints(1));
        assertEquals(85, d.placements());
        // with a global budget of 60 for the second effect: 5 + 2k <= 60 gives k = 27
        DeliveryPolicy tight = new DeliveryPolicy(64, 16, 512, 1000, 0, 100, 0, 1000, 45, 100, 100, 100);
        DeliveryPlan t = DeliveryPlanner.plan(0, reqs, DOT, tight);
        checkInvariants(reqs, DOT, tight, t);
        assertEquals(27, t.deliveries().get(1).keptPoints());
        assertEquals(59, t.deliveries().get(1).placements());
        // a viewer with nothing left is not a recipient
        List<DeliveryRequest> zero = List.of(request(1, r, QualityTier.HEAVY, 0, 0, 1, 1), request(2, r, QualityTier.HEAVY, 0, 0, 1, 1, 2));
        DeliveryPolicy forty = policy(1000, 1000, 1000, 40, 100);
        EffectDelivery z = DeliveryPlanner.plan(0, zero, DOT, forty).deliveries().get(1);
        assertEquals(1, z.recipientCount());
        assertEquals(2, z.recipient(0));
    }

    @Test
    @DisplayName("G10: non-ambient effects never consume the placement reserve, however much they want")
    void placementReserve() {
        EffectRecipe action = tiers("action", EffectPriority.ACTION, "t:dot", null, null, 100);
        EffectRecipe ambient = tiers("ambient", EffectPriority.AMBIENT, "t:dot", null, null, 100);
        EffectRecipe normal = tiers("normal", EffectPriority.NORMAL, "t:dot", null, null, 100);
        for (long reserve : new long[] {0, 1, 37, 150, 400}) {
            List<DeliveryRequest> reqs = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                reqs.add(request(i, action, QualityTier.HEAVY, 0, 0, 1, 10 + i));
                reqs.add(request(100 + i, normal, QualityTier.HEAVY, 0, 0, 1, 20 + i));
            }
            reqs.add(request(200, ambient, QualityTier.HEAVY, 0, 0, 1, 30));
            reqs.add(request(201, ambient, QualityTier.HEAVY, 0, 0, 1, 31));
            DeliveryPolicy pol = new DeliveryPolicy(64, 16, 512, 10_000, 0, 400, reserve, 10_000, 10_000, 100, 100, 100);
            DeliveryPlan p = DeliveryPlanner.plan(0, reqs, DOT, pol);
            checkInvariants(reqs, DOT, pol, p);
            long ambientPlaced = 0, nonAmbient = 0;
            for (int i = 0; i < reqs.size(); i++) {
                long placed = p.deliveries().get(i).placements();
                if (reqs.get(i).recipe().priority() == EffectPriority.AMBIENT) {
                    ambientPlaced += placed;
                } else {
                    nonAmbient += placed;
                }
            }
            assertEquals(400 - reserve, nonAmbient, "action and normal take everything outside the reserve, and nothing inside it");
            assertTrue(ambientPlaced <= reserve, "here the ambient effects can only use the reserve: " + ambientPlaced);
            // NORMAL has no reserve: with reserve 0..400 the four action effects take the first 400 - reserve
            assertEquals(Math.min(400 - reserve, 400), p.deliveries().get(0).placements() + p.deliveries().get(2).placements()
                    + p.deliveries().get(4).placements() + p.deliveries().get(6).placements());
        }
    }

    @Test
    @DisplayName("G10 counterexample 1 (R2): generation reservations are indivisible, so ambient may realize less than min(reserve, demand)")
    void reserveCounterexampleGeneration() {
        EffectRecipe act70 = tiers("act70", EffectPriority.ACTION, "t:dot", null, null, 70);
        EffectRecipe amb20 = tiers("amb20", EffectPriority.AMBIENT, "t:dot", null, null, 20);
        EffectRecipe amb31 = tiers("amb31", EffectPriority.AMBIENT, "t:dot", null, null, 31);
        DeliveryPolicy pol = new DeliveryPolicy(64, 16, 512, 100, 30, 10_000, 0, 10_000, 10_000, 100, 100, 100);
        // two ambient scenes of 20 after action reserved 70: the second needs 20 of the 10 remaining
        List<DeliveryRequest> two = List.of(request(1, act70, QualityTier.HEAVY, 0, 0, 1, 1), request(2, amb20, QualityTier.HEAVY, 0, 0, 1, 2),
                request(3, amb20, QualityTier.HEAVY, 0, 0, 1, 3));
        DeliveryPlan p = DeliveryPlanner.plan(0, two, DOT, pol);
        checkInvariants(two, DOT, pol, p);
        long ambientReserved = p.deliveries().get(1).reservedGeneratedPoints() + p.deliveries().get(2).reservedGeneratedPoints();
        assertEquals(20, ambientReserved);
        assertTrue(ambientReserved < Math.min(30, 40), "the R1 claim min(reserve, demand) = 30 does not hold");
        assertEquals(Outcome.SKIPPED_GENERATION_BUDGET, p.deliveries().get(2).outcome());
        assertEquals(70, p.deliveries().get(0).reservedGeneratedPoints(), "action used exactly G - R_g; the reserve was intact");
        // one ambient scene of 31 > reserve 30: realized 0
        List<DeliveryRequest> one = List.of(request(1, act70, QualityTier.HEAVY, 0, 0, 1, 1), request(2, amb31, QualityTier.HEAVY, 0, 0, 1, 2));
        DeliveryPlan q = DeliveryPlanner.plan(0, one, DOT, pol);
        checkInvariants(one, DOT, pol, q);
        assertEquals(Outcome.SKIPPED_GENERATION_BUDGET, q.deliveries().get(1).outcome());
        assertEquals(0, q.deliveries().get(1).reservedGeneratedPoints(), "realized 0 < min(30, 31)");
    }

    @Test
    @DisplayName("G10 counterexample 2 (R2): placement cost is per viewer, so ambient may realize less than min(reserve, demand)")
    void reserveCounterexamplePlacement() {
        EffectRecipe act95 = tiers("act95", EffectPriority.ACTION, "t:dot", null, null, 95);
        EffectRecipe amb10 = tiers("amb10", EffectPriority.AMBIENT, "t:dot", null, null, 10);
        DeliveryPolicy pol = new DeliveryPolicy(64, 16, 512, 10_000, 0, 100, 5, 10_000, 10_000, 100, 100, 100);
        List<DeliveryRequest> three = List.of(request(1, act95, QualityTier.HEAVY, 0, 0, 1, 100), request(2, amb10, QualityTier.HEAVY, 0, 0, 1, 0, 1, 2));
        DeliveryPlan p = DeliveryPlanner.plan(0, three, DOT, pol);
        checkInvariants(three, DOT, pol, p);
        assertEquals(95, p.deliveries().get(0).placements(), "action used exactly P - R_p; the reserve was intact");
        EffectDelivery a = p.deliveries().get(1);
        assertEquals(1, a.keptPoints(), "3 viewers: 3k <= 5 gives k = 1");
        assertEquals(3, a.placements());
        assertTrue(a.placements() < Math.min(5, 30), "the R1 claim min(reserve, demand) = 5 does not hold");
        List<DeliveryRequest> six = List.of(request(1, act95, QualityTier.HEAVY, 0, 0, 1, 100),
                request(2, amb10, QualityTier.HEAVY, 0, 0, 1, 0, 1, 2, 3, 4, 5));
        DeliveryPlan q = DeliveryPlanner.plan(0, six, DOT, pol);
        checkInvariants(six, DOT, pol, q);
        assertEquals(Outcome.SKIPPED_DELIVERY_BUDGET, q.deliveries().get(1).outcome(), "6 viewers: 6k <= 5 gives k = 0");
        assertEquals(0, q.placements() - 95);
    }
}
