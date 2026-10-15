package workshop.vfx.math.curve;

import workshop.vfx.math.core.Vec3;

/** Quadratic Bernstein curve evaluated by de Casteljau interpolation. */
public record QuadraticBezier(Vec3 p0, Vec3 p1, Vec3 p2) implements Curve {
    public QuadraticBezier {
        CurveChecks.control(p0);
        CurveChecks.control(p1);
        CurveChecks.control(p2);
    }

    public Vec3 point(double t) {
        CurveChecks.parameter(t);
        return p0.lerp(p1, t).lerp(p1.lerp(p2, t), t);
    }

    public Vec3 derivative(double t) {
        CurveChecks.parameter(t);
        return p1.sub(p0).lerp(p2.sub(p1), t).mul(2);
    }

    public Vec3 secondDerivative(double t) {
        CurveChecks.parameter(t);
        return p2.sub(p1).sub(p1.sub(p0)).mul(2);
    }
}
