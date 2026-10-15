package workshop.vfx.math.core;

/** Shared scalar rules. All angles in this package are radians. */
public final class MathUtil {
    public static final double LENGTH_EPS = 1e-12;

    private MathUtil() { }

    public static double clamp(double v, double lo, double hi) {
        if (Double.isNaN(lo) || Double.isNaN(hi) || lo > hi) {
            throw new IllegalArgumentException("clamp: invalid bounds");
        }
        return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }

    public static boolean approxEquals(double a, double b, double tol) {
        return Math.abs(a - b) <= tol;
    }
}
