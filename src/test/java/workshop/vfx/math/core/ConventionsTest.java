package workshop.vfx.math.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static workshop.vfx.math.core.TestSupport.assertVec;
import static workshop.vfx.math.core.TestSupport.triple;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the coordinate conventions of docs/CONVENTIONS.md with exact, hand-derived values.
 *
 * <p>World axes (Minecraft): +X east, +Y up, +Z south. Rotations are active, right-handed (right-hand rule).
 * "Viewed from above" means an observer high above looking straight down -Y with north (-Z) at the top of
 * the picture and east (+X) at the right, like a map. In that picture a point (x, y, z) appears at
 * screen coordinates (sx, sy) = (x, -z).
 */
class ConventionsTest {
    private static final double H = Math.PI / 2;
    private static final double TOL = 1e-12;

    /** Z component of the 2-D cross product in map view: positive = counter-clockwise, negative = clockwise. */
    private static double screenCross(Vec3 from, Vec3 to) {
        double sx0 = from.x();
        double sy0 = -from.z();
        double sx1 = to.x();
        double sy1 = -to.z();
        return sx0 * sy1 - sy0 * sx1;
    }

    @Test
    @DisplayName("Minecraft world axes are a right-handed basis: east x up = south")
    void worldAxesAreRightHanded() {
        Vec3 east = new Vec3(1, 0, 0);
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 south = new Vec3(0, 0, 1);
        assertVec(south, east.cross(up), 0.0, "east x up");
        assertVec(east, up.cross(south), 0.0, "up x south");
        assertVec(up, south.cross(east), 0.0, "south x east");
        assertEquals(1.0, triple(east, up, south), 0.0);
    }

    @Test
    @DisplayName("+90 degrees about +Y sends +X to -Z (east to north)")
    void positiveQuarterTurnAboutPlusYSendsPlusXToMinusZ() {
        Vec3 r = Quat.axisAngle(Vec3.Y, H).rotate(Vec3.X);
        assertVec(new Vec3(0, 0, -1), r, TOL, "+90 about +Y applied to +X");
    }

    @Test
    @DisplayName("+90 degrees about +Y is counter-clockwise when viewed from above")
    void positiveRotationAboutPlusYIsCounterClockwiseFromAbove() {
        Vec3 r = Quat.axisAngle(Vec3.Y, H).rotate(Vec3.X);
        assertTrue(screenCross(Vec3.X, r) > 0.99, "screen cross should be about +1 (counter-clockwise), was " + screenCross(Vec3.X, r));
    }

    @Test
    @DisplayName("Clockwise 90 degrees viewed from above sends +X to +Z (east to south)")
    void clockwiseQuarterTurnFromAboveSendsPlusXToPlusZ() {
        // Clockwise seen from above is a NEGATIVE angle about +Y, equivalently a positive angle about -Y.
        Vec3 viaNegativeAngle = Quat.axisAngle(Vec3.Y, -H).rotate(Vec3.X);
        Vec3 viaNegativeAxis = Quat.axisAngle(Vec3.Y.negate(), H).rotate(Vec3.X);
        assertVec(new Vec3(0, 0, 1), viaNegativeAngle, TOL, "-90 about +Y applied to +X");
        assertVec(new Vec3(0, 0, 1), viaNegativeAxis, TOL, "+90 about -Y applied to +X");
        assertTrue(screenCross(Vec3.X, viaNegativeAngle) < -0.99, "screen cross should be about -1 (clockwise)");
        assertTrue(screenCross(Vec3.X, viaNegativeAxis) < -0.99, "screen cross should be about -1 (clockwise)");
    }

    @Test
    @DisplayName("Positive rotation about +Y and clockwise-from-above are opposite senses")
    void positiveRotationAndClockwiseFromAboveAreOpposite() {
        for (double angle : new double[] {0.1, 0.5, 1.0, 2.0, 3.0}) {
            Vec3 positive = Quat.axisAngle(Vec3.Y, angle).rotate(Vec3.X);
            Vec3 clockwise = Quat.axisAngle(Vec3.Y.negate(), angle).rotate(Vec3.X);
            assertTrue(screenCross(Vec3.X, positive) > 0, "positive about +Y must be counter-clockwise at angle " + angle);
            assertTrue(screenCross(Vec3.X, clockwise) < 0, "clockwise-from-above must be clockwise at angle " + angle);
            assertVec(new Vec3(positive.x(), positive.y(), -positive.z()), clockwise, TOL, "mirror about the XY plane");
        }
    }

    @Test
    @DisplayName("Full Heal 0.2.0 ring formula (cos t, 0, sin t) is clockwise from above (audit item 1)")
    void fullHealRingFormulaIsClockwiseFromAbove() {
        double r = 2.5;
        for (double theta : new double[] {0.0, 0.3, 1.0, H, 2.0, 3.0, -1.0, -2.5}) {
            Vec3 ring = new Vec3(r * Math.cos(theta), 0, r * Math.sin(theta));
            Vec3 viaQuat = Quat.axisAngle(Vec3.Y.negate(), theta).rotate(new Vec3(r, 0, 0));
            assertVec(ring, viaQuat, TOL, "ring at theta=" + theta);
        }
        Vec3 quarter = new Vec3(Math.cos(H), 0, Math.sin(H));
        assertVec(new Vec3(0, 0, 1), quarter, TOL, "ring at 90 degrees reaches +Z");
    }

