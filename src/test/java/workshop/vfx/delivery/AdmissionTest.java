package workshop.vfx.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static workshop.vfx.delivery.M6TestSupport.checkInvariants;
import static workshop.vfx.delivery.M6TestSupport.failing;
import static workshop.vfx.delivery.M6TestSupport.layer;
import static workshop.vfx.delivery.M6TestSupport.policy;
import static workshop.vfx.delivery.M6TestSupport.request;
import static workshop.vfx.delivery.M6TestSupport.roomy;
import static workshop.vfx.delivery.M6TestSupport.scene;
import static workshop.vfx.delivery.M6TestSupport.styles;
import static workshop.vfx.delivery.M6TestSupport.tiers;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.math.time.TimingSample;
import workshop.vfx.scene.EffectScene;
import workshop.vfx.scene.Reveal;
import workshop.vfx.scene.StyleKey;

/** Contract section 6: quality selection and preflight admission before any M5 evaluation. */
class AdmissionTest {
    private static final Set<StyleKey> DOT = styles("t:dot");
    private static final EffectRecipe THREE = tiers("three", EffectPriority.NORMAL, "t:dot", 10, 20, 40);

    private static EffectDelivery only(DeliveryPlan plan) {
        return plan.deliveries().get(0);
    }

    private static DeliveryPlan plan(List<DeliveryRequest> reqs, Set<StyleKey> st, DeliveryPolicy pol) {
        DeliveryPlan plan = DeliveryPlanner.plan(0, reqs, st, pol);
        checkInvariants(reqs, st, pol, plan);
        return plan;
    }

    @Test
    @DisplayName("Downgrade only: the highest tier at most the requested one that fits the generation budget")
    void downgrade() {
        int[][] cases = {{40, 2}, {39, 1}, {20, 1}, {19, 0}, {10, 0}};
        for (int[] c : cases) {
            EffectDelivery d = only(plan(List.of(request(1, THREE, QualityTier.HEAVY, 0, 0, 1, 1)), DOT, policy(c[0], 1000, 1000, 1000, 1000)));
            assertEquals(QualityTier.values()[c[1]], d.tier(), "budget " + c[0]);
            assertEquals(new int[] {10, 20, 40}[c[1]], d.reservedGeneratedPoints());
        }
        EffectDelivery none = only(plan(List.of(request(1, THREE, QualityTier.HEAVY, 0, 0, 1, 1)), DOT, policy(9, 1000, 1000, 1000, 1000)));
        assertEquals(Outcome.SKIPPED_GENERATION_BUDGET, none.outcome());
        assertNull(none.tier());
        assertNull(none.frame());
        // never upgraded above the request, however large the budget
        assertEquals(QualityTier.SIMPLE, only(plan(List.of(request(1, THREE, QualityTier.SIMPLE, 0, 0, 1, 1)), DOT, roomy())).tier());
        assertEquals(QualityTier.BALANCED, only(plan(List.of(request(1, THREE, QualityTier.BALANCED, 0, 0, 1, 1)), DOT, roomy())).tier());
    }

    @Test
    @DisplayName("G11 strict ceiling: the next lower tier when one is missing; never a higher tier; none at or below is SKIPPED_NO_ELIGIBLE_TIER")
    void missingTiers() {
        EffectRecipe gap = tiers("gap", EffectPriority.NORMAL, "t:dot", 5, null, 50);
        assertEquals(QualityTier.SIMPLE, only(plan(List.of(request(1, gap, QualityTier.BALANCED, 0, 0, 1, 1)), DOT, roomy())).tier());
        EffectRecipe high = tiers("high", EffectPriority.NORMAL, "t:dot", null, 30, 60);
        for (DeliveryPolicy pol : List.of(roomy(), policy(29, 1000, 1000, 1000, 1000), policy(0, 0, 0, 0, 0))) {
            EffectDelivery d = only(plan(List.of(request(1, high, QualityTier.SIMPLE, 0, 0, 1, 1)), DOT, pol));
            assertEquals(Outcome.SKIPPED_NO_ELIGIBLE_TIER, d.outcome(), "a SIMPLE request never selects BALANCED or HEAVY, whatever the budget");
            assertNull(d.tier());
            assertNull(d.frame());
            assertEquals(0, d.reservedGeneratedPoints());
        }
        EffectRecipe heavyOnly = tiers("heavy-only", EffectPriority.ACTION, "t:dot", null, null, 8);
        assertEquals(Outcome.SKIPPED_NO_ELIGIBLE_TIER, only(plan(List.of(request(1, heavyOnly, QualityTier.BALANCED, 0, 0, 1, 1)), DOT, roomy())).outcome());
        assertEquals(QualityTier.HEAVY, only(plan(List.of(request(1, heavyOnly, QualityTier.HEAVY, 0, 0, 1, 1)), DOT, roomy())).tier());
    }

