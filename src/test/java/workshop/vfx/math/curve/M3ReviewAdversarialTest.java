package workshop.vfx.math.curve;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static workshop.vfx.math.curve.CurveTestSupport.assertExact;
import static workshop.vfx.math.curve.CurveTestSupport.bezierDerivativeTol;
import static workshop.vfx.math.curve.CurveTestSupport.bezierPointTol;
import static workshop.vfx.math.curve.CurveTestSupport.bezierSecondTol;
import static workshop.vfx.math.curve.CurveTestSupport.maxAbs;
import static workshop.vfx.math.curve.CurveTestSupport.spiralDerivativeTol;
import static workshop.vfx.math.curve.CurveTestSupport.spiralPointTol;
import static workshop.vfx.math.curve.CurveTestSupport.spiralSecondTol;
import static workshop.vfx.math.curve.CurveTestSupport.upAbsDiff;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Quat;
import workshop.vfx.math.core.Vec3;

/**
 * Independent adversarial review of the M3 production code (Claude Code review, commit d76258f). Written after reading
 * the production sources, but the oracles do not reuse its formulas:
 * - Bezier and line values: the exact Bernstein form in BigDecimal;
 * - spiral values: the exact rational phase reduced exactly in BigDecimal, then sin and cos by 80-digit Taylor
 *   series (no quadrant table, no floating-point trigonometry), and the exact radius;
 * - easing: exact polynomials in BigDecimal;
 * - posing: an exact BigDecimal rotation matrix built from the pose's quaternion;
 * - plus metamorphic relations, M1 quaternion rotation, and bitwise determinism across threads.
 * Error bounds are those of contract 7.2 (R2 + A3), computed by the frozen CurveTestSupport helpers; errors are
 * measured exactly. Nothing here changes production code, existing tests, tolerances or reference data.
 */
class M3ReviewAdversarialTest {
    private static final double Q = Double.MIN_VALUE;
    private static final MathContext MC = new MathContext(90, RoundingMode.HALF_EVEN);
    private static final BigDecimal PI = new BigDecimal(
        "3.14159265358979323846264338327950288419716939937510582097494459230781640628620899862803482534211706798");
    private static final BigDecimal TWO = BigDecimal.TWO;

    private static BigDecimal bd(double x) {
        return new BigDecimal(x);
    }

    private static void within(BigDecimal exact, double actual, double tol, String label) {
        if (!Double.isFinite(actual)) {
            fail(label + ": non-finite result " + actual);
        }
        BigDecimal err = exact.subtract(bd(actual)).abs();
        if (err.compareTo(bd(tol)) > 0) {
            fail(label + ": exact " + exact.round(MathContext.DECIMAL64) + ", actual " + actual + ", error "
                + err.round(MathContext.DECIMAL64) + " > bound " + tol);
        }
    }

    private static void within(BigDecimal[] exact, Vec3 actual, double tol, String label) {
        within(exact[0], actual.x(), tol, label + " x");
        within(exact[1], actual.y(), tol, label + " y");
        within(exact[2], actual.z(), tol, label + " z");
    }

    // ================================================================== parameters and exceptions (contract 4, 5, 8)