    @Test
    @DisplayName("Axis rotations by +90 degrees, both directions")
    void axisRotationTable() {
        // +90 degrees about +X: Y -> Z, Z -> -Y
        assertVec(new Vec3(0, 0, 1), Quat.axisAngle(Vec3.X, H).rotate(Vec3.Y), TOL, "X90 Y");
        assertVec(new Vec3(0, -1, 0), Quat.axisAngle(Vec3.X, H).rotate(Vec3.Z), TOL, "X90 Z");
        // +90 degrees about +Y: Z -> X, X -> -Z
        assertVec(new Vec3(1, 0, 0), Quat.axisAngle(Vec3.Y, H).rotate(Vec3.Z), TOL, "Y90 Z");
        assertVec(new Vec3(0, 0, -1), Quat.axisAngle(Vec3.Y, H).rotate(Vec3.X), TOL, "Y90 X");
        // +90 degrees about +Z: X -> Y, Y -> -X
        assertVec(new Vec3(0, 1, 0), Quat.axisAngle(Vec3.Z, H).rotate(Vec3.X), TOL, "Z90 X");
        assertVec(new Vec3(-1, 0, 0), Quat.axisAngle(Vec3.Z, H).rotate(Vec3.Y), TOL, "Z90 Y");
        // -90 degrees is the inverse rotation
        assertVec(new Vec3(0, 1, 0), Quat.axisAngle(Vec3.X, -H).rotate(Vec3.Z), TOL, "X-90 Z");
        assertVec(new Vec3(0, 0, 1), Quat.axisAngle(Vec3.Y, -H).rotate(Vec3.X), TOL, "Y-90 X");
        assertVec(new Vec3(0, -1, 0), Quat.axisAngle(Vec3.Z, -H).rotate(Vec3.X), TOL, "Z-90 X");
        // A half turn about +Y flips X and Z.
        assertVec(new Vec3(-1, 2, -3), Quat.axisAngle(Vec3.Y, Math.PI).rotate(new Vec3(1, 2, 3)), TOL, "Y180");
    }

    @Test
    @DisplayName("a.mul(b) applies b first, then a")
    void multiplicationAppliesRightOperandFirst() {
        Quat qx = Quat.axisAngle(Vec3.X, H);
        Quat qz = Quat.axisAngle(Vec3.Z, H);
        Vec3 v = new Vec3(1, 0, 0);
        // qz.mul(qx): X applied first (v stays), then Z (v -> +Y)
        assertVec(new Vec3(0, 1, 0), qz.mul(qx).rotate(v), TOL, "qz.mul(qx)");
        // qx.mul(qz): Z applied first (v -> +Y), then X (+Y -> +Z)
        assertVec(new Vec3(0, 0, 1), qx.mul(qz).rotate(v), TOL, "qx.mul(qz)");
    }

    @Test
    @DisplayName("Pose reference example: t=(10,0,0), +90 about +Y, scale 2 maps local (1,0,0) to (10,0,-2)")
    void poseReferenceExample() {
        Pose pose = new Pose(new Vec3(10, 0, 0), Quat.axisAngle(Vec3.Y, H), 2.0);
        assertVec(new Vec3(10, 0, -2), pose.toWorld(new Vec3(1, 0, 0)), TOL, "toWorld");
        assertVec(new Vec3(1, 0, 0), pose.toLocal(new Vec3(10, 0, -2)), TOL, "toLocal");
        assertVec(new Vec3(0, 0, -1), pose.directionToWorld(new Vec3(1, 0, 0)), TOL, "directionToWorld ignores scale and translation");
    }

    @Test
    @DisplayName("Frame reference examples from docs/REFERENCE-VECTORS.md")
    void frameReferenceExamples() {
        Frame a = Frame.alignY(new Vec3(0, 1, 0), new Vec3(1, 0, 0));
        assertVec(new Vec3(1, 0, 0), a.x(), TOL, "alignY x");
        assertVec(new Vec3(0, 1, 0), a.y(), TOL, "alignY y");
        assertVec(new Vec3(0, 0, 1), a.z(), TOL, "alignY z");

        // Facing south (+Z) with +Y up: right is west (-X), back is north (-Z).
        Frame heading = Frame.fromHeading(new Vec3(0, 0, 1), new Vec3(0, 1, 0));
        assertVec(new Vec3(-1, 0, 0), heading.x(), TOL, "south heading: right");
        assertVec(new Vec3(0, 1, 0), heading.y(), TOL, "south heading: up");
        assertVec(new Vec3(0, 0, -1), heading.z(), TOL, "south heading: back");
    }
}
