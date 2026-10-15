package workshop.vfx.math.curve;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static workshop.vfx.math.curve.CurveTestSupport.assertExact;
import static workshop.vfx.math.curve.CurveTestSupport.bezierDerivativeTol;
import static workshop.vfx.math.curve.CurveTestSupport.bezierPointTol;
import static workshop.vfx.math.curve.CurveTestSupport.bezierSecondTol;
import static workshop.vfx.math.curve.CurveTestSupport.spiralDerivativeTol;
import static workshop.vfx.math.curve.CurveTestSupport.spiralPointTol;
import static workshop.vfx.math.curve.CurveTestSupport.spiralSecondTol;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.math.core.Vec3;

/**
 * Amendment A3 (subnormal underflow) and the T4 overflow clarification (contract 5 and 7.2). Oracles: exact BigDecimal
 * radii, phases, Bernstein values and contract bounds, a 50-digit pi, and small-angle series with truncation below
 * 1e-900. Errors are measured exactly (BigDecimal), so no expected value is rounded. Nothing here evaluates a curve
 * the way the contract recommends.
 */
class M3UnderflowTest {
    private static final double Q = Double.MIN_VALUE;
    private static final BigDecimal BQ = new BigDecimal(Q);
    private static final BigDecimal PI_LO = new BigDecimal("3.14159265358979323846264338327950288419716939937510");
    private static final BigDecimal PI_HI = new BigDecimal("3.14159265358979323846264338327950288419716939937511");
    private static final MathContext MC = new MathContext(80);
    private static final BigDecimal FOUR = BigDecimal.valueOf(4);
    private static final int[][] CS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

    private static BigDecimal bd(double x) {
        return new BigDecimal(x);
    }

    private static void within(BigDecimal exact, double actual, double tol, String label) {
        BigDecimal err = exact.subtract(bd(actual)).abs();
        if (err.compareTo(bd(tol)) > 0) {
            fail(label + ": exact " + exact.round(MathContext.DECIMAL64) + ", actual " + actual + ", error "
                + err.round(MathContext.DECIMAL64) + " > tolerance " + tol);
        }
    }

    private static void within(BigDecimal[] exact, Vec3 actual, double tol, String label) {
        within(exact[0], actual.x(), tol, label + " x");
        within(exact[1], actual.y(), tol, label + " y");
        within(exact[2], actual.z(), tol, label + " z");
    }

    /** Exact point, derivative and second derivative (contract 5) for given exact cos and sin of the phase. */
    private static BigDecimal[][] exactSpiral(double r0, double r1, double rise, double turns, double t, BigDecimal c, BigDecimal s) {
        BigDecimal bt = bd(t);
        BigDecimal r = r0 == r1 ? bd(r0) : BigDecimal.ONE.subtract(bt).multiply(bd(r0)).add(bt.multiply(bd(r1)));
        BigDecimal dr = bd(r1).subtract(bd(r0));
        BigDecimal w = PI_LO.multiply(BigDecimal.TWO).multiply(bd(turns));
        BigDecimal[] p = {r.multiply(c), bd(rise).multiply(bt), r.multiply(s).negate()};
        BigDecimal[] d = {dr.multiply(c).subtract(w.multiply(r).multiply(s)), bd(rise), dr.multiply(s).negate().subtract(w.multiply(r).multiply(c))};
        BigDecimal k = BigDecimal.TWO.multiply(dr).multiply(w), w2r = w.multiply(w).multiply(r);
        BigDecimal[] dd = {k.multiply(s).negate().subtract(w2r.multiply(c)), BigDecimal.ZERO, k.multiply(c).negate().add(w2r.multiply(s))};
        return new BigDecimal[][] {p, d, dd};
    }

    private static void check(double r0, double r1, double rise, double turns, double start, double t, BigDecimal c, BigDecimal s) {
        Spiral sp = new Spiral(r0, r1, rise, turns, start);
        BigDecimal[][] e = exactSpiral(r0, r1, rise, turns, t, c, s);
        String label = sp + " at t=" + t;
        within(e[0], sp.point(t), spiralPointTol(r0, r1, rise, turns, t), label + " point");
        within(e[1], sp.derivative(t), spiralDerivativeTol(r0, r1, rise, turns, t), label + " derivative");
        within(e[2], sp.secondDerivative(t), spiralSecondTol(r0, r1, turns, t), label + " second");
    }

