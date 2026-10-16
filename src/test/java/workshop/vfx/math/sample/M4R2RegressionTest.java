package workshop.vfx.math.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.curve.Curve;
import workshop.vfx.math.curve.LineSegment;
import workshop.vfx.math.curve.Spiral;

/**
 * Regression tests for the three blocking findings of ChatGPT's independent review of M4 R1 (contract revision 2):
 * F1 subnormal constant-speed lengths and the tolerance floor; F2 finite handling of pathological panels in the
 * normalised partial integral; F3 deterministic Newton-bisection progress and the result after a non-converged exit.
 * Oracles: exact BigDecimal lengths, a closed-form inverse, and properties stated by the contract.
 */
class M4R2RegressionTest {
    private static final double Q = Double.MIN_VALUE;
    private static final double EPS = 0x1p-53;
    private static final double FLOOR = 1024 * Double.MIN_VALUE;
    private static final double X1 = 0x1.13b23fd99b705p-1, X2 = 0x1.cff6ce0533a69p-1; // contract 5.2

    // ------------------------------------------------------------------ F1

    @Test
    @DisplayName("F1: nonzero subnormal-speed lines and constant-speed spirals converge with 16 panels; tau = max(abs, rel L0, 1024 MIN_VALUE); |L - L*| <= rounding allowance with the absolute underflow term 8 K MIN_VALUE")
    void subnormalConstantSpeed() {
        double[][] ends = {{7, 0, 0}, {3, 5, 11}, {1, 0, 0}, {123456, 0, 0}, {Math.scalb(1.0, 40), 3, 0}, {1e-320 / Q, 2e-320 / Q, 0}};
        for (double[] e : ends) {
            Vec3 end = new Vec3(e[0] * Q, e[1] * Q, e[2] * Q);
            for (double[] range : new double[][] {{0, 1}, {-0.5, 1.5}}) {
                ArcLengthTable t = ArcLengthTable.build(new LineSegment(Vec3.ZERO, end), range[0], range[1], new ArcLengthTolerance(0, 0x1p-40), new SampleLimits(16, 4096));
                BigDecimal x = new BigDecimal(end.x()), y = new BigDecimal(end.y()), z = new BigDecimal(end.z());
                double exact = x.multiply(x).add(y.multiply(y)).add(z.multiply(z)).sqrt(MathContext.DECIMAL128)
                    .multiply(new BigDecimal(range[1] - range[0])).doubleValue();
                String label = "line to " + end + " on " + range[0] + ".." + range[1];
                assertTrue(t.converged(), label + " converged");
                assertEquals(16, t.panelCount(), label + " panels");
                assertEquals(240, t.evaluations(), label + " evaluations");
                assertEquals(FLOOR, t.effectiveTolerance(), label + " tau is the floor");
                assertTrue(t.errorEstimate() <= FLOOR);
                double allowance = (range[1] - range[0]) * (5 * EPS * exact + Q) + (16 + 64) * EPS * exact + 8 * 16 * Q;
                assertEquals(exact, t.length(), allowance, label + " length");
            }
        }
        for (double r : new double[] {3 * Q, 1e-320, 1e-315, 1e-310}) {
            for (Spiral s : new Spiral[] {Spiral.circle(r), new Spiral(r, r, r, 3, 0.1)}) {
                ArcLengthTable t = ArcLengthTable.build(s, -0.5, 1.5, new ArcLengthTolerance(0, 0x1p-40), new SampleLimits(16, 4096));
                assertTrue(t.converged(), s + " converged");
                assertEquals(16, t.panelCount(), s + " panels");
                assertEquals(FLOOR, t.effectiveTolerance());
                double speed = Math.hypot(2 * Math.PI * r * s.turns(), s.rise());
                double allowance = 2 * SampleTestSupport.speedErrorBound("SPIRAL", new double[] {r, r, s.rise(), s.turns(), s.startTurn()}, -0.5, 1.5)
                    + 80 * EPS * 2 * speed + 8 * 16 * Q;
                assertEquals(2 * speed, t.length(), allowance, s + " length");
            }
        }
        // an absolute tolerance below the floor is raised to the floor; above it, it is kept
        LineSegment tiny = new LineSegment(Vec3.ZERO, new Vec3(7 * Q, 0, 0));
        assertEquals(FLOOR, ArcLengthTable.build(tiny, new ArcLengthTolerance(Q, 0x1p-40), SampleTestSupport.LIMITS).effectiveTolerance());
        assertEquals(1e-300, ArcLengthTable.build(tiny, new ArcLengthTolerance(1e-300, 0x1p-40), SampleTestSupport.LIMITS).effectiveTolerance());
        // sampling a subnormal-length table stays finite, monotone and inside the range
        ArcLengthTable t = ArcLengthTable.build(new LineSegment(Vec3.ZERO, new Vec3(123456 * Q, 0, 0)), new ArcLengthTolerance(0, 0x1p-40), SampleTestSupport.LIMITS);
        SampleSet set = Sampler.uniformDistance(t, 33, Endpoints.INCLUDE_END, SampleTestSupport.LIMITS);
        for (int k = 1; k < 33; k++) {
            assertTrue(set.parameter(k) >= set.parameter(k - 1) && set.parameter(k) <= 1);
        }
    }

