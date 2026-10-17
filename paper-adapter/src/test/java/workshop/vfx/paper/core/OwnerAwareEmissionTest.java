package workshop.vfx.paper.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static workshop.vfx.paper.core.DemoFixture.ANY_POINT;
import static workshop.vfx.paper.core.DemoFixture.CATALOGUE;
import static workshop.vfx.paper.core.DemoFixture.context;
import static workshop.vfx.paper.core.DemoFixture.ring;
import static workshop.vfx.paper.core.DemoFixture.roomy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.delivery.DeliveryPlan;
import workshop.vfx.delivery.DeliveryPlanner;
import workshop.vfx.delivery.QualityTier;

/** R2 F1: owner-aware authorization with two coincident rings owned by different players (contract section 7). */
class OwnerAwareEmissionTest {
    private static final long A = 0, B = 1;          // session viewer ids of the two owners
    private static final long RING_A = 10, RING_B = 11;
    private static final EmissionLimits LIMITS = new EmissionLimits(4096, 1024);

    /** Ring A is SIMPLE (24 points), ring B is HEAVY (72 points), both centred on the same anchor in world 0. */
    private static DeliveryPlan plan(long[] viewersA, long[] viewersB) {
        return DeliveryPlanner.plan(0, List.of(ring(RING_A, QualityTier.SIMPLE, 0, 0.5, 0, viewersA),
                ring(RING_B, QualityTier.HEAVY, 0, 0.5, 0, viewersB)), CATALOGUE, roomy());
    }

    private static Map<Long, RequestContext> both() {
        return Map.of(RING_A, context(RING_A, A, 0, 0.5), RING_B, context(RING_B, B, 0, 0.5));
    }

    private static EmissionReport emit(DeliveryPlan plan, Map<Long, RequestContext> ctx, DemoFixture.Viewers v, DemoFixture.Sink sink) {
        return new DefaultPlanEmitter().emit(plan, ctx, new SessionStyleHealth(CATALOGUE), v.gate(), ANY_POINT, sink, LIMITS);
    }

    @Test
    @DisplayName("Coincident rings, no view permission: each owner sees exactly their own ring")
    void isolated() {
        DemoFixture.Viewers v = new DemoFixture.Viewers().put(A, true, false).put(B, true, false);
        DemoFixture.Sink sink = new DemoFixture.Sink();
        EmissionReport r = emit(plan(new long[] {A}, new long[] {B}), both(), v, sink);
        assertEquals(24, sink.count(A));
        assertEquals(72, sink.count(B));
        assertEquals(96, r.succeededCalls());
    }

    @Test
    @DisplayName("A planned non-owner recipient with use but without view permission is refused for the other ring only")
    void ownerIdentityDecides() {
        // B was planned into ring A (it had view at snapshot time) but holds only use now: refused for ring A, still owner of ring B
        DemoFixture.Viewers v = new DemoFixture.Viewers().put(A, true, false).put(B, true, false);
        DemoFixture.Sink sink = new DemoFixture.Sink();
        EmissionReport r = emit(plan(new long[] {A, B}, new long[] {B}), both(), v, sink);
        assertEquals(24, sink.count(A));
        assertEquals(72, sink.count(B), "only its own ring");
        assertEquals(1L, r.skippedRecipients().get(SkipReason.VIEWER_PERMISSION_REVOKED));
    }

    @Test
    @DisplayName("View permission granted: B sees both rings; revoked between planning and emission: B loses ring A only")
    void viewPermissionChanges() {
        DemoFixture.Viewers v = new DemoFixture.Viewers().put(A, true, false).put(B, true, true);
        DemoFixture.Sink sink = new DemoFixture.Sink();
        DeliveryPlan p = plan(new long[] {A, B}, new long[] {B});
        emit(p, both(), v, sink);
        assertEquals(24, sink.count(A));
        assertEquals(24 + 72, sink.count(B));
        v.put(B, true, false);
        DemoFixture.Sink after = new DemoFixture.Sink();
        emit(p, both(), v, after);
        assertEquals(72, after.count(B));
    }

    @Test
    @DisplayName("Owner's use permission revoked between planning and emission: the owner receives nothing of their ring")
    void ownerUseRevoked() {
        DemoFixture.Viewers v = new DemoFixture.Viewers().put(A, false, false).put(B, true, false);
        DemoFixture.Sink sink = new DemoFixture.Sink();
        EmissionReport r = emit(plan(new long[] {A}, new long[] {B}), both(), v, sink);
        assertEquals(0, sink.count(A));
        assertEquals(72, sink.count(B));
        assertEquals(1L, r.skippedRecipients().get(SkipReason.VIEWER_PERMISSION_REVOKED));
    }

    @Test
    @DisplayName("Owner A holding view permission is still authorized for ring A by use, and for ring B by view")
    void ownerWithView() {
        DemoFixture.Viewers v = new DemoFixture.Viewers().put(A, true, true).put(B, true, false);
        DemoFixture.Sink sink = new DemoFixture.Sink();
        emit(plan(new long[] {A}, new long[] {A, B}), both(), v, sink);
        assertEquals(24 + 72, sink.count(A));
        assertEquals(72, sink.count(B));
    }

    @Test
    @DisplayName("Missing ownership context: the delivery is skipped whole and counted; the other ring is unaffected")
    void missingContext() {
        DemoFixture.Viewers v = new DemoFixture.Viewers().put(A, true, true).put(B, true, true);
        DemoFixture.Sink sink = new DemoFixture.Sink();
        EmissionReport r = emit(plan(new long[] {A, B}, new long[] {A, B}), Map.of(RING_A, context(RING_A, A, 0, 0.5)), v, sink);
        assertEquals(24, sink.count(A));
        assertEquals(24, sink.count(B));
        assertEquals(1L, r.skippedDeliveries().get(SkipReason.MISSING_REQUEST_CONTEXT));
        assertEquals(48, r.attemptedCalls());
    }

    @Test
    @DisplayName("Viewer selection: the owner is admitted before sorting and capping; the result is strictly increasing")
    void ownerFirstSelection() {
        ViewerSelection s = new OwnerFirstViewerSelection();
        assertArrayEquals(new long[] {1, 2, 7}, s.select(7, true, new long[] {1, 2, 3, 4}, 3), "owner 7 kept despite the larger id");
        assertArrayEquals(new long[] {1, 2, 3}, s.select(7, false, new long[] {1, 2, 3, 4}, 3), "ineligible owner");
        assertArrayEquals(new long[] {2, 3, 5}, s.select(3, true, new long[] {2, 3, 5, 8}, 3), "owner listed among others once");
        assertArrayEquals(new long[] {9}, s.select(9, true, new long[] {1, 2}, 1), "cap 1 keeps only the owner");
    }
}
