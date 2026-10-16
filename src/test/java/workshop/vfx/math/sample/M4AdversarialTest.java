package workshop.vfx.math.sample;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static workshop.vfx.math.sample.SampleTestSupport.LIMITS;
import static workshop.vfx.math.sample.SampleTestSupport.TIGHT;

import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.curve.CubicBezier;
import workshop.vfx.math.curve.Curve;
import workshop.vfx.math.curve.LineSegment;
import workshop.vfx.math.curve.Spiral;

/** Contract sections 8 and 9: bounded work, failure policies, adversarial curves and extreme magnitudes. */
class M4AdversarialTest {

    @Test
    @DisplayName("Budget: build() never calls derivative() more than maxEvaluations times, evaluations() reports the exact count, and point()/secondDerivative() are never called")
    void buildBudget() {
        SplittableRandom r = new SplittableRandom(441);
        int[] budgets = {256, 257, 300, 1000, 4096, 65536};
        for (int i = 0; i < 120; i++) {
            Curve inner = switch (i % 4) {
                case 0 -> SampleTestSupport.wiggle(r.nextDouble(0.001, 0.1), r.nextInt(1, 400));
                case 1 -> Spiral.conicalSpiral(0.1, 9, 2, r.nextInt(1, 200));
                case 2 -> new CubicBezier(new Vec3(0, 0, 0), new Vec3(1e3, 1, 0), new Vec3(-1e3, 1, 0), new Vec3(1, 0, 0));
                default -> Spiral.circle(r.nextDouble(0.1, 10));
            };
            for (int budget : budgets) {
                SampleTestSupport.CountingCurve c = new SampleTestSupport.CountingCurve(inner);
                ArcLengthTable t = ArcLengthTable.build(c, new ArcLengthTolerance(0, 0x1p-40), new SampleLimits(16, budget));
                assertTrue(c.derivatives <= budget, "derivative calls " + c.derivatives + " > " + budget);
                assertEquals(c.derivatives, t.evaluations(), "evaluations() is exact");
                assertEquals(0, c.points + c.seconds, "build evaluates only derivatives");
                assertTrue(Double.isFinite(t.length()) && t.length() >= 0);
                if (!t.converged()) {
                    assertTrue(t.errorEstimate() > t.effectiveTolerance(), "not converged means the estimate exceeds tau");
                }
            }
        }
    }

    @Test
    @DisplayName("Fast oscillation (200 periods): a small budget terminates unconverged with an honest estimate; a large budget converges; more budget never increases the error estimate much")
    void oscillation() {
        Curve w = SampleTestSupport.wiggle(0.01, 200);
        ArcLengthTable small = ArcLengthTable.build(w, new ArcLengthTolerance(0, 1e-9), new SampleLimits(16, 256));
        assertFalse(small.converged());
        assertTrue(small.errorEstimate() > small.effectiveTolerance());
        ArcLengthTable big = ArcLengthTable.build(w, new ArcLengthTolerance(0, 1e-6), new SampleLimits(16, 1 << 20));
        assertTrue(big.converged(), "converges with enough budget");
        assertTrue(big.errorEstimate() <= big.effectiveTolerance());
        double previous = Double.POSITIVE_INFINITY;
        for (int budget : new int[] {1 << 10, 1 << 12, 1 << 14, 1 << 16}) {
            ArcLengthTable t = ArcLengthTable.build(w, new ArcLengthTolerance(0, 1e-12), new SampleLimits(16, budget));
            assertTrue(t.errorEstimate() <= 4 * previous, "estimate at budget " + budget);
            previous = t.errorEstimate();
        }
    }

