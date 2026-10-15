package workshop.vfx.math.curve;

import workshop.vfx.math.core.Vec3;

/** Degree-one Bernstein curve, with finite parameters extrapolated. */
public record LineSegment(Vec3 start, Vec3 end) implements Curve {
    public LineSegment {
        CurveChecks.control(start);
        CurveChecks.control(end);
    }

    public Vec3 point(double t) {
        CurveChecks.parameter(t);
        return start.lerp(end, t);
    }

    public Vec3 derivative(double t) {
        CurveChecks.parameter(t);
        return end.sub(start);
    }

    public Vec3 secondDerivative(double t) {
        CurveChecks.parameter(t);
        return Vec3.ZERO;
    }
}
