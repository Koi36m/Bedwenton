import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.curve.*;
import workshop.vfx.math.sample.*;

/** M4 R2 probe (review scratch): reproduces the three R1 review findings on a given reference implementation. */
public class Findings {
    static final double Q = Double.MIN_VALUE;
    public static void main(String[] a) {
        System.out.println("== F1: nonzero subnormal-speed lines, tolerance (0, 2^-40), budget 4096");
        for (double[] d : new double[][] {{7, 0, 0}, {3, 5, 11}, {1, 0, 0}, {123456, 0, 0}, {1e-310 / Q, 0, 0}}) {
            Vec3 e = new Vec3(d[0] * Q, d[1] * Q, d[2] * Q);
            try {
                ArcLengthTable t = ArcLengthTable.build(new LineSegment(Vec3.ZERO, e), new ArcLengthTolerance(0, 0x1p-40), new SampleLimits(16, 4096));
                System.out.printf("  end=%s: length=%s (%.1f q) tau=%s converged=%s panels=%d evals=%d estimate=%s%n", e, t.length(), t.length() / Q, t.effectiveTolerance(), t.converged(), t.panelCount(), t.evaluations(), t.errorEstimate());
            } catch (RuntimeException x) { System.out.println("  end=" + e + ": " + x); }
        }
        System.out.println("== F2: pathological panel, I1 tiny and I2 huge (ratio overflows); speed 0 elsewhere; budget 256 (no refinement)");
        // panel 0 is [0, 1/16]; whole-panel nodes c +- h X with h = 1/32; half-panel nodes on [0, 1/32] and [1/32, 1/16]
        double x1 = 0x1.13b23fd99b705p-1, x2 = 0x1.cff6ce0533a69p-1;
        java.util.Set<Double> whole = new java.util.HashSet<>(), halves = new java.util.HashSet<>();
        addNodes(whole, 0, 1.0 / 16, x1, x2);
        addNodes(halves, 0, 1.0 / 32, x1, x2);
        addNodes(halves, 1.0 / 32, 1.0 / 16, x1, x2);
        halves.removeAll(whole);
        Curve spike = new Curve() {
            public Vec3 point(double t) { return new Vec3(t, 0, 0); }
            public Vec3 derivative(double t) { return new Vec3(halves.contains(t) ? 1e300 : whole.contains(t) ? 1e-300 : 0, 0, 0); }
            public Vec3 secondDerivative(double t) { return Vec3.ZERO; }
        };
        ArcLengthTable t = ArcLengthTable.build(spike, new ArcLengthTolerance(0, 1e-6), new SampleLimits(64, 256));
        System.out.printf("  length=%s converged=%s panels=%d%n", t.length(), t.converged(), t.panelCount());
        for (double u : new double[] {0.001, 0.01, 0.03, 0.05}) System.out.println("  lengthAt(" + u + ") = " + t.lengthAt(u));
        for (double f : new double[] {0.25, 0.5, 0.75}) System.out.println("  parameterAt(" + f + " L) = " + t.parameterAt(f * t.length()));
        System.out.println("== F3: inversion on a speed discontinuous at a 2^-40 scale (F has jumps); 16 panels, tolerance (0, 2^-40)");
        Curve noisy = new Curve() {
            public Vec3 point(double t) { return new Vec3(t, 0, 0); }
            public Vec3 derivative(double t) { long b = Double.doubleToRawLongBits(t); return new Vec3(((b >>> 11) & 1) == 0 ? 0.25 : 1.75, 0, 0); }
            public Vec3 secondDerivative(double t) { return Vec3.ZERO; }
        };
        ArcLengthTable n = ArcLengthTable.build(noisy, new ArcLengthTolerance(0, 0x1p-40), new SampleLimits(64, 256));
        System.out.printf("  length=%s converged=%s panels=%d%n", n.length(), n.converged(), n.panelCount());
        for (double f : new double[] {0.1, 0.3, 0.5, 0.7, 0.9}) {
            double s = f * n.length(); double p = n.parameterAt(s);
            System.out.printf("  s=%.6f -> t=%.17g, lengthAt(t) - s = %.3e%n", s, p, n.lengthAt(p) - s);
        }
    }
    static void addNodes(java.util.Set<Double> set, double lo, double hi, double x1, double x2) {
        double h = 0.5 * (hi - lo), c = lo + h;
        set.add(c); set.add(c - h * x1); set.add(c + h * x1); set.add(c - h * x2); set.add(c + h * x2);
    }
}
