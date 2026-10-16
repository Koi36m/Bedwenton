package workshop.vfx.math.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static workshop.vfx.math.sample.SampleTestSupport.LIMITS;
import static workshop.vfx.math.sample.SampleTestSupport.assertExact;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.curve.CubicBezier;
import workshop.vfx.math.curve.Curve;
import workshop.vfx.math.curve.LineSegment;
import workshop.vfx.math.curve.Spiral;

/** Contract section 4: uniform parameter sampling (normative parameters, endpoint policies, counts). */
class UniformParameterTest {

    private static final Curve CUBIC = new CubicBezier(new Vec3(0, 0, 0), new Vec3(1, 2, 0), new Vec3(3, -1, 1), new Vec3(4, 0, 0));

    @Test
    @DisplayName("On [0, 1] the parameters are exactly the correctly rounded k/(n-1) (INCLUDE_END) and k/n (EXCLUDE_END)")
    void unitRangeIsCorrectlyRounded() {
        for (int n : new int[] {2, 3, 5, 7, 10, 64, 100, 1000, 4097}) {
            SampleSet open = Sampler.uniformParameter(CUBIC, n, Endpoints.INCLUDE_END, LIMITS);
            SampleSet loop = Sampler.uniformParameter(CUBIC, n, Endpoints.EXCLUDE_END, LIMITS);
            assertEquals(n, open.size());
            assertEquals(n, loop.size());
            for (int k = 0; k < n; k++) {
                // BigDecimal division rounded to the nearest double: an independent statement of fl(k / den)
                double incl = new BigDecimal(k).divide(new BigDecimal(n - 1), MathContext.DECIMAL128).doubleValue();
                double excl = new BigDecimal(k).divide(new BigDecimal(n), MathContext.DECIMAL128).doubleValue();
                assertEquals(incl, open.parameter(k), "INCLUDE_END n=" + n + " k=" + k);
                assertEquals(excl, loop.parameter(k), "EXCLUDE_END n=" + n + " k=" + k);
            }
            assertEquals(1.0, open.parameter(n - 1));
            assertTrue(loop.parameter(n - 1) < 1.0, "a closed loop never repeats the seam point");
        }
    }

    @Test
    @DisplayName("Counts 0, 1 and 2: empty set; one sample at the start (representative value t0) for both policies; both ends")
    void smallCounts() {
        for (Endpoints e : Endpoints.values()) {
            assertEquals(0, Sampler.uniformParameter(CUBIC, 0, e, LIMITS).size());
            SampleSet one = Sampler.uniformParameter(CUBIC, -2, 3, 1, e, LIMITS);
            assertEquals(1, one.size());
            assertEquals(-2.0, one.parameter(0));
            assertExact(CUBIC.point(-2), one.point(0), "count 1 is the start point");
        }
        SampleSet two = Sampler.uniformParameter(CUBIC, -2, 3, 2, Endpoints.INCLUDE_END, LIMITS);
        assertEquals(-2.0, two.parameter(0));
        assertEquals(3.0, two.parameter(1));
        SampleSet half = Sampler.uniformParameter(CUBIC, -2, 3, 2, Endpoints.EXCLUDE_END, LIMITS);
        assertEquals(0.5, half.parameter(1));
    }

    @Test
    @DisplayName("Exact endpoints and points: first parameter t0, last t1 (INCLUDE_END), every point equals curve.point(t) bit for bit; parameters non-decreasing; extrapolated and signed-zero ranges")
    void endpointsAndPoints() {
        SplittableRandom r = new SplittableRandom(401);
        for (int i = 0; i < 3_000; i++) {
            double t0 = switch (r.nextInt(4)) {
                case 0 -> 0.0;
                case 1 -> -0.0;
                case 2 -> r.nextDouble(-1000, 1000);
                default -> r.nextDouble(-1, 1) * Math.scalb(1.0, r.nextInt(-1074, 60));
            };
            double t1 = t0 + switch (r.nextInt(3)) {
                case 0 -> 0;
                case 1 -> r.nextDouble(0, 2000);
                default -> Math.ulp(t0) * r.nextInt(1, 10);
            };
            int n = r.nextInt(0, 300);
            Endpoints e = r.nextBoolean() ? Endpoints.INCLUDE_END : Endpoints.EXCLUDE_END;
            Curve c = r.nextBoolean() ? CUBIC : new Spiral(2, 3, 1, 4, 0.1);
            if (Math.abs(t0) > 1e6) {
                c = new LineSegment(Vec3.ZERO, Vec3.X);
            }
            SampleSet s = Sampler.uniformParameter(c, t0, t1, n, e, LIMITS);
            assertEquals(n, s.size());
            for (int k = 0; k < n; k++) {
                double t = s.parameter(k);
                assertTrue(t >= t0 && t <= t1, "inside the range");
                if (k > 0) {
                    assertTrue(t >= s.parameter(k - 1), "non-decreasing");
                }
                assertExact(c.point(t), s.point(k), "point(t) bit for bit");
            }
            if (n > 0) {
                assertTrue(Double.compare(t0, s.parameter(0)) == 0, "first parameter is t0 itself (sign of zero kept)");
                if (e == Endpoints.INCLUDE_END && n > 1) {
                    assertEquals(t1, s.parameter(n - 1), "last parameter is t1");
                }
            }
        }
    }

