import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.curve.*;
import workshop.vfx.math.sample.*;
/** M4 R2 probe (review scratch): Newton creep (finding 3). x = t + t^2/2 with a 1e6 speed spike around the exact root; reports derivative calls and errors per parameterAt. */
public class Exhaust3 {
    static long d;
    static Curve curve(double lo, double hi) {
        return new Curve() {   // x = t + t^2/2 (speed 1 + t) with a speed spike on [lo, hi] invisible to the quadrature near the root
            public Vec3 point(double t) { return new Vec3(t + 0.5 * t * t, 0, 0); }
            public Vec3 derivative(double t) { d++; return new Vec3(t >= lo && t <= hi ? 1e6 : 1 + t, 0, 0); }
            public Vec3 secondDerivative(double t) { return Vec3.ZERO; }
        };
    }
    public static void main(String[] a) {
        ArcLengthTolerance tol = new ArcLengthTolerance(0, 0x1p-40);
        SampleLimits lim = new SampleLimits(64, 256);
        ArcLengthTable base = ArcLengthTable.build(curve(2, 2), tol, lim);
        int capped = 0, total = 0;
        for (double f : new double[] {0.11, 0.23, 0.37, 0.41, 0.53, 0.67, 0.71, 0.89}) {
            double s = f * base.length();
            double ts = base.parameterAt(s);
            for (double eps : new double[] {3e-5, 1e-4, 3e-4, 1e-3}) {
                ArcLengthTable sp = ArcLengthTable.build(curve(ts - eps, ts + eps), tol, lim);
                d = 0; double t = sp.parameterAt(s); long used = d;
                total++; if (used == 383) capped++;
                System.out.printf("f=%.2f eps=%.0e: same length %s, evals %d, t - t* = %.3e, lengthAt(t) - s = %.3e%n", f, eps, sp.length() == base.length(), used, t - ts, sp.lengthAt(t) - s);
            }
        }
        System.out.println("runs: " + total + " (R1 makes 6 derivative calls per iteration, R2 5 plus 1 in the 16 Newton iterations; 384 = R1 cap)");
    }
}