    private static void checkQuarter(double r0, double r1, double rise, double turns, double start, double t) {
        BigDecimal phase = bd(start).add(bd(turns).multiply(bd(t)));
        int[] cs = CS[phase.multiply(FOUR).toBigIntegerExact().mod(BigInteger.valueOf(4)).intValue()];
        check(r0, r1, rise, turns, start, t, BigDecimal.valueOf(cs[0]), BigDecimal.valueOf(cs[1]));
    }

    /** Non-negative values concentrated in and around the subnormal range. */
    private static double tiny(SplittableRandom r) {
        return switch (r.nextInt(4)) {
            case 0 -> r.nextInt(0, 64) * Q;
            case 1 -> r.nextLong(0, 1L << 52) * Q;
            case 2 -> Double.MIN_NORMAL + r.nextInt(-1000, 1000) * Q;
            default -> r.nextDouble() * Math.scalb(1.0, r.nextInt(-1060, -900));
        };
    }

    @Test
    @DisplayName("A3: r0 = q, r1 = 2q, t = 1/2 (q = MIN_VALUE): no double is within the revision 2 bounds of the exact 1.5 q; the amended bounds hold (radius, spiral point, line)")
    void a3Counterexample() {
        BigDecimal exactR = new BigDecimal("1.5").multiply(BQ);
        BigDecimal s = bd(0.5).multiply(BQ).add(bd(0.5).multiply(bd(2 * Q)));
        assertEquals(0, s.compareTo(exactR), "S = 1.5 q");
        BigDecimal gap = BQ.divide(BigDecimal.TWO);
        // the doubles nearest to 1.5 q are q and 2q, each 0.5 q away, so every double is at least 0.5 q away
        assertEquals(0, exactR.subtract(BQ).compareTo(gap));
        assertEquals(0, bd(2 * Q).subtract(exactR).compareTo(gap));
        BigDecimal r2Radius = s.multiply(bd(0x1p-51));
        BigDecimal r2Point = s.multiply(bd(0x1p-49)).add(s.multiply(PI_HI.multiply(BigDecimal.TWO).multiply(bd(0x1p-51))));
        BigDecimal r2Line = bd(0x1p-49).multiply(bd(2 * Q));
        assertTrue(r2Radius.compareTo(gap) < 0 && r2Point.compareTo(gap) < 0 && r2Line.compareTo(gap) < 0,
            "the revision 2 bounds (" + r2Radius + ", " + r2Point + ", " + r2Line + ") are unsatisfiable for every implementation");

        Spiral sp = new Spiral(Q, 2 * Q, 0, 0, 0);
        double x = sp.point(0.5).x(); // phase 0: x is the evaluated radius times cos 0 = 1
        assertTrue(exactR.subtract(bd(x)).abs().compareTo(r2Radius.add(BigDecimal.TWO.multiply(BQ))) <= 0, "radius rule 2^-51 S + 2 MIN_VALUE: " + x);
        checkQuarter(Q, 2 * Q, 0, 0, 0, 0.5);
        within(exactR, new LineSegment(new Vec3(Q, 0, 0), new Vec3(2 * Q, 0, 0)).point(0.5).x(), bezierPointTol(1, 0.5, 2 * Q), "line");
    }

    @Test
    @DisplayName("A3: the radius underflow error is multiplied by w in the derivative and by w^2 in the second derivative (r0 = q, r1 = 2q, t = 1/2, turns 1000 and others)")
    void radiusUnderflowAmplifiedByW() {
        checkQuarter(Q, 2 * Q, 0, 1000, 0, 0.5);
        checkQuarter(Q, 2 * Q, 0, -1000, 0.25, 0.5);
        checkQuarter(3 * Q, 5 * Q, 0, 4096, 0, -2.5);
        SplittableRandom r = new SplittableRandom(63);
        for (int i = 0; i < 3_000; i++) {
            double r0 = r.nextInt(0, 9) * Q, r1 = r.nextInt(0, 9) * Q;
            double turns = r.nextInt(1, 20_000) * (r.nextBoolean() ? 1 : -1);
            checkQuarter(r0, r1, 0, turns, r.nextInt(0, 4) / 4.0, r.nextInt(-12, 13) / 4.0);
        }
    }