    @Test
    @DisplayName("Sampling work: each equal-distance sample calls point() exactly once and derivative() at most 336 times; parameterAt and lengthAt are bounded too")
    void samplingWork() {
        for (Curve inner : new Curve[] {SampleTestSupport.wiggle(0.05, 30), Spiral.conicalSpiral(0, 5, 1, 9), new CubicBezier(Vec3.ZERO, Vec3.X, Vec3.ZERO, Vec3.X)}) {
            SampleTestSupport.CountingCurve c = new SampleTestSupport.CountingCurve(inner);
            ArcLengthTable t = ArcLengthTable.build(c, new ArcLengthTolerance(0, 1e-6), new SampleLimits(4096, 1 << 14));
            c.points = 0;
            c.derivatives = 0;
            int n = 500;
            Sampler.uniformDistance(t, n, Endpoints.INCLUDE_END, LIMITS);
            assertEquals(n, c.points, "point() once per sample");
            assertTrue(c.derivatives <= 336L * n, "derivatives " + c.derivatives);
            c.derivatives = 0;
            c.points = 0;
            t.parameterAt(t.length() * 0.37);
            assertTrue(c.derivatives <= 336 && c.points == 0);
            c.derivatives = 0;
            t.lengthAt(0.61);
            assertTrue(c.derivatives <= 5 && c.points == 0);
        }
    }

    @Test
    @DisplayName("Inversion cost on smooth curves (validated, contract 6.3): Newton steps keep the mean below 40 derivative evaluations per equal-distance sample (pure bisection would need about 6 x 30)")
    void newtonEfficiency() {
        Curve[] smooth = {Spiral.conicalSpiral(0.5, 5, 1, 9), new CubicBezier(new Vec3(0, 0, 0), new Vec3(4, 6, 0), new Vec3(-3, 5, 2), new Vec3(2, 0, 1)),
            new workshop.vfx.math.curve.QuadraticBezier(Vec3.ZERO, new Vec3(3, 7, 0), new Vec3(6, 0, 2))};
        for (Curve inner : smooth) {
            SampleTestSupport.CountingCurve c = new SampleTestSupport.CountingCurve(inner);
            ArcLengthTable t = ArcLengthTable.build(c, new ArcLengthTolerance(0, 0x1p-30), LIMITS);
            c.derivatives = 0;
            int n = 1000;
            Sampler.uniformDistance(t, n, Endpoints.INCLUDE_END, LIMITS);
            assertTrue(c.derivatives / (double) (n - 2) <= 40, inner + ": mean " + c.derivatives / (double) (n - 2));
        }
    }

    @Test
    @DisplayName("Failures: a non-finite derivative gives ArithmeticException; exceptions thrown by a user curve propagate unchanged; overflow of the length gives ArithmeticException")
    void failures() {
        Curve nan = new Curve() {
            public Vec3 point(double t) { return Vec3.ZERO; }
            public Vec3 derivative(double t) { return t > 0.7 ? new Vec3(Double.NaN, 0, 0) : Vec3.X; }
            public Vec3 secondDerivative(double t) { return Vec3.ZERO; }
        };
        assertThrows(ArithmeticException.class, () -> ArcLengthTable.build(nan, TIGHT, LIMITS));
        Curve inf = new Curve() {
            public Vec3 point(double t) { return Vec3.ZERO; }
            public Vec3 derivative(double t) { return new Vec3(0, Double.NEGATIVE_INFINITY, 0); }
            public Vec3 secondDerivative(double t) { return Vec3.ZERO; }
        };
        assertThrows(ArithmeticException.class, () -> ArcLengthTable.build(inf, TIGHT, LIMITS));
        IllegalStateException boom = new IllegalStateException("user curve failure");
        Curve throwing = new Curve() {
            public Vec3 point(double t) { throw boom; }
            public Vec3 derivative(double t) { throw boom; }
            public Vec3 secondDerivative(double t) { throw boom; }
        };
        assertTrue(assertThrows(IllegalStateException.class, () -> ArcLengthTable.build(throwing, TIGHT, LIMITS)) == boom);
        assertTrue(assertThrows(IllegalStateException.class, () -> Sampler.uniformParameter(throwing, 3, Endpoints.INCLUDE_END, LIMITS)) == boom);
        // speeds near 1e308 sum to infinity
        Curve huge = new LineSegment(new Vec3(-1e308, 0, 0), new Vec3(1e308, 0, 0));
        assertThrows(ArithmeticException.class, () -> ArcLengthTable.build(huge, TIGHT, LIMITS));
    }

