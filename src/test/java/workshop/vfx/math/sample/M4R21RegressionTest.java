package workshop.vfx.math.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.curve.Curve;
import workshop.vfx.math.curve.LineSegment;
import workshop.vfx.math.curve.Spiral;

/**
 * Regression tests for the two findings of ChatGPT's independent review of M4 R2 (contract revision 2.1):
 * F4 half-width underflow (a panel narrower than 2 MIN_VALUE had a computed half-width of 0, so its rule value was 0)
 * and F5 weighted-sum overflow (a finite length gave ArithmeticException, and 0 * infinity gave NaN in lengthAt).
 * Oracles: exact BigDecimal lengths of lines and circles, and the allowance of contract 10.2 (R2.1), whose absolute
 * underflow term counts the half-panel rules of nonzero width, derived here from the normative initial partition.
 */
class M4R21RegressionTest {
    private static final double Q = Double.MIN_VALUE;
    private static final double EPS = 0x1p-53;
    private static final double MAX = Double.MAX_VALUE;
    private static final MathContext MC = new MathContext(40);
    private static final BigDecimal PI = new BigDecimal("3.14159265358979323846264338327950288419716939937510582097494459");

    /** Contract 10.2 (R2.1) for a 16-panel table: the number of half-panel rules of nonzero width (contract 5.3 nodes). */
    private static int nonzeroHalves(double t0, double t1) {
        double[] n = new double[17];
        for (int k = 0; k < 16; k++) {
            n[k] = Math.min(t1, t0 + (t1 - t0) * (k / 16.0));
        }
        n[16] = t1;
        int count = 0;
        for (int k = 0; k < 16; k++) {
            double a = n[k], b = n[k + 1], m = a + 0.5 * (b - a);
            count += (m > a ? 1 : 0) + (b > m ? 1 : 0);
        }
        return count;
    }

    /** Exact length of a line from 0 to {@code end} on [t0, t1]. */
    private static double exactLine(Vec3 end, double t0, double t1) {
        BigDecimal x = new BigDecimal(end.x()), y = new BigDecimal(end.y()), z = new BigDecimal(end.z());
        return x.multiply(x).add(y.multiply(y)).add(z.multiply(z)).sqrt(MC).multiply(new BigDecimal(t1).subtract(new BigDecimal(t0))).doubleValue();
    }

    /**
     * Contract 10.2 (R2.1) for a line, whose derivative is exact (delta_c = 0): (t1 - t0) dv + (K + 64) eps L +
     * R_w MIN_VALUE / 2, with dv = 5 eps v and (t1 - t0) v = L (the speed itself may exceed MAX_VALUE).
     */
    private static double lineAllowance(double t0, double t1, double exact) {
        return 5 * EPS * exact + 80 * EPS * exact + (0.5 * nonzeroHalves(t0, t1)) * Q;
    }

    private static void checkLine(Vec3 end, double t0, double t1) {
        LineSegment line = new LineSegment(Vec3.ZERO, end);
        double exact = exactLine(end, t0, t1);
        String label = "line to " + end + " on [" + t0 + ", " + t1 + "] (exact " + exact + ")";
        ArcLengthTable t = ArcLengthTable.build(line, t0, t1, new ArcLengthTolerance(0, 0x1p-40), new SampleLimits(64, 4096));
        assertTrue(Double.isFinite(t.length()), label + ": finite length");
        assertTrue(t.converged(), label + ": converged");
        assertEquals(16, t.panelCount(), label + ": panels");
        assertEquals(exact, t.length(), lineAllowance(t0, t1, exact), label + ": length");
        if (exact > lineAllowance(t0, t1, exact)) {
            assertTrue(t.length() > 0, label + ": a length above the allowance is not lost");
        }
        double first = t.lengthAt(Math.nextUp(t0)), firstExact = exactLine(end, t0, Math.nextUp(t0));
        assertTrue(Double.isFinite(first) && first >= 0 && first <= t.length(), label + ": lengthAt(nextUp(t0)) = " + first);
        assertEquals(firstExact, first, 5 * (5 * EPS + 80 * EPS) * firstExact + 2 * Q, label + ": lengthAt(nextUp(t0))");
        if (t.length() > 0) {
            double p = t.parameterAt(0.5 * t.length());
            assertTrue(p >= t0 && p <= t1, label + ": parameterAt(L/2) = " + p);
        }
    }