    @Test
    @DisplayName("SKIPPED_NO_ELIGIBLE_TIER: decided after the viewer check and before styles and budget; never evaluated; reserves nothing")
    void noEligibleTierOrder() {
        EffectRecipe high = tiers("high", EffectPriority.NORMAL, "t:odd", null, 30, 60);
        EffectRecipe next = tiers("next", EffectPriority.NORMAL, "t:dot", 10, 20, 40);
        // no viewers wins over no eligible tier; no eligible tier wins over unsupported styles and a zero budget;
        // a failing anchor shows that the request is never evaluated; the next request keeps the whole budget
        List<DeliveryRequest> reqs = List.of(failing(1, high, QualityTier.SIMPLE, 0, 0, 1), failing(2, high, QualityTier.SIMPLE, 0, 0, 1, 1),
                request(3, next, QualityTier.HEAVY, 0, 0, 1, 1));
        DeliveryPlan p = plan(reqs, DOT, policy(40, 1000, 1000, 1000, 1000));
        assertEquals(Outcome.SKIPPED_NO_VIEWERS, p.deliveries().get(0).outcome());
        assertEquals(Outcome.SKIPPED_NO_ELIGIBLE_TIER, p.deliveries().get(1).outcome());
        assertEquals(QualityTier.HEAVY, p.deliveries().get(2).tier(), "nothing was reserved for request 2");
        assertEquals(1, p.count(Outcome.SKIPPED_NO_ELIGIBLE_TIER));
        assertEquals(0, p.count(Outcome.FAILED_EVALUATION));
        assertEquals(40, p.reservedGeneratedPoints());
    }

    @Test
    @DisplayName("Deliverability: a variant without a supported, non-empty layer is passed over; none deliverable is SKIPPED_UNSUPPORTED")
    void deliverability() {
        EffectRecipe mixed = EffectRecipe.of("mixed", EffectPriority.NORMAL, Map.of(
                QualityTier.HEAVY, scene(layer("main", 40, "t:fancy")),
                QualityTier.BALANCED, scene(layer("main", 20, "t:dot"), layer("empty", 0, "t:fancy")),
                QualityTier.SIMPLE, scene(layer("main", 0, "t:dot"), layer("other", 3, "t:fancy"))), Map.of());
        assertEquals(QualityTier.BALANCED, only(plan(List.of(request(1, mixed, QualityTier.HEAVY, 0, 0, 1, 1)), DOT, roomy())).tier());
        EffectDelivery d = only(plan(List.of(request(1, mixed, QualityTier.SIMPLE, 0, 0, 1, 1)), DOT, roomy()));
        assertEquals(Outcome.SKIPPED_UNSUPPORTED, d.outcome(), "an empty shape with a supported style is not deliverable");
        assertNull(d.tier());
        assertNull(d.frame());
        // unsupported is decided before the budget: an over-budget, undeliverable request is SKIPPED_UNSUPPORTED
        assertEquals(Outcome.SKIPPED_UNSUPPORTED, only(plan(List.of(request(1, mixed, QualityTier.HEAVY, 0, 0, 1, 1)), styles("x:y"),
                policy(0, 0, 0, 0, 0))).outcome());
        // a deliverable but over-budget higher tier does not block a fitting lower one
        assertEquals(QualityTier.SIMPLE, only(plan(List.of(request(1, mixed, QualityTier.HEAVY, 0, 0, 1, 1)), styles("t:fancy"),
                policy(39, 1000, 1000, 1000, 1000))).tier());
    }

