import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.curve.*;
import workshop.vfx.math.sample.*;

/** M4 design probe (review scratch; runs against the throwaway reference): adaptive Gauss-Legendre table versus fixed-step chords on curves with known length, and refinement under a budget. */
public class DesignProbe {
    static double chord(Curve c, int n) { double s = 0; Vec3 p = c.point(0); for (int i = 1; i <= n; i++) { Vec3 q = c.point(i / (double) n); s += q.sub(p).length(); p = q; } return s; }
    static void row(String name, Curve c, double exact, double rel) {
        ArcLengthTable t = ArcLengthTable.build(c, new ArcLengthTolerance(0, rel), new SampleLimits(4096, 1 << 16));
        double errT = Math.abs(t.length() - exact) / exact;
        int evals = t.evaluations();
        double errC = Math.abs(chord(c, evals) - exact) / exact; // chord with the same number of curve evaluations
        System.out.printf("%-34s rel=%.0e  GL5: evals=%6d panels=%5d conv=%-5s relErr=%.2e est/L=%.2e | chords(%d pts): relErr=%.2e%n",
            name, rel, evals, t.panelCount(), t.converged(), errT, t.errorEstimate() / exact, evals, errC);
    }
    public static void main(String[] a) {
        for (double rel : new double[] {1e-4, 1e-8, 1e-12}) {
            row("circle r=5", Spiral.circle(5), 2 * Math.PI * 5, rel);
            row("helix r=2 rise=10 turns=8", Spiral.helix(2, 10, 8), Math.hypot(2 * Math.PI * 2 * 8, 10), rel);
            // conical spiral r 1->6, rise 4, 10 turns: exact via closed form (computed in Python, 50 digits)
            row("conical r1..6 rise4 turns10", Spiral.conicalSpiral(1, 6, 4, 10), 220.02832223681888519, rel);
            row("stationary pt v=3(1-2t)^2", new CubicBezier(new Vec3(0, 0, 0), new Vec3(1, 0, 0), new Vec3(0, 0, 0), new Vec3(1, 0, 0)), 1.0, rel);
        }
        Curve wiggle = new Curve() { // user curve with a fast oscillation: x = t, y = 0.01 sin(400 pi t)
            public Vec3 point(double t) { return new Vec3(t, 0.01 * StrictMath.sin(400 * Math.PI * t), 0); }
            public Vec3 derivative(double t) { return new Vec3(1, 0.01 * 400 * Math.PI * StrictMath.cos(400 * Math.PI * t), 0); }
            public Vec3 secondDerivative(double t) { return Vec3.ZERO; }
        };
        for (int budget : new int[] {256, 4096, 1 << 16}) {
            ArcLengthTable t = ArcLengthTable.build(wiggle, new ArcLengthTolerance(0, 1e-9), new SampleLimits(4096, budget));
            System.out.printf("wiggle budget=%6d: evals=%6d panels=%5d converged=%s L=%.15f est=%.2e%n", budget, t.evaluations(), t.panelCount(), t.converged(), t.length(), t.errorEstimate());
        }
        // timing (indicative only, this machine, warm JIT)
        Curve cone = Spiral.conicalSpiral(1, 6, 4, 10);
        for (int rep = 0; rep < 3; rep++) {
            long t0 = System.nanoTime(); ArcLengthTable t = null;
            for (int i = 0; i < 200; i++) t = ArcLengthTable.build(cone, new ArcLengthTolerance(0, 1e-8), new SampleLimits(4096, 1 << 16));
            long t1 = System.nanoTime();
            SampleSet s = null;
            for (int i = 0; i < 200; i++) s = Sampler.uniformDistance(t, 256, Endpoints.INCLUDE_END, new SampleLimits(4096, 1 << 16));
            long t2 = System.nanoTime();
            System.out.printf("indicative: build %.1f us (%d evals), 256 equal-distance samples %.1f us%n", (t1 - t0) / 200e3, t.evaluations(), (t2 - t1) / 200e3);
        }
    }
}