    // ------------------------------------------------------------------ F4

    @Test
    @DisplayName("F4: M3 lines on ranges of a few MIN_VALUE (half-width rounds to 0): length within the contract 10.2 allowance (R2.1), whose underflow term is MIN_VALUE/2 per half-panel rule of nonzero width (1 to 7 rules on [0, k MIN_VALUE]); unit speed on [0, k MIN_VALUE] is exact (a consequence of the normative operation order); lengthAt one ulp above t0 accurate")
    void halfWidthUnderflow() {
        Vec3[] ends = {new Vec3(1, 0, 0), new Vec3(3, 0, 0), new Vec3(0, -2, 0), new Vec3(1, 2, 2), new Vec3(1e10, 0, 0), new Vec3(3e299, 4e299, 0), new Vec3(1e300, 0, 0)};
        double[][] ranges = {{0, Q}, {0, 2 * Q}, {0, 3 * Q}, {0, 7 * Q}, {-Q, Q}, {-3 * Q, 0}, {0, 1e-320}, {0, 0x1p-1060}, {0, 1e-310}};
        for (Vec3 end : ends) {
            for (double[] r : ranges) {
                checkLine(end, r[0], r[1]);
            }
        }
        // the representable cases are exact: unit speed on [0, k MIN_VALUE] has length k MIN_VALUE
        for (int k = 1; k <= 7; k++) {
            ArcLengthTable t = ArcLengthTable.build(new LineSegment(Vec3.ZERO, Vec3.X), 0, k * Q, new ArcLengthTolerance(0, 0x1p-40), new SampleLimits(64, 4096));
            assertEquals(k * Q, t.length(), "unit speed on [0, " + k + " MIN_VALUE]");
        }
    }

    // ------------------------------------------------------------------ F5

    @Test
    @DisplayName("F5: finite smooth curves whose weighted node sum overflows (speeds 1e308 to 2.5e308, components up to MAX_VALUE): build returns the finite length, converged, within the allowance; lengthAt and parameterAt finite")
    void weightedSumOverflow() {
        Vec3[] ends = {new Vec3(1e308, 0, 0), new Vec3(-1e308, 1e308, 0), new Vec3(MAX / 2, MAX / 2, MAX / 2), new Vec3(MAX, MAX, 0), new Vec3(MAX, MAX, MAX)};
        double[][] ranges = {{0, Q}, {0, 3 * Q}, {0, 1e-300}, {-1e-300, 1e-300}, {0, 1e-10}, {1, Math.nextUp(1.0)}};
        for (Vec3 end : ends) {
            for (double[] r : ranges) {
                checkLine(end, r[0], r[1]);
            }
        }
        // a circle of radius 1e306 (speed 2 pi 1e306, M3 trigonometry) on [0, 1e-300]: 2 pi 1e306 1e-300
        ArcLengthTable ring = ArcLengthTable.build(Spiral.circle(1e306), 0, 1e-300, new ArcLengthTolerance(0, 0x1p-40), new SampleLimits(64, 4096));
        double exact = PI.multiply(new BigDecimal(2)).multiply(new BigDecimal(1e306)).multiply(new BigDecimal(1e-300)).doubleValue();
        assertTrue(ring.converged());
        assertEquals(exact, ring.length(), 1e-12 * exact, "circle radius 1e306 on [0, 1e-300]");
    }

