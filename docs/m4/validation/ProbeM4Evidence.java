import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.curve.*;
import workshop.vfx.math.sample.*;

/**
 * M4 evidence probe (review scratch; run against the throwaway reference implementation, which is not delivered).
 * Usage: java ProbeM4Evidence <src/test/resources/m4/reference>
 * Measures, over the reference rows, the achieved length error relative to tau, the evaluation counts, the inversion
 * work per sample, and compares the adaptive Gauss-Legendre table with fixed-step chords at equal cost.
 */
public class ProbeM4Evidence {
    static double h(String s) { return Double.parseDouble(s); }
    static List<String[]> csv(Path p) throws Exception {
        List<String[]> out = new ArrayList<>(); boolean header = false;
        for (String l : Files.readAllLines(p)) { if (l.isBlank() || l.startsWith("#")) continue; if (!header) { header = true; continue; } out.add(l.split(",")); }
        return out;
    }
    static Vec3 v(double[] p, int i) { return new Vec3(p[i], p[i + 1], p[i + 2]); }
    static Curve curve(String k, double[] p) {
        return switch (k) { case "LINE" -> new LineSegment(v(p, 0), v(p, 3)); case "QUAD" -> new QuadraticBezier(v(p, 0), v(p, 3), v(p, 6));
            case "CUBIC" -> new CubicBezier(v(p, 0), v(p, 3), v(p, 6), v(p, 9)); default -> new Spiral(p[0], p[1], p[2], p[3], p[4]); };
    }
    static final class Counting implements Curve {
        final Curve c; long d, pts; Counting(Curve c) { this.c = c; }
        public Vec3 point(double t) { pts++; return c.point(t); }
        public Vec3 derivative(double t) { d++; return c.derivative(t); }
        public Vec3 secondDerivative(double t) { return c.secondDerivative(t); }
    }
    static double chords(Curve c, double t0, double t1, int n) {
        double s = 0; Vec3 p = c.point(t0);
        for (int i = 1; i <= n; i++) { Vec3 q = c.point(i == n ? t1 : t0 + (t1 - t0) * (i / (double) n)); s += q.sub(p).length(); p = q; }
        return s;
    }
    public static void main(String[] a) throws Exception {
        Path ref = Path.of(a[0]);
        SampleLimits big = new SampleLimits(1 << 20, 1 << 22);
        System.out.println("== arclength.csv: achieved error / tau, by tolerance");
        for (double rel : new double[] {1e-4, 1e-7, 0x1p-30, 1e-12}) {
            ArcLengthTolerance tol = new ArcLengthTolerance(0, rel);
            double worst = 0, worstChordRel = 0, worstGlRel = 0; long evalSum = 0, evalMax = 0; int rows = 0, conv = 0, exceed = 0; String worstRow = "";
            for (String[] r : csv(ref.resolve("arclength.csv"))) {
                double[] p = new double[12]; for (int i = 0; i < 12; i++) p[i] = h(r[1 + i]);
                double t0 = h(r[13]), t1 = h(r[14]), L = h(r[15]);
                Curve c = curve(r[0], p);
                ArcLengthTable t = ArcLengthTable.build(c, t0, t1, tol, big);
                rows++; if (t.converged()) conv++;
                evalSum += t.evaluations(); evalMax = Math.max(evalMax, t.evaluations());
                double err = Math.abs(t.length() - L);
                if (t.effectiveTolerance() > 0) {
                    double ratio = err / t.effectiveTolerance();
                    if (ratio > worst) { worst = ratio; worstRow = r[0] + " " + r[17]; }
                    if (err > t.effectiveTolerance()) exceed++;
                }
                if (L > 0 && rel == 1e-7) {
                    worstGlRel = Math.max(worstGlRel, err / L);
                    worstChordRel = Math.max(worstChordRel, Math.abs(chords(c, t0, t1, t.evaluations()) - L) / L);
                }
            }
            System.out.printf("rel %.1e: rows %d, converged %d, rows with error > tau %d, worst error/tau %.3e (%s), evaluations mean %.0f max %d%n",
                rel, rows, conv, exceed, worst, worstRow, evalSum / (double) rows, evalMax);
            if (rel == 1e-7) System.out.printf("   at equal cost (chords with as many points as GL5 evaluations): worst relative error GL5 %.2e, chords %.2e%n", worstGlRel, worstChordRel);
        }
        System.out.println("== equal-distance sampling work (rel 2^-30): derivative evaluations per parameterAt call");
        ArcLengthTolerance tol = new ArcLengthTolerance(0, 0x1p-30);
        long samples = 0, evals = 0, maxPer = 0;
        for (String[] r : csv(ref.resolve("arclength.csv"))) {
            double[] p = new double[12]; for (int i = 0; i < 12; i++) p[i] = h(r[1 + i]);
            double t0 = h(r[13]), t1 = h(r[14]);
            Counting c = new Counting(curve(r[0], p));
            ArcLengthTable t = ArcLengthTable.build(c, t0, t1, tol, big);
            if (t.length() == 0) continue;
            for (int k = 1; k < 64; k++) {
                long before = c.d;
                t.parameterAt(t.length() * (k / 64.0));
                long used = c.d - before; evals += used; samples++; maxPer = Math.max(maxPer, used);
            }
        }
        System.out.printf("parameterAt: %d calls, mean %.1f derivative evaluations, max %d (contract limit 336)%n", samples, evals / (double) samples, maxPer);
        System.out.println("== budget behaviour: oscillating user curve x = t, y = 0.01 sin(400 pi t) (200 periods), rel 1e-9");
        Curve w = new Curve() {
            public Vec3 point(double t) { return new Vec3(t, 0.01 * StrictMath.sin(400 * Math.PI * t), 0); }
            public Vec3 derivative(double t) { return new Vec3(1, 0.01 * 400 * Math.PI * StrictMath.cos(400 * Math.PI * t), 0); }
            public Vec3 secondDerivative(double t) { return Vec3.ZERO; }
        };
        for (int budget : new int[] {256, 4096, 65536, 1 << 20}) {
            ArcLengthTable t = ArcLengthTable.build(w, new ArcLengthTolerance(0, 1e-9), new SampleLimits(16, budget));
            System.out.printf("budget %8d: evaluations %8d panels %6d converged %-5s length %.15f estimate %.2e%n", budget, t.evaluations(), t.panelCount(), t.converged(), t.length(), t.errorEstimate());
        }
        System.out.println("== indicative timing on this machine (not a target): conical spiral, rel 1e-8, warm JIT");
        Curve cone = Spiral.conicalSpiral(1, 6, 4, 10);
        SampleLimits lim = new SampleLimits(4096, 1 << 16);
        for (int rep = 0; rep < 5; rep++) {
            long s0 = System.nanoTime(); ArcLengthTable t = null;
            for (int i = 0; i < 500; i++) t = ArcLengthTable.build(cone, new ArcLengthTolerance(0, 1e-8), lim);
            long s1 = System.nanoTime(); SampleSet s = null;
            for (int i = 0; i < 500; i++) s = Sampler.uniformDistance(t, 128, Endpoints.EXCLUDE_END, lim);
            long s2 = System.nanoTime();
            for (int i = 0; i < 500; i++) s = Sampler.uniformParameter(cone, 128, Endpoints.EXCLUDE_END, lim);
            long s3 = System.nanoTime();
            if (rep == 4) System.out.printf("build %.1f us; 128 equal-distance samples %.1f us; 128 uniform-parameter samples %.1f us%n", (s1 - s0) / 500e3, (s2 - s1) / 500e3, (s3 - s2) / 500e3);
        }
    }
}
