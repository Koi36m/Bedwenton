package workshop.vfx.math.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static workshop.vfx.math.sample.SampleTestSupport.LIMITS;
import static workshop.vfx.math.sample.SampleTestSupport.TIGHT;
import static workshop.vfx.math.sample.SampleTestSupport.assertExact;

import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.curve.CubicBezier;
import workshop.vfx.math.curve.Curve;
import workshop.vfx.math.curve.LineSegment;
import workshop.vfx.math.curve.QuadraticBezier;
import workshop.vfx.math.curve.Spiral;

/** Contract section 6: equal-distance sampling by count and by spacing, seams, monotonicity and degenerate curves. */
class EqualDistanceTest {

    /** Distance error bound of the contract (6.3): 2 tau + rounding allowance. */
    private static double distanceBound(ArcLengthTable t, double allowance) {
        return 2 * t.effectiveTolerance() + allowance;
    }

    @Test
    @DisplayName("Conical and planar spirals (radius changing, as for a spiral around a trident): consecutive samples are equally spaced in arc length within 2 tau (closed-form oracle)")
    void spiralsEquallySpaced() {
        SplittableRandom r = new SplittableRandom(421);
        for (int i = 0; i < 400; i++) {
            Spiral s = new Spiral(r.nextDouble(0.2, 3), r.nextDouble(0.2, 6), r.nextBoolean() ? 0 : r.nextDouble(-8, 8), r.nextDouble(-12, 12), r.nextDouble());
            ArcLengthTable t = ArcLengthTable.build(s, TIGHT, LIMITS);
            int n = r.nextInt(2, 400);
            Endpoints e = r.nextBoolean() ? Endpoints.INCLUDE_END : Endpoints.EXCLUDE_END;
            SampleSet set = Sampler.uniformDistance(t, n, e, LIMITS);
            double total = SampleTestSupport.spiralLength(s, 0, 1);
            double allowance = 1e-12 * total + SampleTestSupport.speedErrorBound("SPIRAL", new double[] {s.startRadius(), s.endRadius(), s.rise(), s.turns(), s.startTurn()}, 0, 1);
            for (int k = 0; k < n; k++) {
                double target = t.length() * (k / (double) (e == Endpoints.INCLUDE_END ? n - 1 : n));
                double actual = SampleTestSupport.spiralLength(s, 0, set.parameter(k));
                assertEquals(target, actual, distanceBound(t, allowance), s + " sample " + k + "/" + n + " " + e);
                assertExact(s.point(set.parameter(k)), set.point(k), "point(t) bit for bit");
            }
        }
    }

    @Test
    @DisplayName("Quadratic Beziers: equal arc-length spacing within 2 tau (closed-form oracle), including extrapolated ranges")
    void quadraticsEquallySpaced() {
        SplittableRandom r = new SplittableRandom(422);
        for (int i = 0; i < 400; i++) {
            QuadraticBezier q = new QuadraticBezier(rv(r, 5), rv(r, 5), rv(r, 5));
            double t0 = r.nextBoolean() ? 0 : r.nextDouble(-1, 0), t1 = r.nextBoolean() ? 1 : r.nextDouble(1, 2);
            ArcLengthTable t = ArcLengthTable.build(q, t0, t1, TIGHT, LIMITS);
            int n = r.nextInt(2, 200);
            SampleSet set = Sampler.uniformDistance(t, n, Endpoints.INCLUDE_END, LIMITS);
            double total = SampleTestSupport.quadLength(q, t0, t1);
            for (int k = 0; k < n; k++) {
                double target = t.length() * (k / (double) (n - 1));
                assertEquals(target, SampleTestSupport.quadLength(q, t0, set.parameter(k)), distanceBound(t, 1e-11 * total), q + " k=" + k);
            }
            assertEquals(t0, set.parameter(0));
            assertEquals(t1, set.parameter(n - 1));
        }
    }

    private static Vec3 rv(SplittableRandom r, double m) {
        return new Vec3(r.nextDouble(-m, m), r.nextDouble(-m, m), r.nextDouble(-m, m));
    }