    @Test
    @DisplayName("A3: radii, rise and products in the subnormal range and around MIN_NORMAL, on exact quarter phases, within the amended bounds")
    void subnormalRadii() {
        SplittableRandom r = new SplittableRandom(61);
        double[] turnsSet = {0, 1, -2, 4, 1000, -333};
        double[] ts = {0.5, -5, 3, 1000, -1000, Math.nextDown(1.0), 0x1p-53, Q, 7 * Q, -0.25};
        for (int i = 0; i < 6_000; i++) {
            double r0 = tiny(r), r1 = r.nextInt(4) == 0 ? r0 : tiny(r);
            double turns = turnsSet[r.nextInt(turnsSet.length)];
            double t = turns == 0 ? (r.nextBoolean() ? ts[r.nextInt(ts.length)] : r.nextDouble(-5, 5)) : r.nextInt(-20, 21) / 4.0;
            double rise = r.nextBoolean() ? 0 : tiny(r) * (r.nextBoolean() ? 1 : -1);
            checkQuarter(r0, r1, rise, turns, r.nextInt(-4, 5) / 4.0, t);
        }
    }

    @Test
    @DisplayName("A3: subnormal turns (w = 2 pi turns underflows) with radii up to 1e100: derivatives within the amended bounds (absolute term proportional to S)")
    void subnormalTurns() {
        SplittableRandom r = new SplittableRandom(62);
        double[] ts = {1, -3, 0.5, 1000, -1000, 0.25};
        for (int i = 0; i < 5_000; i++) {
            double mag = switch (r.nextInt(3)) {
                case 0 -> r.nextInt(1, 64) * Q;
                case 1 -> r.nextLong(1, 1L << 52) * Q;
                default -> Double.MIN_NORMAL * r.nextDouble(1, 4);
            };
            double turns = r.nextBoolean() ? mag : -mag;
            double r0 = Math.scalb(r.nextDouble(1, 2), r.nextInt(-10, 330));
            double r1 = r.nextBoolean() ? r0 : r.nextDouble(0, 10);
            double t = ts[r.nextInt(ts.length)];
            double start = r.nextBoolean() ? 0 : 0.25;
            // phase = start + eps with eps = turns * t exactly, |eps| < 2^-1010: small-angle series, truncation < 1e-900
            BigDecimal theta = PI_LO.multiply(BigDecimal.TWO).multiply(bd(turns).multiply(bd(t)));
            BigDecimal cosE = BigDecimal.ONE.subtract(theta.multiply(theta).divide(BigDecimal.TWO, MC));
            BigDecimal c = start == 0 ? cosE : theta.negate(), s = start == 0 ? theta : cosE;
            check(r0, r1, r.nextBoolean() ? 0 : r.nextDouble(-5, 5), turns, start, t, c, s);
        }
    }

    // ------------------------------------------------------------ Beziers and lines

    private static BigDecimal[] bernstein(BigDecimal[][] p, BigDecimal t) {
        int n = p.length - 1;
        BigDecimal[] out = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        for (int i = 0; i <= n; i++) {
            BigDecimal b = new BigDecimal(BigInteger.valueOf(binom(n, i))).multiply(BigDecimal.ONE.subtract(t).pow(n - i)).multiply(t.pow(i));
            for (int c = 0; c < 3; c++) {
                out[c] = out[c].add(b.multiply(p[i][c]));
            }
        }
        return out;
    }

    private static long binom(int n, int k) {
        return n == 3 ? new long[] {1, 3, 3, 1}[k] : n == 2 ? new long[] {1, 2, 1}[k] : n == 1 ? 1 : 1;
    }

    private static BigDecimal[][] diff(BigDecimal[][] p) {
        int n = p.length - 1;
        BigDecimal[][] d = new BigDecimal[n][3];
        for (int i = 0; i < n; i++) {
            for (int c = 0; c < 3; c++) {
                d[i][c] = p[i + 1][c].subtract(p[i][c]).multiply(BigDecimal.valueOf(n));
            }
        }
        return d;
    }

