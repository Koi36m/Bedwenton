package workshop.vfx.math.curve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static workshop.vfx.math.curve.CurveTestSupport.assertExact;
import static workshop.vfx.math.curve.CurveTestSupport.assertNear;
import static workshop.vfx.math.curve.CurveTestSupport.spiralDerivativeTol;
import static workshop.vfx.math.curve.CurveTestSupport.spiralPointTol;
import static workshop.vfx.math.curve.CurveTestSupport.spiralSecondTol;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
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

    // ------------------------------------------------------------ revision 2 regressions (findings A1 and A2)
    // Oracles: exact BigDecimal phase and radius, a 50-digit pi, and exact quarter-turn values. None of them reduces a
    // phase the way the contract recommends.

    private static final BigDecimal PI_50 = new BigDecimal("3.14159265358979323846264338327950288419716939937510");
    private static final MathContext MC = new MathContext(60);
    private static final BigDecimal FOUR = BigDecimal.valueOf(4);

    /** Quadrant index of an exact phase that is a multiple of 1/4 turn; (cos, sin) = (1,0), (0,1), (-1,0), (0,-1). */
    private static int exactQuadrant(BigDecimal phase) {
        BigDecimal q = phase.multiply(FOUR);
        BigInteger k = q.toBigIntegerExact(); // throws if the phase is not a quarter multiple
        return k.mod(BigInteger.valueOf(4)).intValue();
    }

    private static final int[][] CS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

    @Test
    @DisplayName("A1 / T3: quarter-turn phases are exact for startTurn of any magnitude (2^52, -2^52, 2^53, 2^60, +-MAX_VALUE, 2^50 + 1/4, ...) and for negative offsets")
    void largeStartTurnExactQuarters() {
        // the review counterexample: exact phase 2^52 + 1/4 turns, so the point is (0, 0, -1)
        assertExact(new Vec3(0, 0, -1), new Spiral(1, 1, 0, 1, 0x1p52).point(0.25), "A1 counterexample");
        double[] starts = {0x1p52, -0x1p52, 0x1p53, -0x1p53, 0x1p52 + 1, -(0x1p52 + 1), 0x1p60, -0x1p60, 0x1p51 + 0.5,
            -(0x1p51 + 0.5), 0x1p50 + 0.25, -(0x1p50 + 0.75), 1e15, -1e15, 0x1p1023, Double.MAX_VALUE, -Double.MAX_VALUE,
            -0.25, -0.5, -0.75, -1.25, -2.5, -7, -0.0, 0x1p49 + 0.75, -(0x1p30 + 0.25)};
        double[][] turnsAndT = {{1, 0.25}, {1, 0.5}, {1, 0.75}, {1, 1}, {-1, 0.25}, {3, 0.75}, {0.25, 1}, {1, -1.25},
            {2, 0.375}, {-5, -0.25}, {1024, 0.25 / 1024}, {0.5, 0.5}};
        SplittableRandom r = new SplittableRandom(56);
        for (double start : starts) {
            for (double[] tt : turnsAndT) {
                double turns = tt[0], t = tt[1];
                double radius = r.nextInt(1, 4096) / 16.0;
                BigDecimal phase = new BigDecimal(start).add(new BigDecimal(turns).multiply(new BigDecimal(t)));
                int[] cs = CS[exactQuadrant(phase)];
                Vec3 expected = new Vec3(radius * cs[0], 0, -(radius * cs[1]));
                String label = "startTurn=" + start + " turns=" + turns + " t=" + t;
                Spiral s = new Spiral(radius, radius, 0, turns, start);
                assertExact(expected, s.point(t), label);
                double w = 2 * Math.PI * turns;
                assertNear(new Vec3(-w * radius * cs[1], 0, -w * radius * cs[0]), s.derivative(t), spiralDerivativeTol(radius, radius, 0, turns, t), label + " derivative");
            }
        }
        // products turns * t that are quarter multiples just below 2^51 (the T3 limit): adding the unreduced product to
        // startTurn, or the reduced product to the unreduced startTurn, rounds to the wrong quarter here
        double[][] nearLimit = {{0x1p51 - 0.25, 0.5}, {0x1p51 - 0.25, 0x1p52 + 2}, {-(0x1p51 - 0.75), 0.25},
            {0x1p50 + 0.75, 0.75}, {0x1p51 - 0.5, 0x1p51 + 0.5}, {0x1p51 - 0.25, 0x1p51 + 0.5}};
        for (double[] c : nearLimit) {
            double t = c[0], start = c[1];
            int[] cs = CS[exactQuadrant(new BigDecimal(start).add(new BigDecimal(t)))];
            assertExact(new Vec3(cs[0], 0, -cs[1]), new Spiral(1, 1, 0, 1, start).point(t), "turns * t = " + t + ", startTurn = " + start);
        }
    }

    @Test
    @DisplayName("A1 / T5: for non-negative startTurn the result depends only on startTurn mod 1, bitwise; negative offsets agree within the bound")
    void startTurnPeriodInvariance() {
        SplittableRandom r = new SplittableRandom(57);
        int[] exps = {1, 10, 20, 30, 40, 45, 48, 50, 51, 52};
        for (int i = 0; i < 20_000; i++) {
            double k = Math.scalb(1.0, exps[r.nextInt(exps.length)]) + r.nextInt(0, 1000);
            double x = k + r.nextDouble(); // a large startTurn carrying a fraction
            double f = x - k; // exact (Sterbenz): the fraction x actually carries, x == k + f
            assertTrue(new BigDecimal(x).subtract(new BigDecimal(k)).compareTo(new BigDecimal(f)) == 0);
            double r0 = r.nextDouble(0, 10), r1 = r.nextBoolean() ? r0 : r.nextDouble(0, 10);
            double rise = r.nextDouble(-5, 5), turns = r.nextDouble(-20, 20), t = r.nextDouble(-2, 3);
            Spiral big = new Spiral(r0, r1, rise, turns, x), small = new Spiral(r0, r1, rise, turns, f);
            String label = "startTurn " + x + " vs " + f + ", turns=" + turns + ", t=" + t;
            assertExact(small.point(t), big.point(t), label + " point");
            assertExact(small.derivative(t), big.derivative(t), label + " derivative");
            assertExact(small.secondDerivative(t), big.secondDerivative(t), label + " second");
            // negative offsets: -x is congruent to 1 - f; the reduced value may round once, so compare within bounds
            Spiral neg = new Spiral(r0, r1, rise, turns, -x), negSmall = new Spiral(r0, r1, rise, turns, -f);
            assertNear(negSmall.point(t), neg.point(t), 2 * spiralPointTol(r0, r1, rise, turns, t), label + " negative point");
            assertNear(negSmall.derivative(t), neg.derivative(t), 2 * spiralDerivativeTol(r0, r1, rise, turns, t), label + " negative derivative");
        }
    }

    @Test
    @DisplayName("A1: near-seam offsets (-2^-60, +-MIN_VALUE, 1 - 2^-53, ...) match the exact small-angle values within the section 7.2 bound")
    void nearSeamOffsets() {
        double[] starts = {-0x1p-60, 0x1p-60, -Double.MIN_VALUE, Double.MIN_VALUE, Math.nextDown(1.0), -Math.nextDown(1.0),
            -1e-17, 1 - 0x1p-30, -(0x1p40 + Math.nextDown(1.0)), 0x1p40 + Math.nextDown(1.0)};
        double[][] turnsAndT = {{1, 0}, {1, 0x1p-53}, {-1, 0x1p-53}, {1, Math.nextDown(1.0)}, {3, 1}, {-2, -1}};
        for (double start : starts) {
            for (double[] tt : turnsAndT) {
                double turns = tt[0], t = tt[1], radius = 3;
                BigDecimal phase = new BigDecimal(start).add(new BigDecimal(turns).multiply(new BigDecimal(t)));
                // signed distance to the nearest integer; every case here is within 2^-30 turns of a seam
                BigDecimal d = phase.subtract(phase.setScale(0, java.math.RoundingMode.HALF_EVEN));
                assertTrue(d.abs().compareTo(new BigDecimal(0x1p-29)) < 0, "near a seam: " + d);
                BigDecimal theta = d.multiply(PI_50.multiply(BigDecimal.TWO), MC);
                // |theta| < 2^-26: cos = 1 - theta^2/2 + O(theta^4), sin = theta - theta^3/6 + O(theta^5); truncation < 1e-40
                double cos = BigDecimal.ONE.subtract(theta.pow(2).divide(BigDecimal.TWO, MC)).add(theta.pow(4).divide(BigDecimal.valueOf(24), MC)).doubleValue();
                double sin = theta.subtract(theta.pow(3).divide(BigDecimal.valueOf(6), MC)).doubleValue();
                Spiral s = new Spiral(radius, radius, 0, turns, start);
                String label = "startTurn=" + start + " turns=" + turns + " t=" + t;
                assertNear(new Vec3(radius * cos, 0, -radius * sin), s.point(t), spiralPointTol(radius, radius, 0, turns, t), label);
            }
        }
        // 1 - 2^-53 + 2^-53 is exactly one turn
        assertNear(new Vec3(3, 0, 0), new Spiral(3, 3, 0, 1, Math.nextDown(1.0)).point(0x1p-53), spiralPointTol(3, 3, 0, 1, 0x1p-53), "exactly one turn");
    }

    @Test
    @DisplayName("T4: seams are exact for every integer t (up to 2^60) and every startTurn magnitude (section 5)")
    void seamsForLargeParameters() {
        SplittableRandom r = new SplittableRandom(58);
        double[] ks = {0x1p52, -0x1p52, 0x1p53, -0x1p53, 0x1p53 + 2, 0x1p60, -0x1p60, 123456789012345.0};
        for (int i = 0; i < 2_000; i++) {
            double radius = r.nextDouble(0, 1e3);
            double turns = r.nextInt(1, 21) * (r.nextBoolean() ? 1 : -1);
            double start = switch (r.nextInt(3)) {
                case 0 -> r.nextDouble(-3, 3);
                case 1 -> (r.nextBoolean() ? 1 : -1) * (Math.scalb(1.0, r.nextInt(20, 53)) + r.nextDouble());
                default -> (r.nextBoolean() ? 1 : -1) * Math.scalb(1.0, r.nextInt(52, 1000));
            };
            Spiral s = new Spiral(radius, radius, 0, turns, start);
            for (double k : ks) {
                assertExact(s.point(0), s.point(k), "point seam at " + k + " for " + s);
                assertExact(s.derivative(0), s.derivative(k), "derivative seam at " + k + " for " + s);
            }
        }
    }

    @Test
    @DisplayName("A2: extrapolated radius cancellation; the error is bounded relative to S = |1-t| r0 + |t| r1, not to r(t) (review counterexample: exact radius 2^-52)")
    void extrapolatedRadiusCancellation() {
        // the review counterexample: r0 = 1, r1 = 1.2 (the double), t = -5; exact radius 6 - 5 * 1.2 = 2^-52
        BigDecimal exact = new BigDecimal(6).subtract(new BigDecimal(5).multiply(new BigDecimal(1.2)));
        assertEquals(0, exact.compareTo(new BigDecimal(0x1p-52)), "exact radius");
        Spiral a2 = new Spiral(1, 1.2, 0, 0, 0);
        double tol = spiralPointTol(1, 1.2, 0, 0, -5);
        assertTrue(tol < 1e-13, "the bound stays meaningful: " + tol);
        assertNear(new Vec3(0x1p-52, 0, 0), a2.point(-5), tol, "A2 counterexample point");
        assertNear(new Vec3(1.2 - 1.0, 0, 0), a2.derivative(-5), spiralDerivativeTol(1, 1.2, 0, 0, -5), "A2 derivative");

        SplittableRandom r = new SplittableRandom(59);
        double[] ts = {-5, -3, -1, -0.5, -0.25, 2, 3, 7, 1.5, -1000, 1000, -37.75, 12.25};
        double[] turnsSet = {0, 1, -2, 4, 0.25, -0.5};
        for (int i = 0; i < 20_000; i++) {
            double t = ts[r.nextInt(ts.length)];
            double turns = turnsSet[r.nextInt(turnsSet.length)];
            double start = r.nextInt(-8, 9) / 4.0;
            BigDecimal phase = new BigDecimal(start).add(new BigDecimal(turns).multiply(new BigDecimal(t)));
            if (phase.multiply(FOUR).stripTrailingZeros().scale() > 0) {
                continue; // keep phases on quarter turns, where cos and sin are known exactly
            }
            double r0 = r.nextDouble(0.1, 1) * Math.pow(10, r.nextInt(-3, 7));
            double r1 = (t - 1) * r0 / t; // (1 - t) r0 + t r1 = 0 up to rounding
            r1 = switch (r.nextInt(3)) { case 0 -> r1; case 1 -> Math.nextUp(r1); default -> Math.nextDown(r1); };
            if (!(r1 >= 0)) {
                continue;
            }
            double rise = r.nextInt(-64, 65) / 8.0;
            BigDecimal bt = new BigDecimal(t), b0 = new BigDecimal(r0), b1 = new BigDecimal(r1);
            BigDecimal rad = BigDecimal.ONE.subtract(bt).multiply(b0).add(bt.multiply(b1)); // exact, possibly 0 or negative
            BigDecimal dr = b1.subtract(b0);
            BigDecimal w = PI_50.multiply(BigDecimal.TWO).multiply(new BigDecimal(turns));
            int[] cs = CS[exactQuadrant(phase)];
            BigDecimal c = BigDecimal.valueOf(cs[0]), sn = BigDecimal.valueOf(cs[1]);
            Vec3 p = new Vec3(rad.multiply(c).doubleValue(), new BigDecimal(rise).multiply(bt).doubleValue(), rad.multiply(sn).negate().doubleValue());
            Vec3 d = new Vec3(dr.multiply(c).subtract(w.multiply(rad).multiply(sn)).doubleValue(), rise,
                dr.multiply(sn).negate().subtract(w.multiply(rad).multiply(c)).doubleValue());
            BigDecimal k2 = BigDecimal.TWO.multiply(dr).multiply(w), w2r = w.multiply(w).multiply(rad);
            Vec3 dd = new Vec3(k2.multiply(sn).negate().subtract(w2r.multiply(c)).doubleValue(), 0,
                k2.multiply(c).negate().add(w2r.multiply(sn)).doubleValue());
            Spiral s = new Spiral(r0, r1, rise, turns, start);
            String label = s + " at t=" + t + ", exact radius " + rad.doubleValue();
            assertNear(p, s.point(t), spiralPointTol(r0, r1, rise, turns, t) + Math.ulp(CurveTestSupport.maxAbs(p)), label + " point");
            assertNear(d, s.derivative(t), spiralDerivativeTol(r0, r1, rise, turns, t) + Math.ulp(CurveTestSupport.maxAbs(d)), label + " derivative");
            assertNear(dd, s.secondDerivative(t), spiralSecondTol(r0, r1, turns, t) + Math.ulp(CurveTestSupport.maxAbs(dd)), label + " second");
        }
    }

    @Test
    @DisplayName("Radius accuracy relative to S near an end: very different radii and t close to the end with the small radius (contract 5, radius rule)")
    void radiusAccuracyNearEnds() {
        SplittableRandom r = new SplittableRandom(60);
        for (int i = 0; i < 20_000; i++) {
            double big = r.nextDouble(1, 9) * Math.pow(10, r.nextInt(3, 10)), small = r.nextDouble(0, 2);
            double u = r.nextDouble(0, 0x1p-10);
            boolean atEnd = r.nextBoolean();
            double r0 = atEnd ? big : small, r1 = atEnd ? small : big, t = atEnd ? 1 - u : u;
            BigDecimal bt = new BigDecimal(t);
            double exact = BigDecimal.ONE.subtract(bt).multiply(new BigDecimal(r0)).add(bt.multiply(new BigDecimal(r1))).doubleValue();
            Spiral s = new Spiral(r0, r1, 0, 0, 0); // phase 0: the point is (r(t), 0, 0)
            assertNear(new Vec3(exact, 0, 0), s.point(t), spiralPointTol(r0, r1, 0, 0, t) + Math.ulp(exact), "radius of " + s + " at t=" + t);
        }
    }
}