    @Test
    @DisplayName("Constant-speed curves: equal-distance parameters equal the uniform parameters within 2 tau / speed; a closed circle sampled with EXCLUDE_END has no duplicate seam point")
    void constantSpeedAndSeams() {
        for (int n : new int[] {1, 2, 3, 12, 36, 360, 1000}) {
            Spiral ring = Spiral.circle(4); // a healing ring
            ArcLengthTable t = ArcLengthTable.build(ring, TIGHT, LIMITS);
            SampleSet loop = Sampler.uniformDistance(t, n, Endpoints.EXCLUDE_END, LIMITS);
            assertEquals(n, loop.size());
            double speed = 2 * Math.PI * 4;
            for (int k = 0; k < n; k++) {
                assertEquals(k / (double) n, loop.parameter(k), (2 * t.effectiveTolerance() + 1e-13) / speed, "ring sample " + k);
            }
            assertExact(ring.point(0), loop.point(0), "the loop starts at the seam");
            if (n > 1) {
                assertTrue(loop.point(n - 1).distance(loop.point(0)) > 0.5 * t.length() / n, "the last sample is one spacing before the seam, not on it");
            }
            SampleSet open = Sampler.uniformDistance(t, n, Endpoints.INCLUDE_END, LIMITS);
            if (n > 1) {
                assertEquals(1.0, open.parameter(n - 1));
                assertTrue(open.point(n - 1).distance(open.point(0)) < 1e-12, "an open sampling of a closed curve repeats the seam (why loops use EXCLUDE_END)");
            }
        }
    }