    // ------------------------------------------------------------------ F2

    /** The five nodes of the 5-point rule on [lo, hi] (contract 5.2). */
    private static void nodes(Set<Double> out, double lo, double hi) {
        double h = 0.5 * (hi - lo), c = lo + h;
        out.add(c);
        out.add(c - h * X1);
        out.add(c + h * X1);
        out.add(c - h * X2);
        out.add(c + h * X2);
    }

    /** A user curve along +X whose speed is `half` at the half-panel nodes of panel [0, 1/16], `whole` at its whole-panel nodes, 0 elsewhere. */
    private static Curve pathological(double whole, double half) {
        Set<Double> w = new HashSet<>(), h = new HashSet<>();
        nodes(w, 0, 1.0 / 16);
        nodes(h, 0, 1.0 / 32);
        nodes(h, 1.0 / 32, 1.0 / 16);
        h.removeAll(w);
        return new Curve() {
            @Override public Vec3 point(double t) { return new Vec3(t, 0, 0); }
            @Override public Vec3 derivative(double t) { return new Vec3(h.contains(t) ? half : w.contains(t) ? whole : 0, 0, 0); }
            @Override public Vec3 secondDerivative(double t) { return Vec3.ZERO; }
        };
    }

    @Test
    @DisplayName("F2: pathological panels (I2/I1 overflowing, I1 = 0, I2 = 1000 I1, I1 = 1000 I2, I2/I1 underflowing) on finite tables: lengthAt and parameterAt are finite, monotone and follow the linear fallback of contract 5.5 exactly")
    void pathologicalPanels() {
        double[][] cases = {{1e-300, 1e300}, {0, 1e300}, {1, 1000}, {1e-300, 1e-200}, {1000, 1}, {1e300, 1e-300}};
        for (double[] c : cases) {
            Curve curve = pathological(c[0], c[1]);
            ArcLengthTable t = ArcLengthTable.build(curve, new ArcLengthTolerance(0, 1e-6), new SampleLimits(64, 256)); // 256: no refinement possible
            String label = "whole " + c[0] + ", half " + c[1];
            double len = t.length();
            assertTrue(Double.isFinite(len) && len > 0, label + " finite length " + len);
            assertFalse(t.converged(), label);
            double previous = 0;
            for (int i = 1; i < 64; i++) {
                double u = i / 1024.0; // inside panel [0, 1/16]
                double l = t.lengthAt(u);
                assertTrue(Double.isFinite(l) && l >= previous && l <= len, label + " lengthAt(" + u + ") = " + l);
                // pathological panel: linear fallback F(t) = I2 (t - a) / (b - a), and here I2 = length (all other panels are 0)
                assertEquals(len * (u * 16), l, 4 * Math.ulp(len), label + " linear fallback at " + u);
                previous = l;
            }
            for (int k = 1; k < 16; k++) {
                double s = len * (k / 16.0);
                double p = t.parameterAt(s);
                assertTrue(Double.isFinite(p) && p >= 0 && p <= 1, label + " parameterAt");
                assertEquals(k / 256.0, p, 4 * Math.ulp(1.0 / 16), label + " inverse of the linear fallback at " + k + "/16");
            }
            SampleSet set = Sampler.uniformDistance(t, 17, Endpoints.INCLUDE_END, SampleTestSupport.LIMITS);
            for (int k = 1; k < 17; k++) {
                assertTrue(Double.isFinite(set.parameter(k)) && set.parameter(k) >= set.parameter(k - 1));
            }
        }
    }

