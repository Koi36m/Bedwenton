package workshop.vfx.math.sample;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.curve.Curve;

/** Immutable adaptive Gauss-Legendre table following the M4 R2.1 operation order. */
public final class ArcLengthTable {
    private static final double X1 = 0x1.13b23fd99b705p-1;
    private static final double X2 = 0x1.cff6ce0533a69p-1;
    private static final double H0 = 0x1.23456789abcdfp-2;
    private static final double H1 = 0x1.ea1da25ae415bp-3;
    private static final double H2 = 0x1.e539ec36e038cp-4;
    private static final double TAU_FLOOR = 0x0.0000000000400p-1022;

    private final Curve curve;
    private final double t0;
    private final double t1;
    private final Panel[] panels;
    private final double[] cumulative;
    private final double length;
    private final double tau;
    private final double error;
    private final int evaluations;

    private ArcLengthTable(Curve curve, double t0, double t1, List<Panel> sorted,
            double tau, int evaluations) {
        this.curve = curve;
        this.t0 = t0;
        this.t1 = t1;
        this.tau = tau;
        this.evaluations = evaluations;
        panels = sorted.toArray(Panel[]::new);
        cumulative = new double[panels.length + 1];
        double sum = 0;
        double errors = 0;
        for (int k = 0; k < panels.length; k++) {
            sum = sum + panels[k].i2();
            cumulative[k + 1] = sum;
            errors = errors + panels[k].error();
        }
        if (!Double.isFinite(sum)) {
            throw new ArithmeticException("Arc length is not finite");
        }
        length = sum;
        error = errors;
    }

    public static ArcLengthTable build(Curve curve, ArcLengthTolerance tolerance, SampleLimits limits) {
        return build(curve, 0, 1, tolerance, limits);
    }

    public static ArcLengthTable build(Curve curve, double t0, double t1,
            ArcLengthTolerance tolerance, SampleLimits limits) {
        SampleChecks.nonnull(curve);
        SampleChecks.nonnull(tolerance);
        SampleChecks.nonnull(limits);
        SampleChecks.range(t0, t1);
        double[] nodes = new double[17];
        double[] whole = new double[16];
        for (int k = 0; k < 16; k++) {
            nodes[k] = Math.min(t1, t0 + (t1 - t0) * (k / 16.0));
        }
        nodes[16] = t1;
        // All 16 whole-panel rules precede all 16 half-panel records.
        for (int k = 0; k < 16; k++) {
            whole[k] = rule(curve, nodes[k], nodes[k + 1]);
        }
        PriorityQueue<Panel> active = new PriorityQueue<>((a, b) -> {
            int order = Double.compare(b.error(), a.error());
            return order != 0 ? order : comparePosition(a, b);
        });
        List<Panel> finished = new ArrayList<>();
        double initialLength = 0;
        double estimate = 0;
        for (int k = 0; k < 16; k++) {
            Panel panel = panel(curve, nodes[k], nodes[k + 1], 0, whole[k]);
            active.add(panel);
            initialLength = initialLength + panel.i2();
            estimate = estimate + panel.error();
        }
        if (!Double.isFinite(initialLength)) {
            throw new ArithmeticException("Initial arc length is not finite");
        }
        double tau = Math.max(Math.max(tolerance.absolute(), tolerance.relative() * initialLength), TAU_FLOOR);
        int used = 240;
        // Each split costs 20 calls. Unsplittable removals are also bounded by
        // the total number of records that this budget can create.
        int maxRemovals = 16 + 2 * ((limits.maxEvaluations() - 240) / 20);
        for (int step = 0; step < maxRemovals && estimate > tau
                && !active.isEmpty() && used + 20 <= limits.maxEvaluations(); step++) {
            Panel p = active.remove();
            estimate = estimate - p.error();
            double mid = p.a() + 0.5 * (p.b() - p.a());
            if (p.depth() == 50 || !(p.a() < mid && mid < p.b())) {
                finished.add(p);
            } else {
                Panel left = panel(curve, p.a(), mid, p.depth() + 1, p.left());
                Panel right = panel(curve, mid, p.b(), p.depth() + 1, p.right());
                used += 20;
                active.add(left);
                active.add(right);
                estimate = (estimate + left.error()) + right.error();
            }
        }
        finished.addAll(active);
        finished.sort(ArcLengthTable::comparePosition);
        return new ArcLengthTable(curve, t0, t1, finished, tau, used);
    }

    private static int comparePosition(Panel a, Panel b) {
        int order = Double.compare(a.a(), b.a());
        return order != 0 ? order : Double.compare(a.b(), b.b());
    }

    private static Panel panel(Curve curve, double a, double b, int depth, double i1) {
        double mid = a + 0.5 * (b - a);
        double left = rule(curve, a, mid);
        double right = rule(curve, mid, b);
        double i2 = left + right;
        return new Panel(a, b, depth, i1, left, right, i2, Math.abs(i2 - i1));
    }

    private record Panel(double a, double b, int depth, double i1,
            double left, double right, double i2, double error) {
        private boolean ratioMode() {
            return i1 > 0 && i2 <= 2 * i1 && i1 <= 2 * i2;
        }
    }

    private record Speed(double mu, double nu) { }

    private static Speed speed(Curve curve, double t) {
        Vec3 v = curve.derivative(t);
        if (!v.isFinite()) {
            throw new ArithmeticException("Curve derivative has a non-finite component");
        }
        double mu = Math.max(Math.abs(v.x()), Math.max(Math.abs(v.y()), Math.abs(v.z())));
        if (mu == 0) {
            return new Speed(mu, 0);
        }
        double x = v.x() / mu;
        double y = v.y() / mu;
        double z = v.z() / mu;
        return new Speed(mu, Math.sqrt(x * x + y * y + z * z));
    }

