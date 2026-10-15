package workshop.vfx.math.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Executable forms of the findings in review/M1-CLAUDE-REVIEW.md. Each test asserts one specific reading of the
 * contract and fails against commit 8397c04. They are disabled so that the default build stays green until the
 * contract owner decides each item. To run them, remove the class-level {@code @Disabled} locally, or use the JUnit
 * console launcher with {@code --config=junit.jupiter.conditions.deactivate=org.junit.*DisabledCondition}
 * (see review/REPRODUCE-REVIEW.md). Delete a test when its item is resolved the other way.
 */
@Tag("review-finding")
@Disabled("Review findings: each asserts one reading of the contract and awaits a decision (see review/M1-CLAUDE-REVIEW.md)")
class ReviewFindingsTest {

    @Test
    @DisplayName("D1 (defect, diagnostic): Pose.inverse names the scale, not the translation, when 1/s overflows (section 0A Tier 3, message 'should name the reason')")
    void d1InverseMessageNamesScale() {
        ArithmeticException e = assertThrows(ArithmeticException.class, () -> new Pose(Vec3.ZERO, Quat.IDENTITY, 1e-310).inverse());
        assertTrue(e.getMessage().contains("scale"), "message was: " + e.getMessage());
    }

    @Test
    @DisplayName("A1: transportY's new y is bitwise normalize(newY) (section 4: 'new y exactly normalize(newY)')")
    void a1TransportYIsExactlyNormalizeNewY() {
        Vec3 newY = new Vec3(2, 3, 7);
        assertEquals(newY.normalizedOr(null), Frame.transportY(Frame.WORLD, newY).y());
    }

    @Test
    @DisplayName("A2: Quat.inverse of a degenerate quaternion is the IDENTITY constant (section 0A 'own fallbacks exactly as specified')")
    void a2InverseFallbackIsIdentity() {
        assertEquals(Quat.IDENTITY, new Quat(Double.NaN, 0, 0, 0).inverse());
    }

    @Test
    @DisplayName("A3: axisAngle(null, angle) throws IllegalArgumentException like the Frame and Pose constructors (section 0A Tier 3)")
    void a3AxisAngleNullAxis() {
        assertThrows(IllegalArgumentException.class, () -> Quat.axisAngle(null, 1.0));
    }

    @Test
    @DisplayName("A4: a non-zero xHint below LENGTH_EPS is honoured (section 4 says only 'zero/non-finite' hints fall back)")
    void a4TinyNonZeroHintIsHonoured() {
        Frame f = Frame.alignY(Vec3.Z, new Vec3(0, 1e-13, 0));
        assertEquals(1.0, f.x().y(), 1e-12, "x should follow the hint direction +Y, was " + f.x());
    }

    @Test
    @DisplayName("A5: fromHeading uses the raw |forward x up| < 1e-8 test (literal section 4 text)")
    void a5FromHeadingRawCrossThreshold() {
        // |forward x up| = 1e-9 < 1e-8 literally: up hint becomes the least-aligned axis of forward (+Z), so y ~ +Z.
        Frame f = Frame.fromHeading(new Vec3(1e-9, 1e-3, 0), Vec3.Y);
        assertEquals(1.0, Math.abs(f.y().z()), 1e-6, "y was " + f.y());
    }

    @Test
    @DisplayName("A6: rotate meets the 1e-13 Tier 1 accuracy row for any quaternion inside its domain (norm within 1e-12 of 1)")
    void a6RotateAccuracyForNearUnitQuaternion() {
        double k = 1 + 1e-12;
        Quat q = new Quat(0, 0, 0, k); // half turn about Z, norm 1 + 1e-12: inside the rotate domain
        Vec3 v = new Vec3(1, 0, 0);
        assertEquals(-1.0, q.rotate(v).x(), 1e-13, "rotate result " + q.rotate(v));
    }
}