    @Test
    @DisplayName("A3: lines and Beziers with subnormal control points, against the exact Bernstein form, within the amended bounds (absolute term 2 n^k G^n MIN_VALUE)")
    void bezierSubnormalControlPoints() {
        SplittableRandom r = new SplittableRandom(64);
        double[] ts = {0.5, 0.25, -1, 2, 1000, -3, Q, Math.nextDown(1.0)};
        for (int i = 0; i < 6_000; i++) {
            int n = r.nextInt(1, 4);
            Vec3[] v = new Vec3[n + 1];
            BigDecimal[][] p = new BigDecimal[n + 1][];
            double maxP = 0, maxD = 0;
            for (int j = 0; j <= n; j++) {
                v[j] = new Vec3(sign(r) * tiny(r), sign(r) * tiny(r), sign(r) * tiny(r));
                p[j] = new BigDecimal[] {bd(v[j].x()), bd(v[j].y()), bd(v[j].z())};
                maxP = Math.max(maxP, CurveTestSupport.maxAbs(v[j]));
                if (j > 0) {
                    maxD = Math.max(maxD, Math.max(CurveTestSupport.upAbsDiff(v[j].x(), v[j - 1].x()),
                        Math.max(CurveTestSupport.upAbsDiff(v[j].y(), v[j - 1].y()), CurveTestSupport.upAbsDiff(v[j].z(), v[j - 1].z()))));
                }
            }
            double t = r.nextBoolean() ? ts[r.nextInt(ts.length)] : r.nextDouble(-3, 4);
            Curve c = n == 1 ? new LineSegment(v[0], v[1]) : n == 2 ? new QuadraticBezier(v[0], v[1], v[2]) : new CubicBezier(v[0], v[1], v[2], v[3]);
            BigDecimal bt = bd(t);
            BigDecimal[][] d1 = diff(p);
            String label = c + " at t=" + t;
            within(bernstein(p, bt), c.point(t), bezierPointTol(n, t, maxP), label + " point");
            within(n == 1 ? d1[0] : bernstein(d1, bt), c.derivative(t), bezierDerivativeTol(n, t, maxD), label + " derivative");
            if (n > 1) {
                BigDecimal[][] d2 = diff(d1);
                within(n == 2 ? d2[0] : bernstein(d2, bt), c.secondDerivative(t), bezierSecondTol(n, t, maxP), label + " second");
            }
        }
    }

    private static double sign(SplittableRandom r) {
        return r.nextBoolean() ? 1 : -1;
    }

    // ------------------------------------------------------------ the tolerances themselves (contract 7.2, A3 item 5)

    private static BigDecimal pow(BigDecimal x, int n) {
        return x.pow(n);
    }

    @Test
    @DisplayName("A3: the test tolerances are never below the contract bounds computed exactly (BigDecimal, pi rounded up), including subnormal inputs, and never grossly above them")
    void toleranceHelpersAreUpperBounds() {
        double[] radii = {0, Q, 2 * Q, 3 * Q, Double.MIN_NORMAL, 1e-300, 1, 1e100};
        double[] rises = {0, Q, -1e100};
        double[] ts = {0, 0.5, -5, 1000, Q, Math.nextDown(1.0)};
        double[] turnsSet = {0, Q, 7 * Q, 1, 1000, -1e6};
        BigDecimal two = BigDecimal.TWO, q = BQ;
        int n = 0;
        for (double r0 : radii) {
            for (double r1 : radii) {
                for (double rise : rises) {
                    for (double t : ts) {
                        for (double turns : turnsSet) {
                            BigDecimal bt = bd(t), at = bt.abs();
                            BigDecimal s = r0 == r1 ? bd(r0) : BigDecimal.ONE.subtract(bt).abs().multiply(bd(r0)).add(at.multiply(bd(r1)));
                            BigDecimal dr = bd(r1).subtract(bd(r0)).abs();
                            BigDecimal w = two.multiply(PI_HI).multiply(bd(Math.abs(turns)));
                            BigDecimal dphi = two.multiply(PI_HI).multiply(bd(0x1p-52)).multiply(bd(Math.abs(turns)).multiply(at).add(two));
                            BigDecimal point = bd(0x1p-49).multiply(s.add(bd(Math.abs(rise)).multiply(at))).add(s.multiply(dphi)).add(FOUR.multiply(q));
                            BigDecimal bigW = dr.add(w.multiply(s)).add(bd(Math.abs(rise)));
                            BigDecimal deriv = bd(0x1p-48).multiply(bigW).add(bigW.multiply(dphi)).add(FOUR.multiply(q).multiply(BigDecimal.ONE.add(w).add(s)));
                            BigDecimal bigW2 = two.multiply(dr).multiply(w).add(w.multiply(w).multiply(s));
                            BigDecimal second = bd(0x1p-47).multiply(bigW2).add(bigW2.multiply(dphi))
                                .add(FOUR.multiply(q).multiply(BigDecimal.ONE.add(dr).add(s).add(w.multiply(s)).add(w.multiply(w))));
                            String label = "r0=" + r0 + " r1=" + r1 + " rise=" + rise + " t=" + t + " turns=" + turns;
                            BigDecimal amp = BigDecimal.ONE.add(w).pow(2);
                            tight(point, spiralPointTol(r0, r1, rise, turns, t), amp, label + " point");
                            tight(deriv, spiralDerivativeTol(r0, r1, rise, turns, t), amp, label + " derivative");
                            tight(second, spiralSecondTol(r0, r1, turns, t), amp, label + " second");
                            n++;
                        }
                    }
                }
            }
        }
        assertEquals(radii.length * radii.length * rises.length * ts.length * turnsSet.length, n);
        double[] maxes = {0, Q, 3 * Q, Double.MIN_NORMAL, 1, 1e100};
        for (int deg = 1; deg <= 3; deg++) {
            for (double t : ts) {
                for (double m : maxes) {
                    BigDecimal g = BigDecimal.ONE.subtract(bd(t)).abs().add(bd(t).abs());
                    BigDecimal gn = pow(g, deg), bm = bd(m), d = BigDecimal.valueOf(deg);
                    String label = "degree " + deg + " t=" + t + " max=" + m;
                    BigDecimal amp = BigDecimal.ONE.add(g).pow(3);
                    tight(bd(0x1p-49).multiply(gn).multiply(bm).add(two.multiply(d).multiply(gn).multiply(q)), bezierPointTol(deg, t, m), amp, label + " point");
                    tight(bd(0x1p-48).multiply(d).multiply(pow(g, deg - 1)).multiply(bm).add(two.multiply(d.pow(2)).multiply(gn).multiply(q)),
                        bezierDerivativeTol(deg, t, m), amp, label + " derivative");
                    if (deg > 1) {
                        tight(bd(0x1p-46).multiply(FOUR).multiply(d).multiply(d.subtract(BigDecimal.ONE)).multiply(pow(g, deg - 2)).multiply(bm)
                            .add(two.multiply(d.pow(3)).multiply(gn).multiply(q)), bezierSecondTol(deg, t, m), amp, label + " second");
                    }
                }
            }
        }
        // the revision 2 way of computing a tolerance underflows: 2^-49 * MIN_VALUE is 0.0
        assertEquals(0.0, 0x1p-49 * Q);
    }

