package workshop.vfx.math.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static workshop.vfx.math.core.TestSupport.assertFinite;
import static workshop.vfx.math.core.TestSupport.assertVec;
import static workshop.vfx.math.core.TestSupport.randomVec;
import static workshop.vfx.math.core.TestSupport.triple;

import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for the M0 audit findings that apply to the M1 core. Item numbers refer to section 2.5 of
 * the M0 audit document. Findings about time, sampling and beams become regression tests in M2 to M5
 * (see docs/TEST-SPECIFICATION.md, "Forward regressions").
 *
 * <p>The formulas of the audited plugins are re-implemented here from their mathematical description only;
 * no audited source code is copied.
 */
@Tag("m0-regression")
class M0RegressionTest {
    private static final double TOL = 1e-12;

    /** Audit item 1 / Astral Prism 0.2.0 rotate(p, x, y, z): rotate about X, then about Y, then about Z, right-handed. */
    private static Vec3 astralStyleRotate(Vec3 p, double ax, double ay, double az) {
        double y1 = p.y() * Math.cos(ax) - p.z() * Math.sin(ax);
        double z1 = p.y() * Math.sin(ax) + p.z() * Math.cos(ax);
        double x2 = p.x() * Math.cos(ay) + z1 * Math.sin(ay);
        double z2 = -p.x() * Math.sin(ay) + z1 * Math.cos(ay);
        return new Vec3(x2 * Math.cos(az) - y1 * Math.sin(az), x2 * Math.sin(az) + y1 * Math.cos(az), z2);
    }

    @Test
    @DisplayName("Audit item 1: +90 about +Y reaches -Z, but Full Heal's ring parameterization reaches +Z (opposite senses)")
    void circleOrientationConflict() {
        double h = Math.PI / 2;
        // Astral-style rotation about +Y, as audited
        assertVec(new Vec3(0, 0, -1), astralStyleRotate(new Vec3(1, 0, 0), 0, h, 0), TOL, "Astral-style +90 about Y");
        assertVec(new Vec3(0, 0, -1), Quat.axisAngle(Vec3.Y, h).rotate(Vec3.X), TOL, "engine +90 about +Y agrees with Astral");
        // Full Heal ring(theta) = (r cos t, h, r sin t), as audited
        assertVec(new Vec3(0, 0, 1), new Vec3(Math.cos(h), 0, Math.sin(h)), TOL, "Full Heal ring at 90 degrees");
        assertVec(new Vec3(0, 0, 1), Quat.axisAngle(Vec3.Y, -h).rotate(Vec3.X), TOL, "engine clockwise-from-above agrees with Full Heal");
    }

    @Test
    @DisplayName("Audit item 2: the (right, up, forward) mapping is a reflection; the engine frame is a proper rotation")
    void anchorBasisHandedness() {
        // Astral: right = forward x up, local (x, y, z) -> world (right, up, forward)
        for (Vec3 forward : new Vec3[] {new Vec3(0, 0, 1), new Vec3(1, 0, 0), new Vec3(0, 0, -1), new Vec3(-1, 0, 0)}) {
            Vec3 up = new Vec3(0, 1, 0);
            Vec3 right = forward.cross(up);
            assertEquals(-1.0, triple(right, up, forward), TOL, "reflection for forward " + forward);
            Frame engine = Frame.fromHeading(forward, up);
            assertEquals(1.0, triple(engine.x(), engine.y(), engine.z()), TOL, "proper rotation for forward " + forward);
        }
    }

    @Test
    @DisplayName("Audit (Euler order): Astral-style X-then-Y-then-Z rotation equals the engine's named Euler helper")
    void eulerOrderIsPinned() {
        SplittableRandom r = new SplittableRandom(20261014L);
        for (int i = 0; i < 5_000; i++) {
            double ax = r.nextDouble(-Math.PI, Math.PI);
            double ay = r.nextDouble(-Math.PI, Math.PI);
            double az = r.nextDouble(-Math.PI, Math.PI);
            Vec3 p = randomVec(r, 10);
            assertVec(astralStyleRotate(p, ax, ay, az), Quat.fromEulerApplyXThenYThenZ(ax, ay, az).rotate(p), 1e-11, "euler order");
        }
    }

    @Test
    @DisplayName("Full Heal outward(): a zero offset must give a finite unit fallback, never NaN")
    void zeroOffsetFallback() {
        for (double yaw : new double[] {0, Math.PI / 2, Math.PI}) {
            Vec3 fallback = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
            Vec3 outward = new Vec3(0, 0, 0).normalizedOr(fallback);
            assertFinite(outward, "yaw " + yaw);
            assertEquals(1.0, outward.length(), TOL);
        }
        // the exact (3,4) case from Full Heal's own test
        Vec3 p = new Vec3(3, 0, 4).normalizedOr(new Vec3(0, 0, 1));
        assertEquals(0.6, p.x(), TOL);
        assertEquals(0.8, p.z(), TOL);
    }

    @Test
    @DisplayName("Astral Spec pattern: non-finite or out-of-range configuration is rejected at construction")
    void configurationRejectsNonFinite() {
        assertThrows(IllegalArgumentException.class, () -> new Pose(new Vec3(Double.NaN, 0, 0), Quat.IDENTITY, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new Pose(Vec3.ZERO, Quat.IDENTITY, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Quat.axisAngle(Vec3.Y, Double.NaN));
    }

    @Test
    @DisplayName("Large-coordinate regression: a 0.3-block offset survives at the world edge in double, not in float")
    void largeCoordinatePrecision() {
        Pose edge = new Pose(new Vec3(29_999_984.0, 64.0, -29_999_984.0), Quat.IDENTITY, 1.0);
        Vec3 world = edge.toWorld(new Vec3(0.3, 0.0, 0.3));
        assertEquals(0.3, world.x() - 29_999_984.0, 1e-8);
        assertEquals(0.3, world.z() + 29_999_984.0, 1e-8);
        assertEquals(0.0, (double) (float) world.x() - (double) (float) 29_999_984.0, 0.0);
    }
}