    @Test
    @DisplayName("F2: ratio mode is clamped to the panel value: a speed seen only by the partial integral's nodes cannot make lengthAt exceed the length at the panel end (F_k in [0, I2_k], contract 5.5)")
    void partialClampedToPanel() {
        Set<Double> spike = new HashSet<>();
        nodes(spike, 0, 3.0 / 64); // the nodes of G(0, 3/64), none of which is a table node
        Set<Double> table = new HashSet<>();
        nodes(table, 0, 1.0 / 16);
        nodes(table, 0, 1.0 / 32);
        nodes(table, 1.0 / 32, 1.0 / 16);
        spike.removeAll(table);
        assertEquals(5, spike.size());
        Curve curve = new Curve() {
            @Override public Vec3 point(double t) { return new Vec3(t, 0, 0); }
            @Override public Vec3 derivative(double t) { return new Vec3(spike.contains(t) ? 1e6 : 1, 0, 0); }
            @Override public Vec3 secondDerivative(double t) { return Vec3.ZERO; }
        };
        ArcLengthTable t = ArcLengthTable.build(curve, new ArcLengthTolerance(0, 0x1p-30), new SampleLimits(64, 256));
        assertEquals(16, t.panelCount());
        double end = t.lengthAt(1.0 / 16);
        assertEquals(end, t.lengthAt(3.0 / 64), "F_0(3/64) is clamped to I2_0");
        assertTrue(t.lengthAt(3.0 / 64) < t.length());
        for (int i = 1; i < 64; i++) {
            double u = i / 1024.0;
            assertTrue(t.lengthAt(u) >= 0 && t.lengthAt(u) <= end, "lengthAt(" + u + ") within [0, S_1]");
        }
    }

    @Test
    @DisplayName("F3: ties go to the upper end: (a) a target exactly halfway across a panel shorter than 2 tau 2^-10 returns b_k; (b) after a collapse exit between adjacent doubles with exactly equal residuals, the upper double is returned")
    void tieGoesToUpperEnd() {
        Curve slowStart = new Curve() {
            @Override public Vec3 point(double t) { return new Vec3(t, 0, 0); } // positions are not used by the table
            @Override public Vec3 derivative(double t) { return new Vec3(t < 1.0 / 16 ? 0x1p-40 : 1, 0, 0); }
            @Override public Vec3 secondDerivative(double t) { return Vec3.ZERO; }
        };
        ArcLengthTable t = ArcLengthTable.build(slowStart, new ArcLengthTolerance(0, 0x1p-30), new SampleLimits(64, 256));
        assertEquals(16, t.panelCount());
        double i2 = t.lengthAt(1.0 / 16); // S_1 = 0 + I2_0
        assertTrue(0.5 * i2 <= t.effectiveTolerance() * 0x1p-10, "both ends within the tolerance");
        assertEquals(1.0 / 16, t.parameterAt(0.5 * i2), "tie: the upper end");
        assertEquals(1.0 / 16, t.parameterAt(Math.nextUp(0.5 * i2)), "upper end has the smaller residual");
        assertEquals(0.0, t.parameterAt(Math.nextDown(0.5 * i2)), "lower end has the smaller residual");

        // (b) a line on [1e6, 1e6 + 1]: adjacent parameters differ by 2^-33, so F_0 steps by about 2^-33 > tolInv and the
        // bracket collapses. A target exactly halfway between F_0(lo) and F_0(hi) (lengthAt = F_0 in panel 0) is a tie.
        ArcLengthTable line = ArcLengthTable.build(new LineSegment(Vec3.ZERO, Vec3.X), 1e6, 1e6 + 1, new ArcLengthTolerance(0, 0x1p-30), new SampleLimits(64, 256));
        int ties = 0;
        for (int i = 20; i < 60 && ties < 5; i++) {
            double lo = 1e6 + i / 1000.0, hi = Math.nextUp(lo);
            double flo = line.lengthAt(lo), fhi = line.lengthAt(hi), mid = 0.5 * (flo + fhi);
            if (!(flo < mid && mid < fhi && mid - flo == fhi - mid)) {
                continue;
            }
            assertTrue(mid - flo > line.effectiveTolerance() * 0x1p-10, "the tolerance is unreachable");
            assertEquals(hi, line.parameterAt(mid), "tie at " + lo + ": the upper end");
            assertEquals(lo, line.parameterAt(Math.nextDown(mid)), "the lower end is better just below the tie");
            ties++;
        }
        assertEquals(5, ties, "exact ties found");
    }