    @Test
    @DisplayName("F5: a finite table whose partial integral overflowed (speed 1e308, or components at MAX_VALUE, only for t <= 1e-300; 1 elsewhere): lengthAt and parameterAt near t0 are finite, never NaN, and within [0, length()] (F_k need not be monotone for such a curve)")
    void finiteTableNoNaN() {
        for (double big : new double[] {1e308, MAX}) {
            Curve spikeAtStart = new Curve() {
                @Override public Vec3 point(double t) { return new Vec3(t, 0, 0); } // positions are not used by the table
                @Override public Vec3 derivative(double t) { return t <= 1e-300 ? new Vec3(big, big, 0) : Vec3.X; }
                @Override public Vec3 secondDerivative(double t) { return Vec3.ZERO; }
            };
            ArcLengthTable t = ArcLengthTable.build(spikeAtStart, new ArcLengthTolerance(0, 0x1p-30), new SampleLimits(64, 256));
            assertTrue(Double.isFinite(t.length()) && t.length() > 0.99 && t.length() < 1.01, "the table does not see the spike: " + t.length());
            for (double u : new double[] {Q, 2 * Q, 1e-320, 1e-310, 1e-301, 1e-300, 1e-200, 1e-100, 1e-10, 0.01}) {
                double l = t.lengthAt(u);
                assertTrue(Double.isFinite(l) && l >= 0 && l <= t.length(), "big " + big + ": lengthAt(" + u + ") = " + l);
            }
            // one ulp above t0 the half-width is 0: G(0, MIN_VALUE) = MIN_VALUE * speed, scaled by I2 / I1 (within [1/2, 2])
            double g = (Q * big) * Math.sqrt(2); // MIN_VALUE times the speed sqrt(2) big (which may exceed MAX_VALUE): 6.99e-16 or 1.26e-15
            double l = t.lengthAt(Q);
            assertTrue(l >= 0.5 * g * (1 - 1e-12) && l <= Math.min(t.length(), 2 * g * (1 + 1e-12)), "big " + big + ": lengthAt(MIN_VALUE) = " + l + " for G = " + g);
            for (int k = 1; k < 64; k++) {
                double p = t.parameterAt(t.length() * (k / 64.0));
                assertTrue(p >= 0 && p <= 1, "big " + big + ": parameterAt");
            }
        }
    }

    @Test
    @DisplayName("F4: subnormal speeds over long ranges lose at most MIN_VALUE/2 per half-panel rule (no MIN_VALUE per unit of parameter): lines with components (q, q, 0), (q, 2q, 2q), (3q, q, 0) and (q, 0, 0) on [0, 1000] and [-7, 9]")
    void subnormalSpeedLongRange() {
        Vec3[] ends = {new Vec3(Q, Q, 0), new Vec3(Q, 2 * Q, 2 * Q), new Vec3(3 * Q, Q, 0), new Vec3(Q, 0, 0)};
        for (Vec3 end : ends) {
            for (double[] r : new double[][] {{0, 1000}, {-7, 9}}) {
                checkLine(end, r[0], r[1]);
            }
        }
    }

    @Test
    @DisplayName("F4/F5: no spurious overflow or underflow in the panel rule: 3,000 random lines (speeds 1e-300 to 2.9e308 with components up to MAX_VALUE, range widths 1 MIN_VALUE to 1e3, exact lengths below MAX_VALUE/4) all give finite, converged lengths within the allowance")
    void panelRuleRange() {
        SplittableRandom r = new SplittableRandom(4245);
        int done = 0;
        while (done < 3_000) {
            double scale = Math.scalb(1.0, r.nextInt(-1000, 1024));
            Vec3 end = new Vec3(r.nextDouble(-1, 1) * scale, r.nextBoolean() ? 0 : r.nextDouble(-1, 1) * scale, r.nextBoolean() ? 0 : r.nextDouble(-1, 1) * scale);
            double t0 = r.nextBoolean() ? 0 : r.nextDouble(-1, 1) * Math.scalb(1.0, r.nextInt(-1074, 10));
            double width = r.nextInt(4) == 0 ? r.nextInt(1, 9) * Q : Math.scalb(r.nextDouble(1, 2), r.nextInt(-1074, 10));
            double t1 = t0 + width;
            if (!(t1 > t0) || !Double.isFinite(t1)) {
                continue;
            }
            double exact = exactLine(end, t0, t1);
            if (!(exact < MAX / 4) || exactLine(end, 0, 1) == 0) {
                continue;
            }
            checkLine(end, t0, t1);
            done++;
        }
    }
}