    @Test
    @DisplayName("R01: every method of every curve, posed or not, rejects NaN and infinite t; extreme finite inputs never throw")
    void parametersAndNoExceptions() {
        List<Curve> curves = List.of(new LineSegment(Vec3.X, Vec3.Y), new QuadraticBezier(Vec3.X, Vec3.Y, Vec3.Z),
            new CubicBezier(Vec3.X, Vec3.Y, Vec3.Z, Vec3.ZERO), new Spiral(1, 2, 3, 4, 5), Spiral.circle(1));
        for (Curve c0 : curves) {
            for (Curve c : List.of(c0, c0.transformed(new Pose(new Vec3(1, 2, 3), Quat.axisAngle(Vec3.Y, 1), 2)))) {
                for (double t : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
                    assertThrows(IllegalArgumentException.class, () -> c.point(t), c + " point " + t);
                    assertThrows(IllegalArgumentException.class, () -> c.derivative(t), c + " derivative " + t);
                    assertThrows(IllegalArgumentException.class, () -> c.secondDerivative(t), c + " second " + t);
                }
            }
        }
        assertThrows(IllegalArgumentException.class, () -> new Spiral(-Double.MIN_VALUE, 1, 0, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> Spiral.circle(1).transformed(null));
        double big = Double.MAX_VALUE;
        Vec3 huge = new Vec3(big, -big, big);
        List<Curve> extreme = List.of(new LineSegment(huge, huge.negate()), new QuadraticBezier(huge, huge.negate(), huge),
            new CubicBezier(huge, huge.negate(), huge, huge.negate()), new Spiral(big, big, big, big, big),
            new Spiral(0, big, -big, -big, -big), new Spiral(big, 0, 1, Q, Double.MIN_NORMAL));
        for (Curve c : extreme) {
            for (double t : new double[] {0, -0.0, 1, big, -big, Q, 0.5}) {
                assertDoesNotThrow(() -> c.point(t), c + " point " + t);
                assertDoesNotThrow(() -> c.derivative(t), c + " derivative " + t);
                assertDoesNotThrow(() -> c.secondDerivative(t), c + " second " + t);
            }
        }
        for (Easing e : Easing.values()) {
            for (double p : new double[] {-big, big, Q, -Q, 1 - 0x1p-53, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
                double v = e.apply(p);
                assertTrue(v >= 0 && v <= 1, e + " " + p);
            }
            assertThrows(IllegalArgumentException.class, () -> e.apply(Double.NaN));
        }
    }

    // ================================================================== Beziers and lines

    private static BigDecimal[] bernstein(BigDecimal[][] p, BigDecimal t) {
        int n = p.length - 1;
        BigDecimal[] out = {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        BigDecimal s = BigDecimal.ONE.subtract(t);
        for (int i = 0; i <= n; i++) {
            BigDecimal b = BigDecimal.valueOf(new long[][] {{1}, {1, 1}, {1, 2, 1}, {1, 3, 3, 1}}[n][i]).multiply(s.pow(n - i)).multiply(t.pow(i));
            for (int c = 0; c < 3; c++) {
                out[c] = out[c].add(b.multiply(p[i][c]));
            }
        }
        return out;
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

    private static double randomMagnitude(SplittableRandom r) {
        return switch (r.nextInt(6)) {
            case 0 -> r.nextLong(0, 1L << 52) * Q;
            case 1 -> Math.pow(10, r.nextInt(-300, 101));
            case 2 -> 1;
            case 3 -> 1e7;
            case 4 -> 3e7;
            default -> Math.pow(10, r.nextInt(-6, 16));
        };
    }

    private static double randomT(SplittableRandom r) {
        return switch (r.nextInt(8)) {
            case 0, 1, 2 -> r.nextDouble();
            case 3 -> r.nextDouble(-1e3, 1e3);
            case 4 -> r.nextDouble(-5, 5);
            case 5 -> new double[] {0, -0.0, 1, Math.nextDown(1.0), Math.nextUp(1.0), Q, -Q, Math.nextDown(0.0), 0.5, -1e3, 1e3}[r.nextInt(11)];
            case 6 -> r.nextInt(-64, 129) / 64.0;
            default -> 1 - r.nextDouble() * 0x1p-40;
        };
    }

    private static Curve bezier(Vec3[] v) {
        return switch (v.length) {
            case 2 -> new LineSegment(v[0], v[1]);
            case 3 -> new QuadraticBezier(v[0], v[1], v[2]);
            default -> new CubicBezier(v[0], v[1], v[2], v[3]);
        };
    }

    private static void checkBezier(Vec3[] v, double t, String label) {
        int n = v.length - 1;
        BigDecimal[][] p = new BigDecimal[n + 1][];
        double maxP = 0, maxD = 0;
        for (int j = 0; j <= n; j++) {
            p[j] = new BigDecimal[] {bd(v[j].x()), bd(v[j].y()), bd(v[j].z())};
            maxP = Math.max(maxP, maxAbs(v[j]));
            if (j > 0) {
                maxD = Math.max(maxD, Math.max(upAbsDiff(v[j].x(), v[j - 1].x()),
                    Math.max(upAbsDiff(v[j].y(), v[j - 1].y()), upAbsDiff(v[j].z(), v[j - 1].z()))));
            }
        }
        Curve c = bezier(v);
        BigDecimal bt = bd(t);
        BigDecimal[][] d1 = diff(p);
        within(bernstein(p, bt), c.point(t), bezierPointTol(n, t, maxP), label + " point");
        within(n == 1 ? d1[0] : bernstein(d1, bt), c.derivative(t), bezierDerivativeTol(n, t, maxD), label + " derivative");
        if (n == 1) {
            assertExact(Vec3.ZERO, c.secondDerivative(t), label + " line second derivative");
        } else {
            BigDecimal[][] d2 = diff(d1);
            within(n == 2 ? d2[0] : bernstein(d2, bt), c.secondDerivative(t), bezierSecondTol(n, t, maxP), label + " second");
        }
    }

    @Test
    @DisplayName("R02: lines and Beziers against the exact Bernstein form: 30,000 curves, magnitudes from subnormal to 1e100, t near 0 and 1 and extrapolated to +-1e3, within the R2+A3 bounds")
    void bezierExactBernsteinFuzz() {
        SplittableRandom r = new SplittableRandom(301);
        for (int i = 0; i < 30_000; i++) {
            int n = r.nextInt(1, 4);
            Vec3[] v = new Vec3[n + 1];
            double mag = randomMagnitude(r);
            Vec3 offset = r.nextInt(4) == 0 ? new Vec3(r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1)).mul(randomMagnitude(r)) : Vec3.ZERO;
            for (int j = 0; j <= n; j++) {
                v[j] = r.nextInt(6) == 0 && j > 0 ? v[j - 1]
                    : new Vec3(r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1)).mul(mag).add(offset);
            }
            double t = randomT(r);
            checkBezier(v, t, bezier(v) + " at t=" + t);
        }
    }

    @Test
    @DisplayName("R03: exact endpoints for every input (E1), including t = -0.0, signed-zero and subnormal control points; end derivatives n (P1 - P0) and n (Pn - Pn-1) within bounds")
    void bezierEndpoints() {
        SplittableRandom r = new SplittableRandom(302);
        for (int i = 0; i < 30_000; i++) {
            int n = r.nextInt(1, 4);
            Vec3[] v = new Vec3[n + 1];
            for (int j = 0; j <= n; j++) {
                double m = randomMagnitude(r);
                v[j] = new Vec3(r.nextBoolean() ? -0.0 : r.nextDouble(-1, 1) * m, r.nextDouble(-1, 1) * m, r.nextBoolean() ? 0.0 : r.nextDouble(-1, 1) * m);
            }
            Curve c = bezier(v);
            assertExact(v[0], c.point(0), c + " point(0)");
            assertExact(v[0], c.point(-0.0), c + " point(-0.0)");
            assertExact(v[n], c.point(1), c + " point(1)");
            checkBezier(v, 0, c + " at 0");
            checkBezier(v, 1, c + " at 1");
            checkBezier(v, Math.nextUp(0.0), c + " at MIN_VALUE");
            checkBezier(v, Math.nextDown(1.0), c + " at 1 - 2^-53");
            checkBezier(v, Math.nextUp(1.0), c + " at 1 + 2^-52");
        }
    }

    @Test
    @DisplayName("R04: Bezier metamorphic relations: reversal B(P)(t) = B(reversed P)(1 - t), translation equivariance, degree elevation and C1 continuity near the ends")
    void bezierMetamorphic() {
        SplittableRandom r = new SplittableRandom(303);
        for (int i = 0; i < 20_000; i++) {
            int n = r.nextInt(1, 4);
            double mag = randomMagnitude(r);
            Vec3[] v = new Vec3[n + 1], rev = new Vec3[n + 1];
            for (int j = 0; j <= n; j++) {
                v[j] = new Vec3(r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1)).mul(mag);
            }
            for (int j = 0; j <= n; j++) {
                rev[j] = v[n - j];
            }
            double maxP = 0;
            for (Vec3 p : v) {
                maxP = Math.max(maxP, maxAbs(p));
            }
            double t = r.nextInt(-256, 513) / 256.0; // 1 - t exact
            Curve a = bezier(v), b = bezier(rev);
            CurveTestSupport.assertNear(a.point(t), b.point(1 - t), 2 * bezierPointTol(n, t, maxP), "reversal " + a + " at " + t);
            // translation equivariance: B(P + o)(t) = B(P)(t) + o for t in [0, 1] (partition of unity)
            double tu = r.nextDouble();
            Vec3 o = new Vec3(r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1)).mul(randomMagnitude(r));
            Vec3[] moved = new Vec3[n + 1];
            double maxM = 0;
            for (int j = 0; j <= n; j++) {
                moved[j] = v[j].add(o);
                maxM = Math.max(maxM, maxAbs(moved[j]));
            }
            CurveTestSupport.assertNear(a.point(tu).add(o), bezier(moved).point(tu), bezierPointTol(n, tu, maxP) + 2 * bezierPointTol(n, tu, maxM) + Math.ulp(maxM) * 2,
                "translation " + a + " at " + tu);
            if (n == 2) {
                // degree elevation: the cubic (P0, (P0 + 2 P1) / 3, (2 P1 + P2) / 3, P2) is the same curve
                Vec3 q1 = v[0].add(v[1].mul(2)).mul(1.0 / 3), q2 = v[1].mul(2).add(v[2]).mul(1.0 / 3);
                CubicBezier c3 = new CubicBezier(v[0], q1, q2, v[2]);
                CurveTestSupport.assertNear(a.point(tu), c3.point(tu), 8 * bezierPointTol(3, tu, maxP) + 4 * Math.ulp(maxP), "elevation " + a);
            }
            // C1 near the ends: the step from t = 1 to its neighbours is about B'(1) * ulp
            Vec3 d1 = a.derivative(1);
            double step = Math.nextUp(1.0) - 1;
            CurveTestSupport.assertNear(v[n].add(d1.mul(step)), a.point(Math.nextUp(1.0)), 2 * bezierPointTol(n, Math.nextUp(1.0), maxP) + maxAbs(a.secondDerivative(1)) * step * step,
                "continuity at 1 " + a);
        }
    }