    @Test
    @DisplayName("Extreme magnitudes: subnormal and 1e100-scale curves give accurate lengths (scaled speed norm: no overflow or underflow of squares); ranges up to +-1e3")
    void magnitudes() {
        for (double scale : new double[] {Double.MIN_VALUE * 64, 1e-310, 1e-200, 1e-160, 1, 1e100, 1e160, 1e200}) {
            Vec3 a = new Vec3(3, 4, 12).mul(scale);
            ArcLengthTable t = ArcLengthTable.build(new LineSegment(Vec3.ZERO, a), TIGHT, LIMITS);
            // the exact length of the stored (rounded) components, by BigDecimal
            java.math.BigDecimal x = new java.math.BigDecimal(a.x()), y = new java.math.BigDecimal(a.y()), z = new java.math.BigDecimal(a.z());
            double exact = x.multiply(x).add(y.multiply(y)).add(z.multiply(z)).sqrt(new java.math.MathContext(40)).doubleValue();
            assertEquals(exact, t.length(), 1e-14 * exact + 64 * Double.MIN_VALUE, "scale " + scale);
            assertTrue(t.converged());
        }
        Spiral s = new Spiral(1, 1, 0, 1, 0);
        ArcLengthTable wide = ArcLengthTable.build(s, -1000, 1000, new ArcLengthTolerance(0, 1e-9), LIMITS);
        assertEquals(2000 * 2 * Math.PI, wide.length(), 1e-9 * wide.length());
        SampleSet set = Sampler.uniformDistance(wide, 2000, Endpoints.EXCLUDE_END, LIMITS);
        for (int k = 0; k < 2000; k++) {
            assertEquals(-1000 + k, set.parameter(k), 1e-6, "one sample per turn at integer t");
        }
    }

    @Test
    @DisplayName("Signed zero and tiny ranges: t0 = -0.0 is kept; a range of one ulp works; t0 == t1 gives length 0 and one repeated point")
    void signedZeroAndTinyRanges() {
        Curve c = new CubicBezier(new Vec3(0, 0, 0), new Vec3(1, 2, 0), new Vec3(3, -1, 1), new Vec3(4, 0, 0));
        ArcLengthTable neg = ArcLengthTable.build(c, -0.0, 1, TIGHT, LIMITS);
        assertTrue(Double.compare(-0.0, neg.startParameter()) == 0);
        assertTrue(Double.compare(-0.0, neg.parameterAt(0)) == 0, "parameterAt(0) is t0 itself");
        double u = 0.3;
        ArcLengthTable ulp = ArcLengthTable.build(c, u, Math.nextUp(u), TIGHT, LIMITS);
        assertTrue(ulp.length() >= 0 && ulp.length() < 1e-14);
        assertDoesNotThrow(() -> Sampler.uniformDistance(ulp, 7, Endpoints.INCLUDE_END, LIMITS));
        ArcLengthTable same = ArcLengthTable.build(c, u, u, TIGHT, LIMITS);
        assertEquals(0.0, same.length());
        SampleSet s = Sampler.uniformDistance(same, 3, Endpoints.INCLUDE_END, LIMITS);
        for (int k = 0; k < 3; k++) {
            assertEquals(u, s.parameter(k));
        }
    }

    @Test
    @DisplayName("Particle-count explosion: the largest permitted request is bounded by SampleLimits; limit violations are reported before any allocation-sized work")
    void explosionGuard() {
        SampleTestSupport.CountingCurve c = new SampleTestSupport.CountingCurve(Spiral.circle(1));
        SampleLimits server = new SampleLimits(512, 4096);
        assertThrows(IllegalArgumentException.class, () -> Sampler.uniformParameter(c, 513, Endpoints.EXCLUDE_END, server));
        assertThrows(IllegalArgumentException.class, () -> Sampler.uniformParameter(c, Integer.MAX_VALUE, Endpoints.EXCLUDE_END, server));
        ArcLengthTable t = ArcLengthTable.build(c, TIGHT, server);
        c.points = 0;
        c.derivatives = 0;
        assertThrows(IllegalArgumentException.class, () -> Sampler.uniformDistance(t, 513, Endpoints.EXCLUDE_END, server));
        assertThrows(IllegalArgumentException.class, () -> Sampler.bySpacing(t, 2 * Math.PI / 1000, Endpoints.EXCLUDE_END, server));
        assertEquals(0, c.points + c.derivatives);
        assertEquals(512, Sampler.uniformDistance(t, 512, Endpoints.EXCLUDE_END, server).size());
    }
}