    /**
     * tol >= the exact bound, and tol <= bound (1 + 2^-20) + 64 amp MIN_VALUE. Upward rounding costs a few ulps, but a
     * subnormal intermediate (S, |1 - t|, ...) is quantized to multiples of MIN_VALUE before it is multiplied by the
     * amplification amp ((1 + w)^2 for spirals, (1 + G)^3 for Beziers), so the excess is a few MIN_VALUE times amp.
     */
    private static void tight(BigDecimal bound, double tol, BigDecimal amp, String label) {
        assertTrue(bd(tol).compareTo(bound) >= 0, label + ": tolerance " + tol + " below the contract bound " + bound.round(MathContext.DECIMAL64));
        BigDecimal limit = bound.multiply(BigDecimal.ONE.add(bd(0x1p-20))).add(BigDecimal.valueOf(64).multiply(amp).multiply(BQ));
        assertTrue(bd(tol).compareTo(limit) <= 0, label + ": tolerance " + tol + " far above the contract bound " + bound.round(MathContext.DECIMAL64));
    }

    // ------------------------------------------------------------ T4 clarification: no promise when turns * k overflows

    @Test
    @DisplayName("T4 (clarified): seams are exact up to the largest k for which the double product turns * k is finite; beyond that only 'no exception' is promised")
    void seamsUpToOverflow() {
        double[][] cases = {{3, Math.scalb(1.0, 1021)}, {1, Double.MAX_VALUE}, {-7, -Math.scalb(1.0, 1020)}, {0x1p600, 0x1p423},
            {5, Math.nextDown(Double.MAX_VALUE / 5)}};
        for (double[] c : cases) {
            double turns = c[0], k = c[1];
            assertTrue(Double.isFinite(turns * k) && k == Math.rint(k), "precondition: finite product, integer k");
            Spiral s = new Spiral(2.5, 2.5, 0, turns, 0.375);
            assertTrue(s.closed());
            assertExact(s.point(0), s.point(k), "point seam, turns=" + turns + " k=" + k);
            assertExact(s.derivative(0), s.derivative(k), "derivative seam, turns=" + turns + " k=" + k);
        }
        // overflow: turns * k is infinite. The contract promises no exception (section 8), and nothing about the value.
        Spiral s = new Spiral(1, 1, 0, 0x1p600, 0);
        assertTrue(Double.isInfinite(0x1p600 * 0x1p500));
        assertDoesNotThrow(() -> s.point(0x1p500));
        assertDoesNotThrow(() -> s.derivative(0x1p500));
        assertDoesNotThrow(() -> s.secondDerivative(0)); // w^2 r overflows here even at t = 0
    }
}