    @Test
    @DisplayName("Skipped requests are never evaluated: a failing anchor with no viewers, unsupported styles or no budget is not FAILED_EVALUATION")
    void skippedNotEvaluated() {
        List<DeliveryRequest> reqs = List.of(failing(1, THREE, QualityTier.HEAVY, 0, 0, 1),
                failing(2, tiers("odd", EffectPriority.NORMAL, "t:odd", 1, 1, 1), QualityTier.HEAVY, 0, 0, 1, 1),
                failing(3, THREE, QualityTier.HEAVY, 0, 0, 1, 1));
        DeliveryPlan p = plan(reqs, DOT, policy(9, 1000, 1000, 1000, 1000));
        assertEquals(Outcome.SKIPPED_NO_VIEWERS, p.deliveries().get(0).outcome());
        assertEquals(Outcome.SKIPPED_UNSUPPORTED, p.deliveries().get(1).outcome());
        assertEquals(Outcome.SKIPPED_GENERATION_BUDGET, p.deliveries().get(2).outcome());
        for (EffectDelivery d : p.deliveries()) {
            assertNull(d.frame());
            assertEquals(0, d.reservedGeneratedPoints());
        }
        assertEquals(0, p.count(Outcome.FAILED_EVALUATION));
    }

    @Test
    @DisplayName("FAILED_EVALUATION is isolated: its reservation stays counted, and later requests are planned normally")
    void failureIsolated() {
        List<DeliveryRequest> reqs = List.of(failing(1, THREE, QualityTier.HEAVY, 0, 0, 1, 1), request(2, THREE, QualityTier.HEAVY, 0, 0, 1, 1),
                request(3, THREE, QualityTier.HEAVY, 0, 0, 1, 1));
        DeliveryPlan p = plan(reqs, DOT, policy(80, 1000, 1000, 1000, 1000));
        EffectDelivery failed = p.deliveries().get(0);
        assertEquals(Outcome.FAILED_EVALUATION, failed.outcome());
        assertEquals(QualityTier.HEAVY, failed.tier());
        assertEquals(40, failed.reservedGeneratedPoints());
        assertNull(failed.frame());
        assertEquals(Outcome.DELIVERED, p.deliveries().get(1).outcome());
        assertEquals(Outcome.SKIPPED_GENERATION_BUDGET, p.deliveries().get(2).outcome(), "40 + 40 reserved: nothing left");
        assertEquals(80, p.reservedGeneratedPoints());
    }

    @Test
    @DisplayName("SKIPPED_INACTIVE: before the start, at or after the lifetime, every layer ended, and an empty prefix; the frame is kept")
    void inactive() {
        EffectScene s = scene(10, layer("short", 4, "t:dot", Reveal.ALL, 3), layer("grow", 4, "t:dot", Reveal.PREFIX, 10));
        EffectRecipe r = EffectRecipe.of("r", EffectPriority.NORMAL, Map.of(QualityTier.HEAVY, s), Map.of());
        Object[][] cases = {{-1.0, TimingSample.Stage.BEFORE}, {10.0, TimingSample.Stage.AFTER}, {1e9, TimingSample.Stage.AFTER}};
        for (Object[] c : cases) {
            EffectDelivery d = only(plan(List.of(request(1, r, QualityTier.HEAVY, 0, 0, (double) c[0], 1)), DOT, roomy()));
            assertEquals(Outcome.SKIPPED_INACTIVE, d.outcome(), "local " + c[0]);
            assertEquals(c[1], d.frame().stage());
            assertEquals(8, d.reservedGeneratedPoints(), "the reservation is charged before evaluation");
        }
        EffectScene ended = scene(10, layer("short", 4, "t:dot", Reveal.ALL, 3));
        EffectRecipe e = EffectRecipe.of("e", EffectPriority.NORMAL, Map.of(QualityTier.HEAVY, ended), Map.of());
        EffectDelivery d = only(plan(List.of(request(1, e, QualityTier.HEAVY, 0, 0, 5, 1)), DOT, roomy()));
        assertEquals(Outcome.SKIPPED_INACTIVE, d.outcome());
        assertEquals(TimingSample.Stage.ACTIVE, d.frame().stage());
        assertEquals(0, d.frame().pointCount());
        EffectScene grow = scene(10, layer("grow", 4, "t:dot", Reveal.PREFIX, 10));
        EffectRecipe g = EffectRecipe.of("g", EffectPriority.NORMAL, Map.of(QualityTier.HEAVY, grow), Map.of());
        assertEquals(Outcome.SKIPPED_INACTIVE, only(plan(List.of(request(1, g, QualityTier.HEAVY, 0, 0, 2, 1)), DOT, roomy())).outcome(),
                "floor(0.2 * 4) = 0 points");
        assertEquals(Outcome.DELIVERED, only(plan(List.of(request(1, g, QualityTier.HEAVY, 0, 0, 2.5, 1)), DOT, roomy())).outcome());
    }