    // ------------------------------------------------------------------ F3

    private static int derivativeCalls;

    /** x = t + t^2/2 (speed 1 + t, exact arc length x(t)), with a speed spike of 1e6 on [lo, hi]. */
    private static Curve creeping(double lo, double hi) {
        return new Curve() {
            @Override public Vec3 point(double t) { return new Vec3(t + 0.5 * t * t, 0, 0); }
            @Override public Vec3 derivative(double t) { derivativeCalls++; return new Vec3(t >= lo && t <= hi ? 1e6 : 1 + t, 0, 0); }
            @Override public Vec3 secondDerivative(double t) { return Vec3.ZERO; }
        };
    }

    @Test
    @DisplayName("F3: Newton creep (a speed spike at the root, invisible to the quadrature, makes Newton steps tiny): at most 336 derivative evaluations; |lengthAt(t) - s| <= max(tau 2^-10, 4 (b - a) 2^-47); t within the bound of the exact inverse -1 + sqrt(1 + 2s)")
    void newtonCreep() {
        ArcLengthTolerance tol = new ArcLengthTolerance(0, 0x1p-40);
        SampleLimits lim = new SampleLimits(64, 256);
        ArcLengthTable plain = ArcLengthTable.build(creeping(2, 2), tol, lim);
        for (double f : new double[] {0.11, 0.23, 0.37, 0.41, 0.67, 0.71, 0.89}) {
            double s = f * plain.length();
            double exact = -1 + Math.sqrt(1 + 2 * s); // x(t) = s
            ArcLengthTable spiked = ArcLengthTable.build(creeping(exact - 1e-7, exact + 1e-7), tol, lim);
            assertEquals(plain.length(), spiked.length(), "the spike is invisible to the table (f = " + f + ")");
            derivativeCalls = 0;
            double t = spiked.parameterAt(s);
            assertTrue(derivativeCalls <= 336, "evaluations " + derivativeCalls);
            double bound = Math.max(spiked.effectiveTolerance() * 0x1p-10, 4 * (1.0 / 16) * 0x1p-47) + 4 * Math.ulp(s);
            assertTrue(Math.abs(spiked.lengthAt(t) - s) <= bound, "f = " + f + ": |lengthAt(t) - s| = " + Math.abs(spiked.lengthAt(t) - s) + " > " + bound);
            assertEquals(exact, t, 2 * spiked.effectiveTolerance() + bound + 1e-15, "f = " + f + ": exact inverse");
        }
    }

    @Test
    @DisplayName("F3: a speed discontinuous at a 2^-41 scale (F has jumps, the tolerance is unreachable; in this scenario every exit is by tolerance or by bracket collapse): every result is within the tolerance or is the better end of an adjacent-double bracket; at most 336 evaluations; deterministic")
    void nonConvergedExit() {
        Curve noisy = new Curve() {
            @Override public Vec3 point(double t) { return new Vec3(t, 0, 0); }
            @Override public Vec3 derivative(double t) {
                derivativeCalls++;
                return new Vec3(((Double.doubleToRawLongBits(t) >>> 11) & 1) == 0 ? 0.25 : 1.75, 0, 0);
            }
            @Override public Vec3 secondDerivative(double t) { return Vec3.ZERO; }
        };
        ArcLengthTable t = ArcLengthTable.build(noisy, new ArcLengthTolerance(0, 0x1p-40), new SampleLimits(64, 256));
        double len = t.length(), slack = 4 * Math.ulp(len);
        int nonConverged = 0;
        for (int k = 1; k < 200; k++) {
            double s = len * (k / 200.0);
            derivativeCalls = 0;
            double p = t.parameterAt(s);
            assertTrue(derivativeCalls <= 336, "evaluations " + derivativeCalls);
            assertTrue(p >= 0 && p <= 1);
            assertEquals(p, t.parameterAt(s), "deterministic");
            double dl = t.lengthAt(p) - s;
            if (Math.abs(dl) <= t.effectiveTolerance() * 0x1p-10 + slack) {
                continue;
            }
            nonConverged++;
            double dn = t.lengthAt(Math.nextDown(p)) - s, du = t.lengthAt(Math.nextUp(p)) - s;
            boolean upperEnd = dl >= -slack && dn < slack && Math.abs(dl) <= Math.abs(dn) + slack;
            boolean lowerEnd = dl < slack && du >= -slack && Math.abs(dl) <= Math.abs(du) + slack;
            if (!(upperEnd || lowerEnd)) {
                fail("s = " + s + ": t = " + p + " is not the better end of an adjacent-double bracket (" + dn + ", " + dl + ", " + du + ")");
            }
        }
        assertTrue(nonConverged > 100, "the scenario exercises non-converged exits: " + nonConverged);
    }

