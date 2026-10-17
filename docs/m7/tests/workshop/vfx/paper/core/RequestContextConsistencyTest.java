package workshop.vfx.paper.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static workshop.vfx.paper.core.DemoFixture.ANY_POINT;
import static workshop.vfx.paper.core.DemoFixture.CATALOGUE;
import static workshop.vfx.paper.core.DemoFixture.context;
import static workshop.vfx.paper.core.DemoFixture.ring;
import static workshop.vfx.paper.core.DemoFixture.roomy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.delivery.DeliveryPlan;
import workshop.vfx.delivery.DeliveryPlanner;
import workshop.vfx.delivery.QualityTier;

/**
 * R3: request-context consistency and fail-closed coordinates at the emitter (contract section 7). Plans come from the
 * real accepted DeliveryPlanner; the emitter, rule and style health are the scratch reference.
 */
class RequestContextConsistencyTest {
    private static final long A = 0, B = 1;
    private static final long RING_A = 10, RING_B = 11;
    private static final EmissionLimits LIMITS = new EmissionLimits(4096, 1024);

    /** Ring A (owner A, SIMPLE: 24 points) and ring B (owner B, HEAVY: 72 points); both visible to A and B. */
    private static DeliveryPlan plan() {
        return DeliveryPlanner.plan(0, List.of(ring(RING_A, QualityTier.SIMPLE, 0, 0.5, 0, A, B),
                ring(RING_B, QualityTier.HEAVY, 0, 0.5, 0, A, B)), CATALOGUE, roomy());
    }

    private static Map<Long, RequestContext> valid() {
        Map<Long, RequestContext> m = new HashMap<>();
        m.put(RING_A, context(RING_A, A, 0, 0.5));
        m.put(RING_B, context(RING_B, B, 0, 0.5));
        return m;
    }

    private static DemoFixture.Viewers viewers() {
        return new DemoFixture.Viewers().put(A, true, false).put(B, true, false);
    }

    private static EmissionReport emit(DeliveryPlan p, Map<Long, RequestContext> ctx, DemoFixture.Viewers v, DemoFixture.Sink sink) {
        return new DefaultPlanEmitter().emit(p, ctx, new SessionStyleHealth(CATALOGUE), v.gate(), ANY_POINT, sink, LIMITS);
    }

    /** Every planned placement is accounted for exactly once (contract section 7.2). */
    private static void assertAccounting(DeliveryPlan p, EmissionReport r) {
        long skippedPoints = r.skippedPoints().values().stream().mapToLong(Long::longValue).sum();
        assertEquals(p.placements(), r.plannedPlacements());
        assertEquals(r.plannedPlacements(),
                r.attemptedCalls() + skippedPoints + r.placementsInSkippedRecipients() + r.placementsInSkippedDeliveries());
        assertEquals(r.attemptedCalls(), r.succeededCalls() + r.failedCalls());
    }

    @Test
    @DisplayName("Control: valid contexts; each owner sees its own ring; non-owners without view are refused; accounting balances")
    void validContextsUnaffected() {
        DemoFixture.Sink sink = new DemoFixture.Sink();
        DeliveryPlan p = plan();
        EmissionReport r = emit(p, valid(), viewers(), sink);
        assertEquals(24, sink.count(A));
        assertEquals(72, sink.count(B));
        assertEquals(2L, r.skippedRecipients().get(SkipReason.VIEWER_PERMISSION_REVOKED));
        assertEquals(72 + 24, r.placementsInSkippedRecipients());
        assertEquals(0, r.placementsInSkippedDeliveries());
        assertAccounting(p, r);
    }

    @Test
    @DisplayName("Swapped contexts (key ring A holds ring B's context with owner B, and vice versa): both deliveries rejected, nothing sent")
    void swappedContexts() {
        Map<Long, RequestContext> swapped = Map.of(RING_A, context(RING_B, B, 0, 0.5), RING_B, context(RING_A, A, 0, 0.5));
        DemoFixture.Sink sink = new DemoFixture.Sink();
        DeliveryPlan p = plan();
        EmissionReport r = emit(p, swapped, viewers(), sink);
        assertEquals(0, sink.sends.size(), "a swapped owner would otherwise authorize B for ring A");
        assertEquals(2L, r.skippedDeliveries().get(SkipReason.MISMATCHED_REQUEST_CONTEXT));
        assertEquals(0, r.attemptedCalls());
        assertEquals(p.placements(), r.placementsInSkippedDeliveries());
        assertNull(r.skippedRecipients().get(SkipReason.VIEWER_PERMISSION_REVOKED), "rejected before any recipient check");
        assertAccounting(p, r);
    }

    @Test
    @DisplayName("One mismatched context (ring B's slot names ring A, owner A): only ring B is rejected; ring A is unaffected")
    void oneMismatch() {
        Map<Long, RequestContext> ctx = valid();
        ctx.put(RING_B, context(RING_A, A, 0, 0.5));
        DemoFixture.Sink sink = new DemoFixture.Sink();
        DeliveryPlan p = plan();
        EmissionReport r = emit(p, ctx, viewers(), sink);
        assertEquals(24, sink.count(A), "ring A to its owner");
        assertEquals(0, sink.count(B), "B receives nothing: its ring was rejected, and it may not view ring A");
        assertEquals(1L, r.skippedDeliveries().get(SkipReason.MISMATCHED_REQUEST_CONTEXT));
        assertEquals(72 * 2, r.placementsInSkippedDeliveries());
        assertAccounting(p, r);
    }

    @Test
    @DisplayName("Invalid contexts (NaN or infinite anchor, negative owner or world id): the delivery is rejected whole")
    void invalidContexts() {
        RequestContext[] bad = {
            new RequestContext(RING_B, B, 0, Double.NaN, 64.1, 0.5),
            new RequestContext(RING_B, B, 0, 0.5, Double.POSITIVE_INFINITY, 0.5),
            new RequestContext(RING_B, B, 0, 0.5, 64.1, Double.NEGATIVE_INFINITY),
            new RequestContext(RING_B, -1, 0, 0.5, 64.1, 0.5),
            new RequestContext(RING_B, B, -1, 0.5, 64.1, 0.5),
        };
        for (RequestContext c : bad) {
            Map<Long, RequestContext> ctx = valid();
            ctx.put(RING_B, c);
            DemoFixture.Sink sink = new DemoFixture.Sink();
            DeliveryPlan p = plan();
            EmissionReport r = emit(p, ctx, viewers(), sink);
            assertEquals(24, sink.count(A), c.toString());
            assertEquals(0, sink.count(B), c.toString());
            assertEquals(1L, r.skippedDeliveries().get(SkipReason.INVALID_REQUEST_CONTEXT), c.toString());
            assertAccounting(p, r);
        }
    }

    @Test
    @DisplayName("A viewer with a NaN position is refused (VIEWER_POSITION_INVALID); the other viewers are unaffected")
    void nanViewerAtEmitter() {
        DemoFixture.Viewers v = viewers();
        v.facts.put(B, new ViewerFacts(true, true, 0, Double.NaN, 64.0, 0.5, true, true));
        DemoFixture.Sink sink = new DemoFixture.Sink();
        DeliveryPlan p = plan();
        EmissionReport r = emit(p, valid(), v, sink);
        assertEquals(0, sink.count(B));
        assertEquals(24, sink.count(A));
        assertEquals(2L, r.skippedRecipients().get(SkipReason.VIEWER_POSITION_INVALID), "B in ring A and in ring B");
        assertAccounting(p, r);
    }
}