    @Test
    @DisplayName("Phase 2 SKIPPED_UNSUPPORTED: admitted (a supported layer exists) but only unsupported layers are visible now")
    void unsupportedNow() {
        EffectScene s = scene(10, layer("early", 3, "t:dot", Reveal.ALL, 2), layer("late", 5, "t:fancy", Reveal.ALL, 10));
        EffectRecipe r = EffectRecipe.of("r", EffectPriority.NORMAL, Map.of(QualityTier.HEAVY, s), Map.of());
        EffectDelivery d = only(plan(List.of(request(1, r, QualityTier.HEAVY, 0, 0, 5, 1)), DOT, roomy()));
        assertEquals(Outcome.SKIPPED_UNSUPPORTED, d.outcome());
        assertEquals(QualityTier.HEAVY, d.tier());
        assertNotNull(d.frame());
        assertEquals(5, d.unsupportedPoints());
        assertEquals(5, d.sourcePoints());
        EffectDelivery early = only(plan(List.of(request(1, r, QualityTier.HEAVY, 0, 0, 1, 1)), DOT, roomy()));
        assertEquals(Outcome.THINNED, early.outcome(), "an unsupported batch makes a full delivery THINNED");
        assertEquals(3, early.keptPoints());
        assertEquals(5, early.unsupportedPoints());
    }

    @Test
    @DisplayName("Generation reserve: non-ambient requests can never use it; ambient requests can use the whole budget")
    void generationReserve() {
        EffectRecipe action = tiers("action", EffectPriority.ACTION, "t:dot", null, null, 30);
        EffectRecipe ambient = tiers("ambient", EffectPriority.AMBIENT, "t:dot", null, null, 30);
        DeliveryPolicy pol = new DeliveryPolicy(16, 16, 256, 100, 41, 1000, 0, 1000, 1000, 100, 100, 100);
        List<DeliveryRequest> reqs = List.of(request(1, action, QualityTier.HEAVY, 0, 0, 1, 1), request(2, action, QualityTier.HEAVY, 0, 0, 1, 1),
                request(3, ambient, QualityTier.HEAVY, 0, 0, 1, 1), request(4, ambient, QualityTier.HEAVY, 0, 0, 1, 1));
        DeliveryPlan p = plan(reqs, DOT, pol);
        assertEquals(Outcome.DELIVERED, p.deliveries().get(0).outcome(), "30 <= 100 - 41");
        assertEquals(Outcome.SKIPPED_GENERATION_BUDGET, p.deliveries().get(1).outcome(), "30 > 100 - 41 - 30: the reserve is untouchable");
        assertEquals(Outcome.DELIVERED, p.deliveries().get(2).outcome(), "30 <= 100 - 30");
        assertEquals(Outcome.DELIVERED, p.deliveries().get(3).outcome(), "30 <= 100 - 60");
        assertEquals(90, p.reservedGeneratedPoints());
    }
}