    private static double rule(Curve curve, double a, double b) {
        double d = b - a;
        double h = 0.5 * d;
        double c = a + h;
        Speed v0 = speed(curve, c);
        Speed v1 = speed(curve, c - h * X1);
        Speed v2 = speed(curve, c + h * X1);
        Speed v3 = speed(curve, c - h * X2);
        Speed v4 = speed(curve, c + h * X2);
        double m = Math.max(v0.mu(), Math.max(v1.mu(), Math.max(v2.mu(), Math.max(v3.mu(), v4.mu()))));
        if (m == 0) {
            return 0;
        }
        double r0 = (v0.mu() / m) * v0.nu();
        double r1 = (v1.mu() / m) * v1.nu();
        double r2 = (v2.mu() / m) * v2.nu();
        double r3 = (v3.mu() / m) * v3.nu();
        double r4 = (v4.mu() / m) * v4.nu();
        double s = H0 * r0 + H1 * (r1 + r2) + H2 * (r3 + r4);
        double p = d * s;
        if (p >= Double.MIN_NORMAL) {
            return p * m;
        }
        double q = m * s;
        return q <= Double.MAX_VALUE ? d * q : (m * d) * s;
    }

    public Curve curve() { return curve; }
    public double startParameter() { return t0; }
    public double endParameter() { return t1; }
    public double length() { return length; }
    public double effectiveTolerance() { return tau; }
    public double errorEstimate() { return error; }
    public boolean converged() { return error <= tau; }
    public int panelCount() { return panels.length; }
    public int evaluations() { return evaluations; }

    public double lengthAt(double t) {
        if (!(t >= t0 && t <= t1)) {
            throw new IllegalArgumentException("Parameter outside table range");
        }
        if (t == t0) {
            return 0;
        }
        if (t == t1) {
            return length;
        }
        // Upper bound on panel starts; duplicates belong to the last start.
        int lo = 0;
        int hi = panels.length - 1;
        for (int step = 0; step < 32 && lo < hi; step++) {
            int mid = lo + (hi - lo + 1) / 2;
            if (panels[mid].a() <= t) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return Math.min(length, cumulative[lo] + partial(panels[lo], t));
    }

    private double partial(Panel p, double t) {
        if (t <= p.a()) {
            return 0;
        }
        if (t >= p.b()) {
            return p.i2();
        }
        if (p.i2() == 0) {
            return 0;
        }
        if (p.ratioMode()) {
            double rho = p.i2() / p.i1();
            return Math.min(p.i2(), rule(curve, p.a(), t) * rho);
        }
        return p.i2() * ((t - p.a()) / (p.b() - p.a()));
    }

    public double parameterAt(double distance) {
        if (!(distance >= 0 && distance <= length)) {
            throw new IllegalArgumentException("Distance outside table length");
        }
        if (distance == 0) {
            return t0;
        }
        if (distance == length) {
            return t1;
        }
        // Lower bound on cumulative panel ends; retain stationary intervals.
        int lowIndex = 0;
        int highIndex = panels.length - 1;
        for (int step = 0; step < 32 && lowIndex < highIndex; step++) {
            int mid = lowIndex + (highIndex - lowIndex) / 2;
            if (cumulative[mid + 1] >= distance) {
                highIndex = mid;
            } else {
                lowIndex = mid + 1;
            }
        }
        Panel p = panels[lowIndex];
        double r = Math.min(distance - cumulative[lowIndex], p.i2());
        if (p.i2() == 0 || r <= 0) {
            return p.a();
        }
        double lo = p.a();
        double hi = p.b();
        double fLo = -r;
        double fHi = p.i2() - r;
        double tolInv = tau * 0x1p-10;
        if (Math.min(r, fHi) <= tolInv) {
            return betterEnd(lo, hi, fLo, fHi);
        }
        boolean ratioMode = p.ratioMode();
        double rho = ratioMode ? p.i2() / p.i1() : 0;
        double sigma = p.i2() / (p.b() - p.a());
        double t = lo + (hi - lo) * (r / p.i2());
        if (!(lo < t && t < hi)) {
            t = lo + 0.5 * (hi - lo);
        }
        if (!(lo < t && t < hi)) {
            return betterEnd(lo, hi, fLo, fHi);
        }
        for (int i = 0; i < 64; i++) {
            double f = partial(p, t) - r;
            if (f == 0) {
                return t;
            }
            if (f < 0) {
                lo = t;
                fLo = f;
            } else {
                hi = t;
                fHi = f;
            }
            if (Math.abs(f) <= tolInv) {
                return t;
            }
            if (i == 63) {
                break;
            }
            double candidate = Double.NaN;
            if (i < 16) {
                double slope;
                if (ratioMode) {
                    Speed v = speed(curve, t);
                    slope = (v.mu() * v.nu()) * rho;
                } else {
                    slope = sigma;
                }
                if (slope > 0) {
                    candidate = t - f / slope;
                }
            }
            if (!(lo < candidate && candidate < hi)) {
                candidate = lo + 0.5 * (hi - lo);
            }
            if (!(lo < candidate && candidate < hi)) {
                break;
            }
            t = candidate;
        }
        return betterEnd(lo, hi, fLo, fHi);
    }

    private static double betterEnd(double lo, double hi, double fLo, double fHi) {
        return Math.abs(fLo) < Math.abs(fHi) ? lo : hi;
    }
}
