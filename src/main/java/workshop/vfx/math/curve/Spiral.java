package workshop.vfx.math.curve;

import workshop.vfx.math.core.Vec3;

/** Circular family about local +Y; positive turns run from +X toward -Z. */
public record Spiral(double startRadius, double endRadius, double rise, double turns, double startTurn)
        implements Curve {
    private static final double TAU = 2 * Math.PI;

    public Spiral {
        if (!Double.isFinite(startRadius) || startRadius < 0
                || !Double.isFinite(endRadius) || endRadius < 0
                || !Double.isFinite(rise) || !Double.isFinite(turns) || !Double.isFinite(startTurn)) {
            throw new IllegalArgumentException("Spiral: nonnegative finite radii and finite rise, turns and startTurn required");
        }
    }

    public static Spiral circle(double radius) {
        return new Spiral(radius, radius, 0, 1, 0);
    }

    public static Spiral arc(double radius, double startTurn, double turns) {
        return new Spiral(radius, radius, 0, turns, startTurn);
    }

    public static Spiral helix(double radius, double rise, double turns) {
        return new Spiral(radius, radius, rise, turns, 0);
    }

    public static Spiral planarSpiral(double r0, double r1, double turns) {
        return new Spiral(r0, r1, 0, turns, 0);
    }

    public static Spiral conicalSpiral(double r0, double r1, double rise, double turns) {
        return new Spiral(r0, r1, rise, turns, 0);
    }

    public static Spiral horizontalCounterClockwiseFromAbove(double radius) {
        return circle(radius);
    }

    public static Spiral horizontalClockwiseFromAbove(double radius) {
        return new Spiral(radius, radius, 0, -1, 0);
    }

    public boolean closed() {
        return startRadius == endRadius && rise == 0 && turns != 0 && Math.rint(turns) == turns;
    }

    public Vec3 point(double t) {
        CurveChecks.parameter(t);
        SinCos cs = phase(t);
        double r = radius(t);
        return new Vec3(r * cs.cos, rise * t, -r * cs.sin);
    }

    public Vec3 derivative(double t) {
        CurveChecks.parameter(t);
        SinCos cs = phase(t);
        double dr = endRadius - startRadius;
        double w = TAU * turns;
        double wr = w * radius(t);
        return new Vec3(dr * cs.cos - wr * cs.sin, rise, -dr * cs.sin - wr * cs.cos);
    }

    public Vec3 secondDerivative(double t) {
        CurveChecks.parameter(t);
        SinCos cs = phase(t);
        double dr = endRadius - startRadius;
        double w = TAU * turns;
        double k = 2 * dr * w;
        double w2r = w * w * radius(t);
        return new Vec3(-k * cs.sin - w2r * cs.cos, 0, -k * cs.cos + w2r * cs.sin);
    }

    private double radius(double t) {
        return startRadius == endRadius ? startRadius : (1 - t) * startRadius + t * endRadius;
    }

    private SinCos phase(double t) {
        // Reduce both operands before combining; unreduced offsets can erase the fraction.
        double a = fraction(turns * t);
        double b = fraction(startTurn);
        double f = fraction(a + b);
        double quarters = 4 * f;
        int q = (int) Math.floor(quarters);
        double x = (quarters - q) * (Math.PI / 2);
        double c = StrictMath.cos(x);
        double s = StrictMath.sin(x);
        return switch (q) {
            case 1 -> new SinCos(-s, c);
            case 2 -> new SinCos(-c, -s);
            case 3 -> new SinCos(s, -c);
            default -> new SinCos(c, s);
        };
    }

    private static double fraction(double x) {
        double f = x % 1.0;
        if (f < 0) f += 1.0;
        return f == 1.0 ? 0.0 : f;
    }

    private record SinCos(double cos, double sin) { }
}