    // ================================================================== spirals

    /** cos and sin of 2 pi f for an exact f in [0, 1): x = 2 pi f - pi in [-pi, pi), 90-digit Taylor series. */
    private static BigDecimal[] cosSin2Pi(BigDecimal f) {
        BigDecimal x = PI.multiply(TWO.multiply(f).subtract(BigDecimal.ONE), MC);
        BigDecimal x2 = x.multiply(x, MC);
        BigDecimal s = BigDecimal.ZERO, c = BigDecimal.ZERO;
        BigDecimal term = x;
        BigDecimal eps = new BigDecimal("1e-85");
        for (int k = 1; term.abs().compareTo(eps) > 0; k += 2) {
            s = s.add(term, MC);
            term = term.multiply(x2, MC).negate().divide(BigDecimal.valueOf((long) (k + 1) * (k + 2)), MC);
        }
        term = BigDecimal.ONE;
        for (int k = 0; term.abs().compareTo(eps) > 0; k += 2) {
            c = c.add(term, MC);
            term = term.multiply(x2, MC).negate().divide(BigDecimal.valueOf((long) (k + 1) * (k + 2)), MC);
        }
        return new BigDecimal[] {c.negate(), s.negate()}; // cos(y + pi) = -cos y, sin(y + pi) = -sin y
    }

    /** The exact fractional part of an exact phase. */
    private static BigDecimal frac(BigDecimal phase) {
        BigDecimal fl = phase.setScale(0, RoundingMode.FLOOR);
        return phase.subtract(fl);
    }

