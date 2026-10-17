package workshop.vfx.paper.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static workshop.vfx.paper.core.DemoFixture.ANY_POINT;
import static workshop.vfx.paper.core.DemoFixture.CATALOGUE;
import static workshop.vfx.paper.core.DemoFixture.context;
import static workshop.vfx.paper.core.DemoFixture.ring;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.delivery.DeliveryPlan;
import workshop.vfx.delivery.DeliveryPlanner;
import workshop.vfx.delivery.DeliveryPolicy;
import workshop.vfx.delivery.DeliveryRequest;
import workshop.vfx.delivery.QualityTier;

/** R2 F2: emission honours M6 allocation order; aligned M6 budgets keep the defensive caps from binding (contract 7, 9.1). */
class AllocationOrderEmissionTest {
    private static final long VIEWER = 5;

    /** Ring 1 at x = 0.5, ring 2 at x = 20.5 (distinguishable by position); both HEAVY, one shared viewer. */
    private static List<DeliveryRequest> rings(long tick, boolean ring2First) {
        DeliveryRequest r1 = ring(1, QualityTier.HEAVY, 0, 0.5, tick, VIEWER);
        DeliveryRequest r2 = ring(2, QualityTier.HEAVY, 0, 20.5, tick, VIEWER);
        return ring2First ? List.of(r2, r1) : List.of(r1, r2);
    }

    private static Map<Long, RequestContext> contexts() {
        return Map.of(1L, context(1, VIEWER, 0, 0.5), 2L, context(2, VIEWER, 0, 20.5));
    }

    private static DemoFixture.Viewers viewer() {
        return new DemoFixture.Viewers().put(VIEWER, true, true);
    }

    /** Which ring's points the viewer actually received, as a count per ring id. */
    private static Map<Long, Long> visible(DemoFixture.Sink sink) {
        Map<Long, Long> m = new HashMap<>();
        for (DemoFixture.Send s : sink.sends) {
            m.merge(s.x() < 10 ? 1L : 2L, 1L, Long::sum);
        }
        return m;
    }

    @Test
    @DisplayName("Defensive caps binding (unaligned policy): the rank-0 ring is emitted first, whatever the input order; rotation alternates")
    void rankNotInputOrder() {
        // deliberately unaligned: M6 allows 144 placements, the emitter allows 72 calls
        DeliveryPolicy unaligned = new DeliveryPolicy(16, 16, 256, 1152, 0, 4096, 0, 4096, 1024, 72, 72, 72);
        EmissionLimits caps = new EmissionLimits(72, 72);
        for (long tick = 0; tick < 4; tick++) {
            long expectedWinner = Math.floorMod(tick, 2) == 0 ? 1 : 2; // ACTION group ids {1, 2} rotated by floorMod(tick, 2)
            for (boolean ring2First : new boolean[] {true, false}) {
                DeliveryPlan p = DeliveryPlanner.plan(tick, rings(tick, ring2First), CATALOGUE, unaligned);
                DemoFixture.Sink sink = new DemoFixture.Sink();
                EmissionReport r = new DefaultPlanEmitter().emit(p, contexts(), new SessionStyleHealth(CATALOGUE), viewer().gate(),
                        ANY_POINT, sink, caps);
                assertEquals(Map.of(expectedWinner, 72L), visible(sink), "tick " + tick + ", ring2First " + ring2First);
                assertEquals(72L, r.skippedPoints().get(SkipReason.EMISSION_LIMIT));
            }
        }
    }

    @Test
    @DisplayName("Aligned policy (placement and viewer budgets capped by the emission limits): M6 decides, the caps never bind")
    void alignedPolicy() {
        EmissionLimits caps = new EmissionLimits(72, 72);
        // alignment rule of contract 9.1: placement, world and viewer budgets are at most the corresponding emission caps
        DeliveryPolicy aligned = new DeliveryPolicy(16, 16, 256, 1152, 0, 72, 0, 72, 72, 72, 72, 72);
        for (long tick = 0; tick < 4; tick++) {
            long expectedWinner = Math.floorMod(tick, 2) == 0 ? 1 : 2;
            DeliveryPlan p = DeliveryPlanner.plan(tick, rings(tick, true), CATALOGUE, aligned);
            DemoFixture.Sink sink = new DemoFixture.Sink();
            EmissionReport r = new DefaultPlanEmitter().emit(p, contexts(), new SessionStyleHealth(CATALOGUE), viewer().gate(),
                    ANY_POINT, sink, caps);
            assertEquals(Map.of(expectedWinner, 72L), visible(sink));
            assertEquals(null, r.skippedPoints().get(SkipReason.EMISSION_LIMIT));
            assertEquals(p.placements(), r.attemptedCalls(), "everything M6 planned was attempted");
        }
    }

    @Test
    @DisplayName("Aligned policy over 300 random multi-ring scenarios: no EMISSION_LIMIT skip, attempted calls = planned placements")
    void alignedRandom() {
        Random rnd = new Random(7);
        for (int i = 0; i < 300; i++) {
            int maxCalls = 1 + rnd.nextInt(400), perViewer = 1 + rnd.nextInt(150);
            DeliveryPolicy aligned = new DeliveryPolicy(16, 16, 256, 1152, 0, Math.min(2048, maxCalls), 0,
                    Math.min(2048, maxCalls), Math.min(256, perViewer), 24, 48, 72);
            List<DeliveryRequest> reqs = new ArrayList<>();
            Map<Long, RequestContext> ctx = new HashMap<>();
            DemoFixture.Viewers v = new DemoFixture.Viewers();
            int rings = 1 + rnd.nextInt(16);
            for (int k = 0; k < rings; k++) {
                List<Long> ids = new ArrayList<>();
                for (long id = 0; id < 16; id++) {
                    if (rnd.nextInt(3) == 0) {
                        ids.add(id);
                        v.put(id, true, true);
                    }
                }
                if (ids.isEmpty()) {
                    ids.add(0L);
                    v.put(0L, true, true);
                }
                long tick = rnd.nextInt(10);
                reqs.add(ring(100 + k, QualityTier.values()[rnd.nextInt(3)], 0, 0.5, tick, ids.stream().mapToLong(Long::longValue).toArray()));
                ctx.put(100L + k, context(100 + k, ids.get(0), 0, 0.5));
            }
            DeliveryPlan p = DeliveryPlanner.plan(rnd.nextInt(10), reqs, CATALOGUE, aligned);
            EmissionReport r = new DefaultPlanEmitter().emit(p, ctx, new SessionStyleHealth(CATALOGUE), v.gate(), ANY_POINT,
                    new DemoFixture.Sink(), new EmissionLimits(maxCalls, perViewer));
            assertEquals(null, r.skippedPoints().get(SkipReason.EMISSION_LIMIT), "scenario " + i);
            assertEquals(p.placements(), r.attemptedCalls(), "scenario " + i);
            assertTrue(r.attemptedCalls() <= maxCalls);
        }
    }
}
