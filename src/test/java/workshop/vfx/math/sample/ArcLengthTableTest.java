package workshop.vfx.math.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static workshop.vfx.math.sample.SampleTestSupport.EPS;
import static workshop.vfx.math.sample.SampleTestSupport.LIMITS;
import static workshop.vfx.math.sample.SampleTestSupport.TIGHT;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Quat;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.curve.CubicBezier;
import workshop.vfx.math.curve.Curve;
import workshop.vfx.math.curve.LineSegment;
import workshop.vfx.math.curve.QuadraticBezier;
import workshop.vfx.math.curve.Spiral;

/** Contract section 5: the arc-length table (exactly solvable lengths, convergence semantics, exact ends, reuse). */
class ArcLengthTableTest {

    private static final BigDecimal PI = new BigDecimal("3.14159265358979323846264338327950288419716939937510582097494459");
    private static final MathContext MC = new MathContext(40);

    @Test
    @DisplayName("Lines: length |t1 - t0| |P1 - P0| (BigDecimal oracle) within 64 eps relative, for magnitudes 1e-300 to 1e100 and extrapolated ranges")
    void lines() {
        SplittableRandom r = new SplittableRandom(411);
        for (int i = 0; i < 2_000; i++) {
            double mag = Math.pow(10, r.nextInt(-300, 101));
            Vec3 a = new Vec3(r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1)).mul(mag);
            Vec3 b = new Vec3(r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1)).mul(mag);
            double t0 = r.nextBoolean() ? 0 : r.nextDouble(-1000, 0), t1 = r.nextBoolean() ? 1 : r.nextDouble(0, 1000);
            ArcLengthTable t = ArcLengthTable.build(new LineSegment(a, b), t0, t1, TIGHT, LIMITS);
            BigDecimal d2 = sq(b.x(), a.x()).add(sq(b.y(), a.y())).add(sq(b.z(), a.z()));
            double exact = d2.sqrt(MC).multiply(new BigDecimal(t1).subtract(new BigDecimal(t0))).doubleValue();
            assertEquals(exact, t.length(), 64 * EPS * exact + Double.MIN_VALUE * 64, "line " + a + " " + b + " [" + t0 + ", " + t1 + "]");
            assertTrue(t.converged(), "a constant speed converges");
        }
    }

    private static BigDecimal sq(double u, double v) {
        BigDecimal d = new BigDecimal(u).subtract(new BigDecimal(v));
        return d.multiply(d);
    }

    @Test
    @DisplayName("Circles and helices: length |t1 - t0| sqrt((2 pi r turns)^2 + rise^2) (constant speed, BigDecimal pi) within the M3 derivative error plus 64 eps")
    void constantSpeedSpirals() {
        SplittableRandom r = new SplittableRandom(412);
        for (int i = 0; i < 2_000; i++) {
            double radius = r.nextDouble(0, 1) * Math.pow(10, r.nextInt(-3, 4));
            double rise = r.nextBoolean() ? 0 : r.nextDouble(-50, 50);
            double turns = r.nextBoolean() ? r.nextInt(-64, 65) : r.nextDouble(-20, 20);
            double start = r.nextDouble(-3, 3);
            double t0 = r.nextBoolean() ? 0 : r.nextDouble(-2, 0), t1 = r.nextBoolean() ? 1 : r.nextDouble(0, 3);
            Spiral s = new Spiral(radius, radius, rise, turns, start);
            ArcLengthTable t = ArcLengthTable.build(s, t0, t1, TIGHT, LIMITS);
            BigDecimal w = PI.multiply(BigDecimal.TWO).multiply(new BigDecimal(turns)).multiply(new BigDecimal(radius));
            BigDecimal speed = w.multiply(w).add(new BigDecimal(rise).multiply(new BigDecimal(rise))).sqrt(MC);
            double exact = speed.multiply(new BigDecimal(t1).subtract(new BigDecimal(t0))).doubleValue();
            double allowance = (t1 - t0) * SampleTestSupport.speedErrorBound("SPIRAL", new double[] {radius, radius, rise, turns, start}, t0, t1);
            assertEquals(exact, t.length(), allowance + 64 * EPS * exact + 1e-300, s + " [" + t0 + ", " + t1 + "]");
            assertTrue(t.converged());
            assertEquals(t.panelCount(), 16, "a constant speed needs no refinement beyond the initial partition");
        }
    }

    @Test
    @DisplayName("Quadratic Beziers and conical spirals against the closed-form antiderivative: |L - L*| <= tau + rounding allowance, with converged() and errorEstimate() <= tau")
    void closedForms() {
        SplittableRandom r = new SplittableRandom(413);
        for (int i = 0; i < 1_500; i++) {
            double t0 = r.nextBoolean() ? 0 : r.nextDouble(-1, 0), t1 = r.nextBoolean() ? 1 : r.nextDouble(1, 2);
            double expected, allowance;
            ArcLengthTable t;
            ArcLengthTolerance tol = new ArcLengthTolerance(0, new double[] {1e-4, 1e-7, 0x1p-30, 1e-11}[r.nextInt(4)]);
            if (r.nextBoolean()) {
                QuadraticBezier q = new QuadraticBezier(SampleTestSupport.vec(rand(r, 10), 0), SampleTestSupport.vec(rand(r, 10), 0), SampleTestSupport.vec(rand(r, 10), 0));
                t = ArcLengthTable.build(q, t0, t1, tol, LIMITS);
                expected = SampleTestSupport.quadLength(q, t0, t1);
                double[] p = {q.p0().x(), q.p0().y(), q.p0().z(), q.p1().x(), q.p1().y(), q.p1().z(), q.p2().x(), q.p2().y(), q.p2().z()};
                allowance = (t1 - t0) * SampleTestSupport.speedErrorBound("QUAD", p, t0, t1);
            } else {
                double r0 = r.nextDouble(0.1, 10), r1 = r.nextDouble(0.1, 10);
                Spiral s = new Spiral(r0, r1, r.nextDouble(-10, 10), r.nextDouble(-15, 15), r.nextDouble());
                t = ArcLengthTable.build(s, t0, t1, tol, LIMITS);
                expected = SampleTestSupport.spiralLength(s, t0, t1);
                allowance = (t1 - t0) * SampleTestSupport.speedErrorBound("SPIRAL", new double[] {r0, r1, s.rise(), s.turns(), s.startTurn()}, t0, t1);
            }
            assertTrue(t.converged(), "converged");
            assertTrue(t.errorEstimate() <= t.effectiveTolerance(), "converged implies errorEstimate <= tau");
            assertEquals(expected, t.length(), t.effectiveTolerance() + allowance + 1e-13 * expected, "length within tau, tol " + tol.relative());
        }
    }

    private static double[] rand(SplittableRandom r, double mag) {
        return new double[] {r.nextDouble(-mag, mag), r.nextDouble(-mag, mag), r.nextDouble(-mag, mag)};
    }

    @Test
    @DisplayName("Effective tolerance: tau = max(absolute, relative * initial estimate); tau >= absolute, and with absolute = 0 tau is relative * L within 1e-6 for smooth curves")
    void effectiveTolerance() {
        CubicBezier c = new CubicBezier(new Vec3(0, 0, 0), new Vec3(1, 3, 0), new Vec3(4, -2, 1), new Vec3(5, 0, 0));
        ArcLengthTable rel = ArcLengthTable.build(c, new ArcLengthTolerance(0, 1e-6), LIMITS);
        assertEquals(1e-6 * rel.length(), rel.effectiveTolerance(), 1e-6 * 1e-6 * rel.length());
        ArcLengthTable abs = ArcLengthTable.build(c, new ArcLengthTolerance(0.25, 0x1p-40), LIMITS);
        assertEquals(0.25, abs.effectiveTolerance());
        ArcLengthTable both = ArcLengthTable.build(c, new ArcLengthTolerance(1e-9, 1e-3), LIMITS);
        assertEquals(1e-3 * both.length(), both.effectiveTolerance(), 1e-6 * both.length());
    }

    @Test
    @DisplayName("Convergence: for a cubic, the length error shrinks with the tolerance and stays below tau for tau = 1e-3 ... 1e-12 relative")
    void convergence() {
        CubicBezier c = new CubicBezier(new Vec3(0, 0, 0), new Vec3(10, 30, 0), new Vec3(-20, 30, 5), new Vec3(10, 0, 0));
        // reference: the tightest table at the tightest tolerance and the largest budget, cross-checked against the 1e-28 oracle row below
        ArcLengthTable ref = ArcLengthTable.build(c, new ArcLengthTolerance(0, 0x1p-40), new SampleLimits(16, 1 << 24));
        double previousError = Double.POSITIVE_INFINITY;
        for (double rel : new double[] {1e-3, 1e-5, 1e-7, 1e-9, 1e-12}) {
            ArcLengthTable t = ArcLengthTable.build(c, new ArcLengthTolerance(0, rel), LIMITS);
            double err = Math.abs(t.length() - ref.length());
            assertTrue(t.converged());
            assertTrue(err <= t.effectiveTolerance() + 1e-12 * ref.length(), "tol " + rel + ": error " + err);
            assertTrue(err <= previousError + 1e-12 * ref.length(), "monotone convergence at " + rel);
            previousError = err;
            assertTrue(t.panelCount() >= 16 && t.evaluations() >= 240, "at least the initial partition");
        }
    }

    @Test
    @DisplayName("Exact ends: lengthAt(t0) = 0, lengthAt(t1) = length, parameterAt(0) = t0, parameterAt(length) = t1 (bit for bit); lengthAt non-decreasing on a grid for smooth curves")
    void exactEnds() {
        SplittableRandom r = new SplittableRandom(414);
        for (int i = 0; i < 300; i++) {
            Curve c = r.nextBoolean() ? new CubicBezier(SampleTestSupport.vec(rand(r, 5), 0), SampleTestSupport.vec(rand(r, 5), 0), SampleTestSupport.vec(rand(r, 5), 0), SampleTestSupport.vec(rand(r, 5), 0))
                : new Spiral(r.nextDouble(0.5, 5), r.nextDouble(0.5, 5), r.nextDouble(-5, 5), r.nextDouble(-8, 8), r.nextDouble());
            double t0 = r.nextBoolean() ? 0 : r.nextDouble(-0.5, 0), t1 = r.nextBoolean() ? 1 : r.nextDouble(1, 1.5);
            ArcLengthTable t = ArcLengthTable.build(c, t0, t1, TIGHT, LIMITS);
            assertEquals(0.0, t.lengthAt(t0));
            assertEquals(t.length(), t.lengthAt(t1));
            assertEquals(t0, t.parameterAt(0));
            assertEquals(t1, t.parameterAt(t.length()));
            assertEquals(t0, t.startParameter());
            assertEquals(t1, t.endParameter());
            assertTrue(t.curve() == c, "the table keeps the curve it measured");
            double prev = -1;
            for (int k = 0; k <= 200; k++) {
                double u = t0 + (t1 - t0) * (k / 200.0);
                double s = t.lengthAt(Math.min(u, t1));
                assertTrue(s >= prev - t.effectiveTolerance() * 0x1p-8 && s >= 0 && s <= t.length(), "lengthAt in [0, L] and non-decreasing");
                prev = Math.max(prev, s);
            }
        }
    }

    @Test
    @DisplayName("Rigid motion and uniform scale: the posed curve's length is scale * length (within tau), rotation and translation invariant; subranges add up")
    void invariances() {
        SplittableRandom r = new SplittableRandom(415);
        for (int i = 0; i < 300; i++) {
            Curve c = new CubicBezier(SampleTestSupport.vec(rand(r, 5), 0), SampleTestSupport.vec(rand(r, 5), 0), SampleTestSupport.vec(rand(r, 5), 0), SampleTestSupport.vec(rand(r, 5), 0));
            double scale = new double[] {1, 2, 0.5, 3.7, 1e-3, 1e3}[r.nextInt(6)];
            Pose pose = new Pose(new Vec3(r.nextDouble(-3e7, 3e7), r.nextDouble(-64, 320), r.nextDouble(-3e7, 3e7)),
                new Quat(r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1)), scale);
            ArcLengthTable local = ArcLengthTable.build(c, TIGHT, LIMITS);
            ArcLengthTable world = ArcLengthTable.build(c.transformed(pose), TIGHT, LIMITS);
            assertEquals(scale * local.length(), world.length(), world.effectiveTolerance() + scale * local.effectiveTolerance() + 1e-12 * world.length(), "scale " + scale);
            double m = r.nextDouble(0.1, 0.9);
            ArcLengthTable left = ArcLengthTable.build(c, 0, m, TIGHT, LIMITS), right = ArcLengthTable.build(c, m, 1, TIGHT, LIMITS);
            assertEquals(local.length(), left.length() + right.length(), local.effectiveTolerance() + left.effectiveTolerance() + right.effectiveTolerance() + 1e-13 * local.length(), "additivity at " + m);
            assertEquals(left.length(), local.lengthAt(m), local.effectiveTolerance() + left.effectiveTolerance() + 1e-13 * local.length(), "lengthAt(m) = L(0, m)");
        }
    }

    @Test
    @DisplayName("Collapsed and stationary geometry: zero length, converged; a stationary point off the initial nodes converges to the exact length")
    void degenerate() {
        assertEquals(0.0, ArcLengthTable.build(new LineSegment(Vec3.X, Vec3.X), TIGHT, LIMITS).length());
        assertEquals(0.0, ArcLengthTable.build(new CubicBezier(Vec3.Y, Vec3.Y, Vec3.Y, Vec3.Y), TIGHT, LIMITS).length());
        assertEquals(0.0, ArcLengthTable.build(new Spiral(0, 0, 0, 5, 0), TIGHT, LIMITS).length());
        ArcLengthTable point = ArcLengthTable.build(new CubicBezier(Vec3.X, Vec3.Y, Vec3.Z, Vec3.ZERO), 0.25, 0.25, TIGHT, LIMITS);
        assertEquals(0.0, point.length());
        assertTrue(point.converged());
        assertEquals(0.25, point.parameterAt(0));
        // straight cubic with a stationary point: x(t) = 3t - 6t^2 + 4t^3, speed 3 (1 - 2t)^2 vanishes at t = 1/2; length 1
        CubicBezier reversal = new CubicBezier(new Vec3(0, 0, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 0, 0));
        ArcLengthTable rev = ArcLengthTable.build(reversal, TIGHT, LIMITS);
        assertEquals(1.0, rev.length(), rev.effectiveTolerance() + 1e-15);
        // the same curve on [0.1, 0.95], so that the stationary point is not an initial panel node; x is monotone
        ArcLengthTable shifted = ArcLengthTable.build(reversal, 0.1, 0.95, TIGHT, LIMITS);
        double exact = (3 * 0.95 - 6 * 0.95 * 0.95 + 4 * 0.95 * 0.95 * 0.95) - (3 * 0.1 - 6 * 0.01 + 4 * 0.001);
        assertTrue(shifted.converged(), "stationary point converges");
        assertEquals(exact, shifted.length(), shifted.effectiveTolerance() + 1e-14);
        // a genuine cusp with a kink in the speed, |t - 5/32| |alpha + beta t|, is covered by the reference rows (arclength.csv)
    }

    @Test
    @DisplayName("Validation: null arguments; non-finite, reversed or overflowing ranges; lengthAt and parameterAt outside their domains")
    void validation() {
        Curve c = new LineSegment(Vec3.ZERO, Vec3.X);
        assertThrows(IllegalArgumentException.class, () -> ArcLengthTable.build(null, TIGHT, LIMITS));
        assertThrows(IllegalArgumentException.class, () -> ArcLengthTable.build(c, null, LIMITS));
        assertThrows(IllegalArgumentException.class, () -> ArcLengthTable.build(c, TIGHT, null));
        assertThrows(IllegalArgumentException.class, () -> ArcLengthTable.build(c, 1, 0, TIGHT, LIMITS));
        assertThrows(IllegalArgumentException.class, () -> ArcLengthTable.build(c, Double.NaN, 1, TIGHT, LIMITS));
        assertThrows(IllegalArgumentException.class, () -> ArcLengthTable.build(c, 0, Double.POSITIVE_INFINITY, TIGHT, LIMITS));
        assertThrows(IllegalArgumentException.class, () -> ArcLengthTable.build(c, -Double.MAX_VALUE, Double.MAX_VALUE, TIGHT, LIMITS));
        ArcLengthTable t = ArcLengthTable.build(c, -1, 2, TIGHT, LIMITS);
        assertEquals(3.0, t.length(), 1e-15);
        for (double bad : new double[] {Double.NaN, -1.5, 2.5, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> t.lengthAt(bad));
        }
        for (double bad : new double[] {Double.NaN, -1e-300, Math.nextUp(t.length()), Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> t.parameterAt(bad));
        }
    }
}