    private static BigDecimal[][] exactSpiral(double r0, double r1, double rise, double turns, double start, double t) {
        BigDecimal bt = bd(t);
        BigDecimal[] cs = cosSin2Pi(frac(bd(start).add(bd(turns).multiply(bt))));
        BigDecimal c = cs[0], s = cs[1];
        BigDecimal r = r0 == r1 ? bd(r0) : BigDecimal.ONE.subtract(bt).multiply(bd(r0)).add(bt.multiply(bd(r1)));
        BigDecimal dr = bd(r1).subtract(bd(r0));
        BigDecimal w = PI.multiply(TWO).multiply(bd(turns));
        BigDecimal[] p = {r.multiply(c), bd(rise).multiply(bt), r.multiply(s).negate()};
        BigDecimal[] d = {dr.multiply(c).subtract(w.multiply(r).multiply(s)), bd(rise), dr.multiply(s).negate().subtract(w.multiply(r).multiply(c))};
        BigDecimal k = TWO.multiply(dr).multiply(w), w2r = w.multiply(w).multiply(r);
        BigDecimal[] dd = {k.multiply(s).negate().subtract(w2r.multiply(c)), BigDecimal.ZERO, k.multiply(c).negate().add(w2r.multiply(s))};
        return new BigDecimal[][] {p, d, dd};
    }

    private static void checkSpiral(double r0, double r1, double rise, double turns, double start, double t, String label) {
        Spiral sp = new Spiral(r0, r1, rise, turns, start);
        BigDecimal[][] e = exactSpiral(r0, r1, rise, turns, start, t);
        String l = label + " " + sp + " at t=" + t;
        within(e[0], sp.point(t), spiralPointTol(r0, r1, rise, turns, t), l + " point");
        within(e[1], sp.derivative(t), spiralDerivativeTol(r0, r1, rise, turns, t), l + " derivative");
        within(e[2], sp.secondDerivative(t), spiralSecondTol(r0, r1, turns, t), l + " second");
    }

    private static double randomRadius(SplittableRandom r) {
        return switch (r.nextInt(6)) {
            case 0 -> 0;
            case 1 -> r.nextLong(0, 1L << 52) * Q;
            case 2 -> r.nextInt(0, 16) * Q;
            default -> r.nextDouble() * Math.pow(10, r.nextInt(-300, 101));
        };
    }

    private static double randomTurns(SplittableRandom r) {
        double sign = r.nextBoolean() ? 1 : -1;
        return sign * switch (r.nextInt(7)) {
            case 0 -> 0;
            case 1 -> r.nextLong(1, 1L << 52) * Q;
            case 2 -> r.nextDouble(0, 10);
            case 3 -> r.nextInt(1, 10_000);
            case 4 -> r.nextDouble() * 0x1p20;
            case 5 -> 1;
            default -> r.nextInt(1, 64) / 64.0;
        };
    }

    private static double randomStart(SplittableRandom r) {
        double sign = r.nextBoolean() ? 1 : -1;
        return switch (r.nextInt(9)) {
            case 0 -> 0;
            case 1 -> -0.0;
            case 2 -> r.nextDouble(-3, 3);
            case 3 -> sign * (Math.scalb(1.0, r.nextInt(1, 53)) + r.nextDouble());
            case 4 -> sign * Math.scalb(1.0, r.nextInt(52, 1024));
            case 5 -> sign * Double.MAX_VALUE;
            case 6 -> sign * r.nextInt(1, 16) * Q;
            case 7 -> sign * Math.nextDown(1.0);
            default -> sign * r.nextInt(0, 64) / 4.0;
        };
    }

    @Test
    @DisplayName("R05: spirals against an exact-phase 90-digit Taylor oracle: 30,000 cases with subnormal to 1e100 radii, subnormal to 2^20 turns, startTurn up to +-MAX_VALUE and -0.0, t extrapolated to +-1e3, within the R2+A3 bounds")
    void spiralTaylorFuzz() {
        SplittableRandom r = new SplittableRandom(304);
        for (int i = 0; i < 30_000; i++) {
            double r0 = randomRadius(r), r1 = r.nextInt(3) == 0 ? r0 : randomRadius(r);
            double rise = switch (r.nextInt(4)) { case 0 -> 0; case 1 -> r.nextDouble(-100, 100); case 2 -> r.nextInt(-8, 9) * Q; default -> r.nextDouble(-1, 1) * 1e100; };
            checkSpiral(r0, r1, rise, randomTurns(r), randomStart(r), randomT(r), "fuzz");
        }
    }

    @Test
    @DisplayName("R06: handedness and +Y axis: positive turns rotate like M1 Quat.axisAngle(+Y, angle) (counter-clockwise from above, +X towards -Z); the normal p x p' is +Y for positive turns and -Y for negative")
    void handedness() {
        SplittableRandom r = new SplittableRandom(305);
        for (int i = 0; i < 20_000; i++) {
            double radius = r.nextDouble(0.01, 100);
            double turns = r.nextInt(1, 6) * (r.nextBoolean() ? 1 : -1);
            double t = r.nextDouble();
            double angle = 2 * Math.PI * turns * t;
            Vec3 viaQuat = Quat.axisAngle(Vec3.Y, angle).rotate(new Vec3(radius, 0, 0));
            Spiral s = new Spiral(radius, radius, 0, turns, 0);
            CurveTestSupport.assertNear(viaQuat, s.point(t), 1e-12 * radius * Math.abs(turns), "rotation about +Y, turns " + turns);
            double ny = s.point(t).cross(s.derivative(t)).y();
            assertTrue(Math.signum(ny) == Math.signum(turns), "normal sign for turns " + turns + ": " + ny);
        }
        assertExact(new Vec3(0, 0, -2), Spiral.circle(2).point(0.25), "+X towards -Z");
        assertExact(new Vec3(0, 0, 2), Spiral.horizontalClockwiseFromAbove(2).point(0.25), "clockwise reaches +Z");
        assertExact(new Vec3(0, 3, 0), new Spiral(0, 0, 3, 7, 0.3).point(1), "zero radius stays on the +Y axis");
    }

