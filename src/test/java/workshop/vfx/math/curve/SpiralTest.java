package workshop.vfx.math.curve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static workshop.vfx.math.curve.CurveTestSupport.assertExact;
import static workshop.vfx.math.curve.CurveTestSupport.assertNear;

import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.math.core.Quat;
import workshop.vfx.math.core.Vec3;

/**
 * Contract section 5: the circular family (circle, arc, helix, planar and conical spirals) and the M1 CONVENTIONS
 * section 5 requirements for circles.
 */
class SpiralTest {

    @Test
    @DisplayName("Validation: radii finite and >= 0; rise, turns, startTurn finite; parameters finite (sections 5, 8)")
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> new Spiral(-1, 1, 0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Spiral(1, -0.5, 0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Spiral(Double.NaN, 1, 0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Spiral(1, Double.POSITIVE_INFINITY, 0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Spiral(1, 1, Double.NaN, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Spiral(1, 1, 0, Double.NEGATIVE_INFINITY, 0));
        assertThrows(IllegalArgumentException.class, () -> new Spiral(1, 1, 0, 1, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Spiral.circle(1).point(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Spiral.circle(1).derivative(Double.POSITIVE_INFINITY));
        assertEquals(new Spiral(0, 0, 0, 0, 0), new Spiral(0, 0, 0, 0, 0), "zero radius and zero turns are valid (a point)");
    }

    @Test
    @DisplayName("Factories: circle, arc, helix, planar and conical spirals, and the M1 horizontal circle factories (section 5)")
    void factories() {
        assertEquals(new Spiral(2, 2, 0, 1, 0), Spiral.circle(2));
        assertEquals(new Spiral(2, 2, 0, 0.25, 0.5), Spiral.arc(2, 0.5, 0.25));
        assertEquals(new Spiral(2, 2, 6, 3, 0), Spiral.helix(2, 6, 3));
        assertEquals(new Spiral(1, 4, 0, 5, 0), Spiral.planarSpiral(1, 4, 5));
        assertEquals(new Spiral(1, 4, 6, 5, 0), Spiral.conicalSpiral(1, 4, 6, 5));
        assertEquals(Spiral.circle(3), Spiral.horizontalCounterClockwiseFromAbove(3));
        assertEquals(new Spiral(3, 3, 0, -1, 0), Spiral.horizontalClockwiseFromAbove(3));
    }

    @Test
    @DisplayName("M1 CONVENTIONS 5: starting at +X with normal +Y, counter-clockwise from above reaches -Z at +90 degrees, clockwise reaches +Z (exact)")
    void m1CircleConventions() {
        Spiral ccw = Spiral.horizontalCounterClockwiseFromAbove(5);
        Spiral cw = Spiral.horizontalClockwiseFromAbove(5);
        assertExact(new Vec3(5, 0, 0), ccw.point(0), "start at +X");
        assertExact(new Vec3(0, 0, -5), ccw.point(0.25), "CCW from above: +90 degrees is -Z (north)");
        assertExact(new Vec3(-5, 0, 0), ccw.point(0.5), "half turn");
        assertExact(new Vec3(0, 0, 5), ccw.point(0.75), "three quarters");
        assertExact(new Vec3(5, 0, 0), cw.point(0), "start at +X");
        assertExact(new Vec3(0, 0, 5), cw.point(0.25), "CW from above: +90 degrees is +Z (south)");
        // the circle normal is +Y for counter-clockwise (right-hand rule: p x p' points along the normal)
        Vec3 n = ccw.point(0.1).cross(ccw.derivative(0.1));
        assertTrue(n.y() > 0 && Math.abs(n.x()) < 1e-12 && Math.abs(n.z()) < 1e-12, "CCW normal +Y: " + n);
        assertTrue(cw.point(0.1).cross(cw.derivative(0.1)).y() < 0, "CW turns about -Y");
    }

    @Test
    @DisplayName("Full Heal ring (r cos th, 0, r sin th) is horizontalClockwiseFromAbove; Astral Prism's +Y rotation is horizontalCounterClockwiseFromAbove (M1 CONVENTIONS 2, 5)")
    void legacyEffectsReproduced() {
        SplittableRandom r = new SplittableRandom(51);
        for (int i = 0; i < 10_000; i++) {
            double radius = r.nextDouble(0.1, 50);
            double th = r.nextDouble(0, 2 * Math.PI);
            double t = th / (2 * Math.PI);
            assertNear(new Vec3(radius * Math.cos(th), 0, radius * Math.sin(th)), Spiral.horizontalClockwiseFromAbove(radius).point(t), 1e-13 * radius, "Full Heal ring");
            Vec3 rotated = Quat.axisAngle(Vec3.Y, th).rotate(new Vec3(radius, 0, 0));
            assertNear(rotated, Spiral.horizontalCounterClockwiseFromAbove(radius).point(t), 1e-13 * radius, "positive rotation about +Y");
        }
    }

    @Test
    @DisplayName("Seams: a closed spiral (equal radii, no rise, integer non-zero turns) has point(k) == point(0) and derivative(k) == derivative(0) exactly for integer k (section 5)")
    void exactSeams() {
        SplittableRandom r = new SplittableRandom(52);
        for (int i = 0; i < 5_000; i++) {
            double radius = r.nextDouble(0, 1e3);
            double turns = r.nextInt(-20, 21);
            if (turns == 0) {
                turns = 7;
            }
            double start = r.nextDouble(-3, 3);
            Spiral s = new Spiral(radius, radius, 0, turns, start);
            assertTrue(s.closed(), "closed " + s);
            for (int k : new int[] {1, -1, 2, 1000, -77}) {
                assertExact(s.point(0), s.point(k), "point seam at " + k + " for " + s);
                assertExact(s.derivative(0), s.derivative(k), "derivative seam at " + k + " for " + s);
            }
        }
        assertFalse(new Spiral(1, 1, 0, 1.5, 0).closed(), "non-integer turns");
        assertFalse(new Spiral(1, 2, 0, 1, 0).closed(), "radius changes");
        assertFalse(new Spiral(1, 1, 0.1, 1, 0).closed(), "rises");
        assertFalse(new Spiral(1, 1, 0, 0, 0).closed(), "zero turns");
    }

    @Test
    @DisplayName("Large turn counts: quarter phases stay exact after 2^20 turns, where a radian implementation is off by about 1e-9 (section 5, DM3-6)")
    void largeTurnCounts() {
        Spiral s = new Spiral(2, 2, 0, 1_048_576, 0); // 2^20 turns
        assertExact(new Vec3(2, 0, 0), s.point(1), "2^20 full turns");
        assertExact(new Vec3(2, 0, 0), s.point(0.5), "2^19 full turns");
        Spiral q = new Spiral(2, 2, 0, 1_048_576.25, 0);
        assertExact(new Vec3(0, 0, -2), q.point(1), "2^20 + 1/4 turns ends at -Z exactly");
        double radian = 2 * Math.cos(2 * Math.PI * 1_048_576.25);
        assertTrue(Math.abs(radian) > 1e-11, "contrast: the radian formula gives " + radian);
    }

    @Test
    @DisplayName("Helix and spirals: radius interpolates exactly at the ends; height is rise * t; helix speed is constant sqrt((2 pi r turns)^2 + rise^2) (section 5)")
    void shapeProperties() {
        SplittableRandom r = new SplittableRandom(53);
        for (int i = 0; i < 10_000; i++) {
            double r0 = r.nextDouble(0, 20), r1 = r.nextDouble(0, 20), rise = r.nextDouble(-30, 30), turns = r.nextDouble(-8, 8);
            Spiral cone = Spiral.conicalSpiral(r0, r1, rise, turns);
            double t = r.nextDouble();
            Vec3 p = cone.point(t);
            double radius = (1 - t) * r0 + t * r1;
            assertEquals(radius, Math.hypot(p.x(), p.z()), 1e-13 * (1 + radius), "horizontal distance is r(t)");
            assertEquals(rise * t, p.y(), 1e-14 * (1 + Math.abs(rise)), "height");
            assertEquals(r0, Math.hypot(cone.point(0).x(), cone.point(0).z()), 1e-14 * (1 + r0), "r(0)");
            assertEquals(r1, Math.hypot(cone.point(1).x(), cone.point(1).z()), 1e-14 * (1 + r1), "r(1)");
            Spiral helix = Spiral.helix(r0, rise, turns);
            double speed = Math.hypot(2 * Math.PI * r0 * turns, rise);
            assertEquals(speed, helix.derivative(t).length(), 1e-12 * (1 + speed), "constant helix speed");
            // curvature of a circle is 1/r: |p' x p''| / |p'|^3
            if (r0 > 0.01) {
                Spiral c = Spiral.circle(r0);
                Vec3 d1 = c.derivative(t), d2 = c.secondDerivative(t);
                assertEquals(1 / r0, d1.cross(d2).length() / Math.pow(d1.length(), 3), 1e-10 / r0, "circle curvature");
            }
        }
    }

    @Test
    @DisplayName("Derivatives agree with central finite differences, including extrapolated t (section 5)")
    void derivativesMatchFiniteDifferences() {
        SplittableRandom r = new SplittableRandom(54);
        for (int i = 0; i < 5_000; i++) {
            Spiral s = new Spiral(r.nextDouble(0, 5), r.nextDouble(0, 5), r.nextDouble(-5, 5), r.nextDouble(-3, 3), r.nextDouble(-1, 1));
            double t = r.nextDouble(-0.5, 1.5);
            double h = 1e-6;
            assertNear(s.point(t + h).sub(s.point(t - h)).mul(1 / (2 * h)), s.derivative(t), 1e-6 * (1 + s.derivative(t).length()), "derivative");
            assertNear(s.derivative(t + h).sub(s.derivative(t - h)).mul(1 / (2 * h)), s.secondDerivative(t), 1e-5 * (1 + s.secondDerivative(t).length()), "second derivative");
        }
    }

    @Test
    @DisplayName("Exact quarter-turn points for any radius and start phase that land on quarter turns (section 5)")
    void exactQuarterPoints() {
        SplittableRandom r = new SplittableRandom(55);
        Vec3[] dirs = {new Vec3(1, 0, 0), new Vec3(0, 0, -1), new Vec3(-1, 0, 0), new Vec3(0, 0, 1)};
        for (int i = 0; i < 20_000; i++) {
            double radius = r.nextDouble(0, 1e6);
            int q = r.nextInt(-1000, 1000);
            Spiral s = Spiral.arc(radius, q / 4.0, 1.0);
            int quadrant = Math.floorMod(q, 4);
            Vec3 expected = new Vec3(radius * dirs[quadrant].x(), 0, radius * dirs[quadrant].z());
            assertExact(expected, s.point(0), "start phase " + q + "/4");
            assertExact(expected, s.point(1), "one turn later");
        }
    }
}