    @Test
    @DisplayName("F3: a target equal to the length at a panel end (an end of the bracket is already within the tolerance) returns that end exactly, with no derivative evaluation")
    void panelEndTargets() {
        Curve[] curves = {Spiral.circle(2.5), Spiral.conicalSpiral(0.5, 5, 1, 9), creeping(2, 2)};
        for (Curve inner : curves) {
            Curve counted = new Curve() {
                @Override public Vec3 point(double t) { return inner.point(t); }
                @Override public Vec3 derivative(double t) { derivativeCalls++; return inner.derivative(t); }
                @Override public Vec3 secondDerivative(double t) { return inner.secondDerivative(t); }
            };
            ArcLengthTable t = ArcLengthTable.build(counted, new ArcLengthTolerance(0, 0x1p-3), new SampleLimits(16, 256));
            assertEquals(16, t.panelCount(), inner + ": the 16 initial panels (contract 5.3)");
            for (int k = 1; k < 16; k++) {
                double end = k / 16.0, s = t.lengthAt(end); // S_k: F_k(a_k) = 0
                derivativeCalls = 0;
                assertEquals(end, t.parameterAt(s), inner + ": panel end " + k);
                assertEquals(0, derivativeCalls, inner + ": evaluations at panel end " + k);
            }
        }
    }

    @Test
    @DisplayName("Stationary intervals (R2 mutation survivors M26, M15): parameterAt(length()) == t1 even when the last panels have zero length; a distance reached at the start of a stationary interval maps to its start (the smallest panel k with S_(k+1) >= distance)")
    void stationaryTail() {
        Curve stopsHalfway = new Curve() {
            @Override public Vec3 point(double t) { return new Vec3(Math.min(t, 0.5), 0, 0); }
            @Override public Vec3 derivative(double t) { return new Vec3(t < 0.5 ? 1 : 0, 0, 0); }
            @Override public Vec3 secondDerivative(double t) { return Vec3.ZERO; }
        };
        for (double[] range : new double[][] {{0, 1}, {-0.5, 1.5}}) {
            ArcLengthTable t = ArcLengthTable.build(stopsHalfway, range[0], range[1], new ArcLengthTolerance(0, 0x1p-30), SampleTestSupport.LIMITS);
            assertEquals(range[1], t.parameterAt(t.length()), "parameterAt(length()) is t1 on " + range[0] + ".." + range[1]);
            assertEquals(range[0], t.parameterAt(0), "parameterAt(0) is t0");
            assertTrue(t.parameterAt(Math.nextDown(t.length())) <= 0.5 + 1e-9, "the tail adds no length");
        }
        Curve pausesInTheMiddle = new Curve() { // zero speed on [1/4, 1/2]: panels 4 to 7 have zero length
            @Override public Vec3 point(double t) { return new Vec3(t < 0.25 ? t : t < 0.5 ? 0.25 : t - 0.25, 0, 0); }
            @Override public Vec3 derivative(double t) { return new Vec3(t >= 0.25 && t < 0.5 ? 0 : 1, 0, 0); }
            @Override public Vec3 secondDerivative(double t) { return Vec3.ZERO; }
        };
        ArcLengthTable t = ArcLengthTable.build(pausesInTheMiddle, new ArcLengthTolerance(0, 0x1p-30), SampleTestSupport.LIMITS);
        assertEquals(16, t.panelCount());
        assertEquals(t.lengthAt(0.25), t.lengthAt(0.5), "the pause adds no length");
        assertEquals(0.25, t.parameterAt(t.lengthAt(0.25)), "the start of the pause");
        assertEquals(0.5, t.parameterAt(Math.nextUp(t.lengthAt(0.5))), 1e-9, "just after the pause");
    }