    @Test
    @DisplayName("R07: exactness: T3 quarter turns (any startTurn magnitude, products just below 2^51), T4 seams up to the overflow of turns * k, T5 period invariance with subnormal and large fractions")
    void exactness() {
        SplittableRandom r = new SplittableRandom(306);
        int[][] cs = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        for (int i = 0; i < 20_000; i++) {
            // T3
            double start = switch (r.nextInt(4)) {
                case 0 -> r.nextInt(-400, 401) / 4.0;
                case 1 -> (r.nextBoolean() ? 1 : -1) * Math.scalb((double) r.nextLong(1, 1L << 50), r.nextInt(1, 970));
                case 2 -> (r.nextBoolean() ? 1 : -1) * (Math.scalb(1.0, 50) + r.nextInt(0, 1 << 20) / 4.0);
                default -> -0.0;
            };
            double turns, t;
            if (r.nextBoolean()) {
                turns = r.nextInt(-64, 65);
                t = r.nextInt(-4096, 4097) / 256.0;
            } else {
                turns = 1;
                t = (r.nextBoolean() ? 1 : -1) * (Math.scalb(1.0, 51) - r.nextInt(1, 1 << 12) / 4.0);
            }
            BigDecimal phase = bd(start).add(bd(turns).multiply(bd(t)));
            BigDecimal quarters = phase.multiply(BigDecimal.valueOf(4));
            if (quarters.stripTrailingZeros().scale() > 0 || bd(turns).multiply(bd(t)).abs().compareTo(bd(0x1p51)) >= 0) {
                continue;
            }
            int[] q = cs[quarters.toBigIntegerExact().mod(BigInteger.valueOf(4)).intValue()];
            double radius = r.nextInt(0, 1 << 20) / 64.0;
            Spiral s = new Spiral(radius, radius, 0, turns, start);
            assertExact(new Vec3(radius * q[0], 0, -(radius * q[1])), s.point(t), "T3 " + s + " at " + t);
        }
        for (int i = 0; i < 5_000; i++) {
            // T4: closed spirals; k up to the largest value with a finite product
            double turns = r.nextInt(1, 1000) * (r.nextBoolean() ? 1 : -1);
            double start = randomStart(r);
            double radius = r.nextDouble(0, 1e3);
            Spiral s = new Spiral(radius, radius, 0, turns, start);
            double kmax = Math.floor(Double.MAX_VALUE / Math.abs(turns));
            double[] ks = {1, -1, r.nextInt(-1_000_000, 1_000_000), Math.scalb(1.0, r.nextInt(53, 1000)), kmax, -kmax, Math.floor(r.nextDouble() * kmax)};
            for (double k : ks) {
                if (!Double.isFinite(turns * k) || k != Math.rint(k)) {
                    continue;
                }
                assertExact(s.point(0), s.point(k), "T4 point " + s + " k=" + k);
                assertExact(s.derivative(0), s.derivative(k), "T4 derivative " + s + " k=" + k);
            }
        }
        for (int i = 0; i < 20_000; i++) {
            // T5: x >= 0 and y = x - floor(x) (exact) give identical results
            double x = switch (r.nextInt(4)) {
                case 0 -> r.nextDouble() * Math.scalb(1.0, r.nextInt(0, 60));
                case 1 -> r.nextInt(1, 100) + r.nextInt(0, 64) * Q;
                case 2 -> Math.scalb(1.0, r.nextInt(0, 1000)) * r.nextDouble(1, 2);
                default -> r.nextInt(0, 64) * Q;
            };
            double y = x - Math.floor(x);
            Spiral a = new Spiral(r.nextDouble(0, 5), r.nextDouble(0, 5), r.nextDouble(-5, 5), r.nextDouble(-50, 50), x);
            Spiral b = new Spiral(a.startRadius(), a.endRadius(), a.rise(), a.turns(), y);
            double t = randomT(r);
            assertExact(a.point(t), b.point(t), "T5 point " + x + " vs " + y);
            assertExact(a.derivative(t), b.derivative(t), "T5 derivative " + x + " vs " + y);
            assertExact(a.secondDerivative(t), b.secondDerivative(t), "T5 second " + x + " vs " + y);
        }
    }

