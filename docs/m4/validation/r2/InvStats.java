import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.curve.*;
import workshop.vfx.math.sample.*;
/**
 * M4 R2 probe (review scratch): inversion exits and costs. Needs a copy of the throwaway reference implementation
 * instrumented with public static counters ArcLengthTable.EXIT[exact, tolerance, cap, collapse] and ITERS[iterations]
 * (four increments at the exits of parameterAt); the delivered contract has no such members.
 */
public class InvStats {
    static long d;
    static Curve counting(Curve c) {
        return new Curve() {
            public Vec3 point(double t) { return c.point(t); }
            public Vec3 derivative(double t) { d++; return c.derivative(t); }
            public Vec3 secondDerivative(double t) { return c.secondDerivative(t); }
        };
    }
    static void reset() { java.util.Arrays.fill(ArcLengthTable.EXIT, 0); java.util.Arrays.fill(ArcLengthTable.ITERS, 0); }
    static void report(String name, long calls, long maxEv, double sumEv) {
        long[] e = ArcLengthTable.EXIT; int maxIt = 0; for (int i = 0; i < 65; i++) if (ArcLengthTable.ITERS[i] > 0) maxIt = i;
        System.out.printf("%-58s calls=%5d mean evals=%7.2f max evals=%3d max iterations=%2d exits: exact=%d tolerance=%d cap=%d collapse=%d%n",
            name, calls, sumEv / calls, maxEv, maxIt, e[0], e[1], e[2], e[3]);
    }
    static void run(String name, ArcLengthTable t, double[] targets) {
        reset(); long max = 0; double sum = 0;
        for (double s : targets) { d = 0; t.parameterAt(s); max = Math.max(max, d); sum += d; }
        report(name, targets.length, max, sum);
    }
    static double[] grid(double L, int n) { double[] r = new double[n - 2]; for (int k = 1; k < n - 1; k++) r[k - 1] = L * k / (n - 1); return r; }
    public static void main(String[] a) {
        SampleLimits lim = new SampleLimits(1 << 20, 1 << 20);
        System.out.println("== smooth curves (the newtonEfficiency curves plus a cusp), relative tolerances 2^-20, 2^-30, 2^-40; 998 equally spaced interior targets each");
        Curve[] smooth = {Spiral.conicalSpiral(0.5, 5, 1, 9), new CubicBezier(new Vec3(0, 0, 0), new Vec3(4, 6, 0), new Vec3(-3, 5, 2), new Vec3(2, 0, 1)),
            new QuadraticBezier(Vec3.ZERO, new Vec3(3, 7, 0), new Vec3(6, 0, 2)),
            new CubicBezier(new Vec3(0, 0, 0), new Vec3(1, 1, 0), new Vec3(0, 1, 0), new Vec3(1, 0, 0))};
        for (double rel : new double[] {0x1p-20, 0x1p-30, 0x1p-40}) for (Curve c : smooth) {
            ArcLengthTable t = ArcLengthTable.build(counting(c), new ArcLengthTolerance(0, rel), lim);
            run(String.format("rel %.0e %s", rel, c.getClass().getSimpleName() + (c instanceof CubicBezier cb && cb.p1().x() == 1 ? "(cusp)" : "")), t, grid(t.length(), 1000));
        }
        System.out.println("== Newton creep: x = t + t^2/2 with a 1e6 speed spike within +-eps of the exact root; tolerance (0, 2^-40), budget 256");
        for (double eps : new double[] {1e-7, 3e-5, 1e-3}) {
            reset(); long max = 0; double sum = 0; int n = 0; double worstT = 0;
            for (double f : new double[] {0.11, 0.23, 0.37, 0.41, 0.53, 0.67, 0.71, 0.89}) {
                double L = 1.5, s = f * L, ts = -1 + Math.sqrt(1 + 2 * s);
                final double lo = ts - eps, hi = ts + eps;
                Curve c = new Curve() {
                    public Vec3 point(double t) { return new Vec3(t + 0.5 * t * t, 0, 0); }
                    public Vec3 derivative(double t) { d++; return new Vec3(t >= lo && t <= hi ? 1e6 : 1 + t, 0, 0); }
                    public Vec3 secondDerivative(double t) { return Vec3.ZERO; }
                };
                ArcLengthTable t = ArcLengthTable.build(c, new ArcLengthTolerance(0, 0x1p-40), new SampleLimits(64, 256));
                d = 0; double p = t.parameterAt(s); max = Math.max(max, d); sum += d; n++; worstT = Math.max(worstT, Math.abs(p - ts));
            }
            report(String.format("creep eps=%.0e (max |t - t*| = %.2e)", eps, worstT), n, max, sum);
        }
        System.out.println("== noisy speed (bit 11 of t selects 0.25 or 1.75); tolerance (0, 2^-40), budget 256");
        Curve noisy = counting(new Curve() {
            public Vec3 point(double t) { return new Vec3(t, 0, 0); }
            public Vec3 derivative(double t) { long b = Double.doubleToRawLongBits(t); return new Vec3(((b >>> 11) & 1) == 0 ? 0.25 : 1.75, 0, 0); }
            public Vec3 secondDerivative(double t) { return Vec3.ZERO; }
        });
        ArcLengthTable nt = ArcLengthTable.build(noisy, new ArcLengthTolerance(0, 0x1p-40), new SampleLimits(1 << 12, 256));
        run("noisy, 998 targets", nt, grid(nt.length(), 1000));
    }
}