    @Test
    @DisplayName("Uniform parameter samples of constant-speed curves are equally spaced in distance (circle chords equal to rounding)")
    void constantSpeedEquality() {
        Spiral circle = Spiral.circle(7);
        SampleSet s = Sampler.uniformParameter(circle, 24, Endpoints.EXCLUDE_END, LIMITS);
        double chord = 2 * 7 * Math.sin(Math.PI / 24);
        for (int k = 0; k < 24; k++) {
            double d = s.point(k).distance(s.point((k + 1) % 24));
            assertEquals(chord, d, 1e-13, "chord " + k);
        }
    }

    @Test
    @DisplayName("Validation: null curve, policy or limits; non-finite or reversed range; range width overflow; negative count; count above maxSamples (before any evaluation)")
    void validation() {
        SampleTestSupport.CountingCurve c = new SampleTestSupport.CountingCurve(CUBIC);
        assertThrows(IllegalArgumentException.class, () -> Sampler.uniformParameter(null, 3, Endpoints.INCLUDE_END, LIMITS));
        assertThrows(IllegalArgumentException.class, () -> Sampler.uniformParameter(c, 3, null, LIMITS));
        assertThrows(IllegalArgumentException.class, () -> Sampler.uniformParameter(c, 3, Endpoints.INCLUDE_END, null));
        assertThrows(IllegalArgumentException.class, () -> Sampler.uniformParameter(c, -1, Endpoints.INCLUDE_END, LIMITS));
        assertThrows(IllegalArgumentException.class, () -> Sampler.uniformParameter(c, 1, 0, 3, Endpoints.INCLUDE_END, LIMITS));
        assertThrows(IllegalArgumentException.class, () -> Sampler.uniformParameter(c, Double.NaN, 1, 3, Endpoints.INCLUDE_END, LIMITS));
        assertThrows(IllegalArgumentException.class, () -> Sampler.uniformParameter(c, 0, Double.POSITIVE_INFINITY, 3, Endpoints.INCLUDE_END, LIMITS));
        assertThrows(IllegalArgumentException.class, () -> Sampler.uniformParameter(c, -Double.MAX_VALUE, Double.MAX_VALUE, 3, Endpoints.INCLUDE_END, LIMITS));
        SampleLimits small = new SampleLimits(100, 256);
        assertEquals(100, Sampler.uniformParameter(c, 100, Endpoints.INCLUDE_END, small).size());
        c.points = 0;
        assertThrows(IllegalArgumentException.class, () -> Sampler.uniformParameter(c, 101, Endpoints.INCLUDE_END, small));
        assertThrows(IllegalArgumentException.class, () -> Sampler.uniformParameter(c, Integer.MAX_VALUE, Endpoints.EXCLUDE_END, small));
        assertEquals(0, c.points + c.derivatives + c.seconds, "rejected before evaluating the curve");
    }

    @Test
    @DisplayName("Uniform parameter sampling evaluates point(t) exactly once per sample and never the derivatives")
    void evaluationCount() {
        SampleTestSupport.CountingCurve c = new SampleTestSupport.CountingCurve(CUBIC);
        Sampler.uniformParameter(c, 257, Endpoints.EXCLUDE_END, LIMITS);
        assertEquals(257, c.points);
        assertEquals(0, c.derivatives + c.seconds);
    }
}