    @Test
    @DisplayName("R08: constant radius exact under extrapolation; degenerate zero-radius and zero-turn spirals exact; t = -0.0 equals t = 0; startTurn -0.0 equals 0")
    void degenerateAndSignedZero() {
        SplittableRandom r = new SplittableRandom(307);
        for (int i = 0; i < 20_000; i++) {
            double radius = randomRadius(r);
            double t = r.nextInt(-4000, 4001) / 4.0; // turns 1: quarter phases, far extrapolated
            Spiral s = new Spiral(radius, radius, 0, 1, 0);
            int q = Math.floorMod((int) (4 * t), 4);
            Vec3 expected = switch (q) {
                case 0 -> new Vec3(radius, 0, 0);
                case 1 -> new Vec3(0, 0, -radius);
                case 2 -> new Vec3(-radius, 0, 0);
                default -> new Vec3(0, 0, radius);
            };
            assertExact(expected, s.point(t), "constant radius " + radius + " at " + t);
            double rise = r.nextDouble(-10, 10), turns = randomTurns(r), start = randomStart(r), tt = randomT(r);
            Spiral zero = new Spiral(0, 0, rise, turns, start);
            assertExact(new Vec3(0, rise * tt, 0), zero.point(tt), "zero radius point");
            assertExact(new Vec3(0, rise, 0), zero.derivative(tt), "zero radius derivative");
            assertExact(Vec3.ZERO, zero.secondDerivative(tt), "zero radius second derivative");
            double r0 = r.nextDouble(0, 10), r1 = r.nextDouble(0, 10);
            Spiral still = new Spiral(r0, r1, rise, 0, 0);
            assertExact(new Vec3(r1 - r0, rise, 0), still.derivative(tt), "zero turns derivative");
            assertExact(Vec3.ZERO, still.secondDerivative(tt), "zero turns second derivative");
            assertExact(new Vec3((1 - tt) * r0 + tt * r1, rise * tt, 0), still.point(tt), "zero turns point");
            Spiral any = new Spiral(r0, r1, rise, randomTurns(r), randomStart(r));
            assertExact(any.point(0.0), any.point(-0.0), "t = -0.0");
            assertExact(any.derivative(0.0), any.derivative(-0.0), "t = -0.0 derivative");
            Spiral pz = new Spiral(r0, r1, rise, any.turns(), 0.0), nz = new Spiral(r0, r1, rise, any.turns(), -0.0);
            assertExact(pz.point(tt), nz.point(tt), "startTurn -0.0");
            assertExact(pz.secondDerivative(tt), nz.secondDerivative(tt), "startTurn -0.0 second");
        }
    }

    @Test
    @DisplayName("R12: T2 bitwise: a product turns * t whose fraction rounds up to 1.0 (tiny negative) contributes exactly 0, so the phase equals the startTurn fraction bit for bit")
    void reducedOneWrapsToZero() {
        SplittableRandom r = new SplittableRandom(311);
        for (int i = 0; i < 20_000; i++) {
            double start = r.nextBoolean() ? r.nextDouble() : r.nextDouble(-3, 3);
            double tiny = -Math.scalb(r.nextDouble(1, 2), r.nextInt(-1074, -54)); // frac(tiny) = 1 - |tiny| rounds to 1.0
            double radius = r.nextDouble(0.1, 100);
            // turns = -1 at t = -tiny > 0 gives the product tiny; T2 maps frac(tiny) = 1.0 to 0.0, so f = frac(startTurn)
            Spiral moving = new Spiral(radius, radius, 0, -1, start);
            Spiral still = new Spiral(radius, radius, 0, 0, start);
            assertExact(still.point(0.5), moving.point(-tiny), "startTurn " + start + ", turns * t = " + tiny);
        }
    }

    // ================================================================== easing

    private static BigDecimal easeExact(Easing e, BigDecimal t) {
        BigDecimal one = BigDecimal.ONE, half = new BigDecimal("0.5");
        String name = e.name();
        if (name.equals("LINEAR")) return t;
        if (name.equals("SMOOTHSTEP")) return BigDecimal.valueOf(3).multiply(t.pow(2)).subtract(TWO.multiply(t.pow(3)));
        if (name.equals("SMOOTHERSTEP")) return BigDecimal.valueOf(6).multiply(t.pow(5)).subtract(BigDecimal.valueOf(15).multiply(t.pow(4))).add(BigDecimal.TEN.multiply(t.pow(3)));
        int n = switch (name.substring(0, name.indexOf('_'))) { case "QUAD" -> 2; case "CUBIC" -> 3; case "QUART" -> 4; default -> 5; };
        if (name.endsWith("_IN_OUT")) {
            return t.compareTo(half) < 0 ? TWO.pow(n - 1).multiply(t.pow(n)) : one.subtract(TWO.subtract(TWO.multiply(t)).pow(n).divide(TWO));
        }
        if (name.endsWith("_OUT")) return one.subtract(one.subtract(t).pow(n));
        return t.pow(n);
    }