    @Test
    @DisplayName("Parameters are non-decreasing and within [t0, t1] for every policy and count, also for cusps, stationary points and adversarial curves")
    void monotoneParameters() {
        SplittableRandom r = new SplittableRandom(423);
        Curve[] curves = {
            new CubicBezier(new Vec3(0, 0, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 0, 0)),
            new CubicBezier(new Vec3(0, 0, 0), new Vec3(2, 2, 0), new Vec3(-1, 2, 0), new Vec3(1, 0, 0)),
            new QuadraticBezier(new Vec3(0, 0, 0), new Vec3(2, 0, 0), new Vec3(1, 0, 0)),
            SampleTestSupport.wiggle(0.01, 50), Spiral.conicalSpiral(0, 5, 3, 7)};
        for (Curve c : curves) {
            ArcLengthTable t = ArcLengthTable.build(c, new ArcLengthTolerance(0, 1e-6), new SampleLimits(4096, 1 << 14));
            for (int i = 0; i < 20; i++) {
                int n = r.nextInt(0, 2000);
                Endpoints e = r.nextBoolean() ? Endpoints.INCLUDE_END : Endpoints.EXCLUDE_END;
                SampleSet s = Sampler.uniformDistance(t, n, e, new SampleLimits(4096, 256));
                for (int k = 0; k < n; k++) {
                    double p = s.parameter(k);
                    assertTrue(p >= 0 && p <= 1, "inside");
                    if (k > 0) {
                        assertTrue(p >= s.parameter(k - 1), c + " non-decreasing at " + k);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Monotone parameters under a loose tolerance (relative 2^-3) and dense sampling (60,000 samples), where the inversion's stopping tolerance exceeds the spacing")
    void monotoneUnderLooseTolerance() {
        Curve[] curves = {new CubicBezier(new Vec3(0, 0, 0), new Vec3(3, 4, 0), new Vec3(-2, 4, 1), new Vec3(1, 0, 0)),
            Spiral.conicalSpiral(0.2, 4, 2, 9), SampleTestSupport.wiggle(0.03, 12)};
        for (Curve c : curves) {
            ArcLengthTable t = ArcLengthTable.build(c, new ArcLengthTolerance(0, 0x1p-3), LIMITS);
            for (Endpoints e : Endpoints.values()) {
                SampleSet s = Sampler.uniformDistance(t, 60_000, e, LIMITS);
                for (int k = 1; k < s.size(); k++) {
                    assertTrue(s.parameter(k) >= s.parameter(k - 1), c + " " + e + " non-decreasing at " + k);
                }
            }
        }
    }

    @Test
    @DisplayName("Zero-length curves: equal-distance sampling falls back to the uniform parameters (all points coincide); spacing gives exactly one sample")
    void zeroLength() {
        CubicBezier dot = new CubicBezier(Vec3.Y, Vec3.Y, Vec3.Y, Vec3.Y);
        ArcLengthTable t = ArcLengthTable.build(dot, -1, 3, TIGHT, LIMITS);
        assertEquals(0.0, t.length());
        SampleSet byCount = Sampler.uniformDistance(t, 5, Endpoints.INCLUDE_END, LIMITS);
        SampleSet uniform = Sampler.uniformParameter(dot, -1, 3, 5, Endpoints.INCLUDE_END, LIMITS);
        for (int k = 0; k < 5; k++) {
            assertEquals(uniform.parameter(k), byCount.parameter(k));
            assertExact(Vec3.Y, byCount.point(k), "collapsed point");
        }
        for (Endpoints e : Endpoints.values()) {
            SampleSet one = Sampler.bySpacing(t, 0.25, e, LIMITS);
            assertEquals(1, one.size());
            assertEquals(-1.0, one.parameter(0));
        }
    }

    @Test
    @DisplayName("Spacing: count = round-half-up(L / d) intervals (+1 for INCLUDE_END, at least 1 for EXCLUDE_END); the realised spacing is L / intervals; denser spacing on a longer spiral keeps the density")
    void spacing() {
        LineSegment ten = new LineSegment(Vec3.ZERO, new Vec3(10, 0, 0));
        ArcLengthTable t = ArcLengthTable.build(ten, TIGHT, LIMITS);
        assertEquals(41, Sampler.bySpacing(t, 0.25, Endpoints.INCLUDE_END, LIMITS).size());
        assertEquals(40, Sampler.bySpacing(t, 0.25, Endpoints.EXCLUDE_END, LIMITS).size());
        assertEquals(4, Sampler.bySpacing(t, 3, Endpoints.INCLUDE_END, LIMITS).size(), "10/3 = 3.33 -> 3 intervals");
        // exact halves depend on the last bit of the computed length; the rule itself is pinned by counts.csv
        assertEquals(Sampler.countForSpacing(t.length(), 4, Endpoints.INCLUDE_END), Sampler.bySpacing(t, 4, Endpoints.INCLUDE_END, LIMITS).size());
        assertEquals(1, Sampler.bySpacing(t, 25, Endpoints.INCLUDE_END, LIMITS).size(), "10/25 = 0.4 -> 0 intervals: the start only");
        assertEquals(1, Sampler.bySpacing(t, 25, Endpoints.EXCLUDE_END, LIMITS).size());
        SampleSet s = Sampler.bySpacing(t, 0.3, Endpoints.INCLUDE_END, LIMITS); // 33.3 -> 33 intervals of 10/33
        assertEquals(34, s.size());
        for (int k = 1; k < s.size(); k++) {
            assertEquals(10.0 / 33, s.point(k).x() - s.point(k - 1).x(), 1e-12);
        }
        // the same spacing on a small and a large conical spiral gives the same density (samples per block) within one sample
        for (double scale : new double[] {1, 2, 5}) {
            Spiral cone = Spiral.conicalSpiral(0.5 * scale, 2 * scale, 4 * scale, 6);
            ArcLengthTable ct = ArcLengthTable.build(cone, TIGHT, LIMITS);
            SampleSet cs = Sampler.bySpacing(ct, 0.25, Endpoints.INCLUDE_END, LIMITS);
            assertEquals(ct.length() / 0.25, cs.size() - 1, 0.5 + 1e-9, "density at scale " + scale);
        }
    }

    @Test
    @DisplayName("Spacing validation and explosion guard: non-positive, non-finite or tiny spacing is rejected before any sample is evaluated; countForSpacing saturates")
    void spacingGuards() {
        SampleTestSupport.CountingCurve c = new SampleTestSupport.CountingCurve(new LineSegment(Vec3.ZERO, new Vec3(1000, 0, 0)));
        ArcLengthTable t = ArcLengthTable.build(c, TIGHT, LIMITS);
        c.points = 0;
        c.derivatives = 0;
        for (double bad : new double[] {0, -1, -0.0, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> Sampler.bySpacing(t, bad, Endpoints.INCLUDE_END, LIMITS));
        }
        assertThrows(IllegalArgumentException.class, () -> Sampler.bySpacing(t, 1e-300, Endpoints.INCLUDE_END, LIMITS), "1e303 samples");
        assertThrows(IllegalArgumentException.class, () -> Sampler.bySpacing(t, 1000.0 / 70_000, Endpoints.INCLUDE_END, LIMITS), "above maxSamples");
        assertEquals(0, c.points + c.derivatives, "nothing evaluated");
        assertEquals(Long.MAX_VALUE, Sampler.countForSpacing(1e300, 1e-300, Endpoints.INCLUDE_END));
        assertEquals(Long.MAX_VALUE, Sampler.countForSpacing(Double.MAX_VALUE, Double.MIN_VALUE, Endpoints.EXCLUDE_END));
        assertThrows(IllegalArgumentException.class, () -> Sampler.countForSpacing(-1, 1, Endpoints.INCLUDE_END));
        assertThrows(IllegalArgumentException.class, () -> Sampler.countForSpacing(Double.NaN, 1, Endpoints.INCLUDE_END));
        assertThrows(IllegalArgumentException.class, () -> Sampler.countForSpacing(1, 0, Endpoints.INCLUDE_END));
        assertThrows(IllegalArgumentException.class, () -> Sampler.countForSpacing(1, 1, null));
        assertThrows(IllegalArgumentException.class, () -> Sampler.uniformDistance(null, 3, Endpoints.INCLUDE_END, LIMITS));
        assertThrows(IllegalArgumentException.class, () -> Sampler.uniformDistance(t, -1, Endpoints.INCLUDE_END, LIMITS));
        assertThrows(IllegalArgumentException.class, () -> Sampler.uniformDistance(t, 5, Endpoints.INCLUDE_END, new SampleLimits(4, 256)));
    }
}