    @Test
    @DisplayName("Non-finite derivative after build (contract 9, R2 mutation survivor M12): a curve finite at every table node but NaN elsewhere makes lengthAt and parameterAt throw ArithmeticException, never return NaN")
    void nonFiniteAfterBuild() {
        Set<Double> tableNodes = new HashSet<>();
        for (int k = 0; k < 16; k++) {
            double a = k / 16.0, b = (k + 1) / 16.0, m = a + 0.5 * (b - a);
            nodes(tableNodes, a, b);
            nodes(tableNodes, a, m);
            nodes(tableNodes, m, b);
        }
        Curve finiteAtNodes = new Curve() {
            @Override public Vec3 point(double t) { return new Vec3(t, 0, 0); }
            @Override public Vec3 derivative(double t) { return new Vec3(tableNodes.contains(t) ? 1 : Double.NaN, 0, 0); }
            @Override public Vec3 secondDerivative(double t) { return Vec3.ZERO; }
        };
        ArcLengthTable t = ArcLengthTable.build(finiteAtNodes, new ArcLengthTolerance(0, 0x1p-30), new SampleLimits(64, 256));
        assertTrue(t.converged() && Double.isFinite(t.length()));
        org.junit.jupiter.api.Assertions.assertThrows(ArithmeticException.class, () -> t.lengthAt(0.3));
        org.junit.jupiter.api.Assertions.assertThrows(ArithmeticException.class, () -> t.parameterAt(0.3 * t.length()));
    }

    @Test
    @DisplayName("F3: iteration cap: a target inside a jump of F (unreachable tolerance) near t = 2^-40, where the bracket cannot collapse within 64 iterations, and zero speed at the first iterate: all 64 iterations run (332 to 336 evaluations); the result is the bracket end with the smaller residual, on the correct side of the jump and within 2^-47 (b - a) + 2 ulp of it")
    void iterationCapExit() {
        double c = 0x1p-40;
        double j0 = c / (0.5 * (1 + X2)); // approximate parameter where the top Gauss node of F_0 crosses c
        Curve jump = new Curve() {
            private double speed(double t) { return t >= 0.3 * j0 && t <= 0.4 * j0 ? 0 : t < c ? 1 : 3; }
            @Override public Vec3 point(double t) { return new Vec3(t, 0, 0); } // positions are not used by the table
            @Override public Vec3 derivative(double t) { derivativeCalls++; return new Vec3(speed(t), 0, 0); }
            @Override public Vec3 secondDerivative(double t) { return Vec3.ZERO; }
        };
        ArcLengthTable t = ArcLengthTable.build(jump, new ArcLengthTolerance(0, 0x1p-40), new SampleLimits(64, 256));
        assertEquals(16, t.panelCount());
        // The jump of lengthAt: the smallest double J in [c, 2c] with lengthAt(J) > 1.1 J (found by bisection on the bit patterns).
        long lo = Double.doubleToRawLongBits(c), hi = Double.doubleToRawLongBits(2 * c);
        while (hi - lo > 1) {
            long mid = (lo + hi) >>> 1;
            double m = Double.longBitsToDouble(mid);
            if (t.lengthAt(m) > 1.1 * m) hi = mid; else lo = mid;
        }
        double jAt = Double.longBitsToDouble(hi), below = t.lengthAt(Math.nextDown(jAt)), above = t.lengthAt(jAt);
        assertTrue(above - below > 0.2 * jAt, "a jump of F: " + below + " -> " + above);
        double w = 0x1p-47 * (1.0 / 16) * (1 + 0x1p-40) + 2 * Math.ulp(1.0 / 16); // contract 5.6: final bracket width
        for (double frac : new double[] {1.0 / 3, 2.0 / 3}) {
            double s = below + frac * (above - below);
            derivativeCalls = 0;
            double p = t.parameterAt(s);
            assertTrue(derivativeCalls >= 332 && derivativeCalls <= 336, "the cap is reached: " + derivativeCalls + " evaluations");
            assertEquals(p, t.parameterAt(s), "deterministic");
            double res = t.lengthAt(p) - s;
            assertTrue(Math.abs(res) > t.effectiveTolerance() * 0x1p-10, "the tolerance is unreachable");
            if (frac < 0.5) {
                assertTrue(p < jAt && res < 0, "lower end (smaller residual): p = " + p + ", J = " + jAt);
            } else {
                assertTrue(p >= jAt && res > 0, "upper end (smaller residual): p = " + p + ", J = " + jAt);
            }
            assertTrue(Math.abs(p - jAt) <= w, "within the final bracket width: " + Math.abs(p - jAt));
            assertTrue(Math.abs(p - jAt) <= 0x1p-20 * jAt, "measured: the bracket closed on the jump: " + Math.abs(p - jAt) / jAt);
        }
    }
}