    @Test
    @DisplayName("R09: all 15 easings against exact polynomials (G1: 2^-50), dense adjacent-double windows at 0, 0.5 and 1, exact monotonicity of power easings (G4), 2^-49 near-monotonicity of the smooth ones (G5), symmetry and duality (G3, G6), +0.0 for non-positive input")
    void easingExact() {
        SplittableRandom r = new SplittableRandom(308);
        List<Double> pts = new ArrayList<>();
        for (int i = 0; i < 4_000; i++) {
            pts.add(r.nextDouble());
        }
        double[] centres = {0.5, 1.0, 0x1p-20, 0.25, 0.75};
        for (double c : centres) {
            double x = c;
            for (int i = 0; i < 1_500; i++) {
                x = Math.nextDown(x);
                pts.add(x);
            }
            x = c;
            for (int i = 0; i < 1_500 && x < 1; i++) {
                pts.add(x);
                x = Math.nextUp(x);
            }
        }
        double x = 0;
        for (int i = 0; i < 1_500; i++) {
            x = Math.nextUp(x);
            pts.add(x); // subnormal inputs
        }
        pts.sort(Double::compare);
        BigDecimal g1 = bd(0x1p-50);
        for (Easing e : Easing.values()) {
            double prev = -1;
            boolean power = e.name().contains("_");
            for (double p : pts) {
                if (!(p > 0 && p < 1)) {
                    continue;
                }
                double v = e.apply(p);
                assertTrue(v >= 0 && v <= 1, e + " range at " + p);
                if (bd(v).subtract(easeExact(e, bd(p))).abs().compareTo(g1) > 0) {
                    fail(e + " G1 at " + p + ": " + v);
                }
                if (power || e == Easing.LINEAR) {
                    assertTrue(v >= prev, e + " G4 monotone at " + p);
                } else {
                    assertTrue(v >= prev - 0x1p-49, e + " G5 at " + p);
                }
                prev = Math.max(prev, v);
            }
            assertEquals(0L, Double.doubleToRawLongBits(e.apply(-0.0)), e + " apply(-0.0) is +0.0");
            assertEquals(0L, Double.doubleToRawLongBits(e.apply(0.0)), e + " apply(0.0) is +0.0");
            assertEquals(1.0, e.apply(1.0));
            if (e == Easing.LINEAR || e == Easing.SMOOTHSTEP || e == Easing.SMOOTHERSTEP || e.name().endsWith("_IN_OUT")) {
                assertEquals(0.5, e.apply(0.5), e + " G3");
                for (int i = 0; i < 2_000; i++) {
                    double t = r.nextInt(1, 1 << 20) / (double) (1 << 20); // 1 - t exact
                    assertTrue(Math.abs(e.apply(1 - t) - (1 - e.apply(t))) <= 4 * 0x1p-50, e + " G6 symmetry at " + t);
                }
            }
            if (e.name().endsWith("_OUT") && !e.name().endsWith("_IN_OUT")) {
                Easing in = Easing.valueOf(e.name().replace("_OUT", "_IN"));
                for (int i = 0; i < 2_000; i++) {
                    double t = r.nextInt(1, 1 << 20) / (double) (1 << 20);
                    assertTrue(Math.abs(e.apply(t) - (1 - in.apply(1 - t))) <= 4 * 0x1p-50, e + " G6 duality at " + t);
                }
            }
        }
    }

    // ================================================================== placement

    private static BigDecimal[][] rotationMatrix(Quat q) {
        BigDecimal w = bd(q.w()), x = bd(q.x()), y = bd(q.y()), z = bd(q.z());
        BigDecimal n = w.pow(2).add(x.pow(2)).add(y.pow(2)).add(z.pow(2)); // M1 normalizes; divide anyway
        BigDecimal one = BigDecimal.ONE;
        BigDecimal[][] m = {
            {one.subtract(TWO.multiply(y.pow(2).add(z.pow(2))).divide(n, MC)), TWO.multiply(x.multiply(y).subtract(w.multiply(z))).divide(n, MC), TWO.multiply(x.multiply(z).add(w.multiply(y))).divide(n, MC)},
            {TWO.multiply(x.multiply(y).add(w.multiply(z))).divide(n, MC), one.subtract(TWO.multiply(x.pow(2).add(z.pow(2))).divide(n, MC)), TWO.multiply(y.multiply(z).subtract(w.multiply(x))).divide(n, MC)},
            {TWO.multiply(x.multiply(z).subtract(w.multiply(y))).divide(n, MC), TWO.multiply(y.multiply(z).add(w.multiply(x))).divide(n, MC), one.subtract(TWO.multiply(x.pow(2).add(y.pow(2))).divide(n, MC))}};
        return m;
    }

    private static BigDecimal[] apply(BigDecimal[][] m, Vec3 v, double scale, Vec3 translation) {
        BigDecimal[] in = {bd(v.x()), bd(v.y()), bd(v.z())}, tr = {bd(translation.x()), bd(translation.y()), bd(translation.z())};
        BigDecimal[] out = new BigDecimal[3];
        for (int i = 0; i < 3; i++) {
            out[i] = m[i][0].multiply(in[0]).add(m[i][1].multiply(in[1])).add(m[i][2].multiply(in[2])).multiply(bd(scale)).add(tr[i]);
        }
        return out;
    }

