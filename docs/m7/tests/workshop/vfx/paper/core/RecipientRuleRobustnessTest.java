package workshop.vfx.paper.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * R3 F1: the owner-aware rule fails closed on invalid coordinates (contract section 7, step 1). Pure unit tests of the
 * scratch reference OwnerAwareRecipientRule; no planner involved.
 */
class RecipientRuleRobustnessTest {
    private static final long OWNER = 0, OTHER = 1;
    private static final double R = 32.0;
    private static final RecipientRule RULE = new OwnerAwareRecipientRule(R);
    private static final double[] BAD = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};

    private static RequestContext ctx(double ax, double ay, double az) {
        return new RequestContext(10, OWNER, 0, ax, ay, az);
    }

    private static ViewerFacts at(double x, double y, double z) {
        return new ViewerFacts(true, true, 0, x, y, z, true, true);
    }

    private static final RequestContext GOOD = ctx(0.5, 64.0, 0.5);

    @Test
    @DisplayName("Viewer x, y or z NaN, +infinity or -infinity: denied as VIEWER_POSITION_INVALID (owner and non-owner)")
    void invalidViewerPosition() {
        for (double bad : BAD) {
            for (long viewer : new long[] {OWNER, OTHER}) {
                assertEquals(Optional.of(SkipReason.VIEWER_POSITION_INVALID), RULE.decide(GOOD, viewer, at(bad, 64, 0.5)), "x=" + bad);
                assertEquals(Optional.of(SkipReason.VIEWER_POSITION_INVALID), RULE.decide(GOOD, viewer, at(0.5, bad, 0.5)), "y=" + bad);
                assertEquals(Optional.of(SkipReason.VIEWER_POSITION_INVALID), RULE.decide(GOOD, viewer, at(0.5, 64, bad)), "z=" + bad);
            }
        }
    }

    @Test
    @DisplayName("Request anchor x, y or z NaN, +infinity or -infinity: denied as INVALID_REQUEST_CONTEXT")
    void invalidAnchor() {
        ViewerFacts near = at(0.5, 64, 0.5);
        for (double bad : BAD) {
            assertEquals(Optional.of(SkipReason.INVALID_REQUEST_CONTEXT), RULE.decide(ctx(bad, 64, 0.5), OWNER, near), "ax=" + bad);
            assertEquals(Optional.of(SkipReason.INVALID_REQUEST_CONTEXT), RULE.decide(ctx(0.5, bad, 0.5), OTHER, near), "ay=" + bad);
            assertEquals(Optional.of(SkipReason.INVALID_REQUEST_CONTEXT), RULE.decide(ctx(0.5, 64, bad), OWNER, near), "az=" + bad);
        }
        // both invalid: the context is reported (anchor checked before viewer position)
        assertEquals(Optional.of(SkipReason.INVALID_REQUEST_CONTEXT), RULE.decide(ctx(Double.NaN, 64, 0.5), OWNER, at(Double.NaN, 64, 0.5)));
    }

    @Test
    @DisplayName("Normal controls: in range allowed, exactly at the radius allowed, just beyond and huge finite coordinates denied")
    void normalControls() {
        assertEquals(Optional.empty(), RULE.decide(GOOD, OWNER, at(0.5, 64, 0.5)));
        assertEquals(Optional.empty(), RULE.decide(GOOD, OTHER, at(0.5 + 31.9, 64, 0.5)));
        assertEquals(Optional.empty(), RULE.decide(GOOD, OTHER, at(0.5 + 32.0, 64, 0.5)), "d = radius is in range");
        assertEquals(Optional.of(SkipReason.VIEWER_OUT_OF_RANGE), RULE.decide(GOOD, OTHER, at(0.5 + 32.001, 64, 0.5)));
        assertEquals(Optional.of(SkipReason.VIEWER_OUT_OF_RANGE), RULE.decide(GOOD, OTHER, at(0.5, 64 - 40, 0.5)));
        // finite but huge: the squared distance overflows to +infinity, which is still out of range
        assertEquals(Optional.of(SkipReason.VIEWER_OUT_OF_RANGE), RULE.decide(GOOD, OWNER, at(1e200, 64, 0.5)));
        assertEquals(Optional.of(SkipReason.VIEWER_OUT_OF_RANGE), RULE.decide(GOOD, OWNER, at(-Double.MAX_VALUE, 64, 0.5)));
    }

    @Test
    @DisplayName("Authorization order for normal inputs is unchanged: offline, world, permission, then range checks")
    void order() {
        ViewerFacts offlineNaN = new ViewerFacts(false, true, 0, Double.NaN, 64, 0.5, true, true);
        assertEquals(Optional.of(SkipReason.VIEWER_OFFLINE), RULE.decide(GOOD, OWNER, offlineNaN));
        assertEquals(Optional.of(SkipReason.VIEWER_OFFLINE), RULE.decide(GOOD, OWNER, null), "missing facts deny");
        ViewerFacts invalidEntity = new ViewerFacts(true, false, 0, 0.5, 64, 0.5, true, true);
        assertEquals(Optional.of(SkipReason.VIEWER_OFFLINE), RULE.decide(GOOD, OWNER, invalidEntity));
        ViewerFacts otherWorldNaN = new ViewerFacts(true, true, 1, Double.NaN, 64, 0.5, true, true);
        assertEquals(Optional.of(SkipReason.VIEWER_WORLD_CHANGED), RULE.decide(GOOD, OWNER, otherWorldNaN));
        ViewerFacts noViewNaN = new ViewerFacts(true, true, 0, Double.NaN, 64, 0.5, true, false);
        assertEquals(Optional.of(SkipReason.VIEWER_PERMISSION_REVOKED), RULE.decide(GOOD, OTHER, noViewNaN));
        ViewerFacts noUseFar = new ViewerFacts(true, true, 0, 500, 64, 0.5, false, true);
        assertEquals(Optional.of(SkipReason.VIEWER_PERMISSION_REVOKED), RULE.decide(GOOD, OWNER, noUseFar), "permission before range");
    }

    @Test
    @DisplayName("View radius: NaN, infinite or negative is rejected at construction; 0 is valid (exact anchor only)")
    void radiusPolicy() {
        for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1e-9, -32}) {
            assertThrows(IllegalArgumentException.class, () -> new OwnerAwareRecipientRule(bad), "radius " + bad);
        }
        RecipientRule zero = new OwnerAwareRecipientRule(0);
        assertEquals(Optional.empty(), zero.decide(GOOD, OWNER, at(0.5, 64, 0.5)));
        assertEquals(Optional.of(SkipReason.VIEWER_OUT_OF_RANGE), zero.decide(GOOD, OWNER, at(0.5, 64, 0.5001)));
    }
}