    @Test
    @DisplayName("R10: PosedCurve against an exact BigDecimal rotation (points translated, derivatives scaled directions never translated), bitwise translation invariance of derivatives, exact power-of-two scale linearity, composition with Pose.then")
    void posedCurve() {
        SplittableRandom r = new SplittableRandom(309);
        for (int i = 0; i < 10_000; i++) {
            Curve c = switch (r.nextInt(4)) {
                case 0 -> new Spiral(r.nextDouble(0, 10), r.nextDouble(0, 10), r.nextDouble(-5, 5), r.nextDouble(-4, 4), r.nextDouble());
                case 1 -> new CubicBezier(CurveTestSupport.randomVec(r, 10), CurveTestSupport.randomVec(r, 10), CurveTestSupport.randomVec(r, 10), CurveTestSupport.randomVec(r, 10));
                case 2 -> new QuadraticBezier(CurveTestSupport.randomVec(r, 10), CurveTestSupport.randomVec(r, 10), CurveTestSupport.randomVec(r, 10));
                default -> new LineSegment(CurveTestSupport.randomVec(r, 10), CurveTestSupport.randomVec(r, 10));
            };
            Quat rot = new Quat(r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1));
            double scale = new double[] {1, 2, 0.5, 3.7, 1e-3, 64}[r.nextInt(6)];
            int trKind = r.nextInt(3);
            Vec3 tr = trKind == 0 ? Vec3.ZERO : CurveTestSupport.randomVec(r, trKind == 1 ? 10 : 3e7);
            Pose pose = new Pose(tr, rot, scale);
            Curve posed = c.transformed(pose);
            double t = r.nextDouble(-0.5, 1.5);
            BigDecimal[][] m = rotationMatrix(pose.rotation());
            Vec3 p = c.point(t), d = c.derivative(t), dd = c.secondDerivative(t);
            double eps = 0x1p-53;
            within(apply(m, p, scale, tr), posed.point(t), 64 * eps * (maxAbs(tr) + scale * 3 * maxAbs(p)) + Double.MIN_NORMAL, "posed point " + c);
            within(apply(m, d, scale, Vec3.ZERO), posed.derivative(t), 64 * eps * scale * 3 * maxAbs(d) + Double.MIN_NORMAL, "posed derivative " + c);
            within(apply(m, dd, scale, Vec3.ZERO), posed.secondDerivative(t), 64 * eps * scale * 3 * maxAbs(dd) + Double.MIN_NORMAL, "posed second " + c);
            // translation never reaches derivatives: bitwise identical for a different translation
            Curve moved = c.transformed(new Pose(CurveTestSupport.randomVec(r, 1e6), rot, scale));
            assertExact(posed.derivative(t), moved.derivative(t), "derivative translation invariance");
            assertExact(posed.secondDerivative(t), moved.secondDerivative(t), "second derivative translation invariance");
            // power-of-two scale: exactly linear
            Curve doubled = c.transformed(new Pose(tr, rot, 2 * scale));
            assertExact(posed.derivative(t).mul(2), doubled.derivative(t), "derivative scale linearity");
            assertExact(posed.secondDerivative(t).mul(2), doubled.secondDerivative(t), "second derivative scale linearity");
            // composition: c.transformed(a).transformed(b) = c.transformed(b.then(a))
            Pose b = new Pose(CurveTestSupport.randomVec(r, 100), new Quat(r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1), 0.3), new double[] {1, 2, 0.25}[r.nextInt(3)]);
            Curve twice = posed.transformed(b), once = c.transformed(b.then(pose));
            double mag = maxAbs(b.translation()) + b.scale() * (maxAbs(tr) + scale * 2 * maxAbs(p));
            CurveTestSupport.assertNear(twice.point(t), once.point(t), 1e-13 * mag + 1e-300, "composition point");
            CurveTestSupport.assertNear(twice.derivative(t), once.derivative(t), 1e-13 * b.scale() * scale * (1 + maxAbs(d)), "composition derivative");
        }
    }

    // ================================================================== determinism

    @Test
    @DisplayName("R11: determinism: bitwise identical results from 8 concurrent threads and from repeated sequential evaluation (no shared mutable state)")
    void determinismAcrossThreads() throws Exception {
        SplittableRandom r = new SplittableRandom(310);
        int n = 4_000;
        Curve[] curves = new Curve[n];
        double[] ts = new double[n];
        for (int i = 0; i < n; i++) {
            curves[i] = switch (i % 4) {
                case 0 -> new Spiral(randomRadius(r), randomRadius(r), r.nextDouble(-5, 5), randomTurns(r), randomStart(r));
                case 1 -> new CubicBezier(CurveTestSupport.randomVec(r, 100), CurveTestSupport.randomVec(r, 100), CurveTestSupport.randomVec(r, 100), CurveTestSupport.randomVec(r, 100));
                case 2 -> new QuadraticBezier(CurveTestSupport.randomVec(r, 1), CurveTestSupport.randomVec(r, 1), CurveTestSupport.randomVec(r, 1))
                    .transformed(new Pose(CurveTestSupport.randomVec(r, 10), new Quat(1, 2, 3, 4), 1.5));
                default -> new LineSegment(CurveTestSupport.randomVec(r, 1e3), CurveTestSupport.randomVec(r, 1e3));
            };
            ts[i] = randomT(r);
        }
        Supplier<long[]> eval = () -> {
            long[] bits = new long[n * 9 + Easing.values().length * n];
            int k = 0;
            for (int i = 0; i < n; i++) {
                for (Vec3 v : new Vec3[] {curves[i].point(ts[i]), curves[i].derivative(ts[i]), curves[i].secondDerivative(ts[i])}) {
                    bits[k++] = Double.doubleToRawLongBits(v.x());
                    bits[k++] = Double.doubleToRawLongBits(v.y());
                    bits[k++] = Double.doubleToRawLongBits(v.z());
                }
            }
            for (Easing e : Easing.values()) {
                for (int i = 0; i < n; i++) {
                    bits[k++] = Double.doubleToRawLongBits(e.apply(ts[i]));
                }
            }
            return bits;
        };
        long[] reference = eval.get();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<long[]>> futures = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                futures.add(pool.submit(eval::get));
            }
            for (Future<long[]> f : futures) {
                assertTrue(java.util.Arrays.equals(reference, f.get()), "concurrent evaluation differs");
            }
        } finally {
            pool.shutdownNow();
        }
        for (int i = 0; i < 5; i++) {
            assertTrue(java.util.Arrays.equals(reference, eval.get()), "repeated evaluation differs (JIT)");
        }
    }
}
