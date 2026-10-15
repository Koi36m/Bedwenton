package workshop.vfx.math.curve;

import workshop.vfx.math.core.Vec3;

/** Cubic Bernstein curve evaluated by de Casteljau interpolation. */
public record CubicBezier(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3) implements Curve {
    public CubicBezier {
        CurveChecks.control(p0);
        CurveChecks.control(p1);
        CurveChecks.control(p2);
        CurveChecks.control(p3);
    }

    public Vec3 point(double t) {
        CurveChecks.parameter(t);
        Vec3 a = p0.lerp(p1, t);
        Vec3 b = p1.lerp(p2, t);
        Vec3 c = p2.lerp(p3, t);
        return a.lerp(b, t).lerp(b.lerp(c, t), t);
    }

    public Vec3 derivative(double t) {
        CurveChecks.parameter(t);
        Vec3 a = p1.sub(p0);
        Vec3 b = p2.sub(p1);
        Vec3 c = p3.sub(p2);
        return a.lerp(b, t).lerp(b.lerp(c, t), t).mul(3);
    }

    public Vec3 secondDerivative(double t) {
        CurveChecks.parameter(t);
        Vec3 a = p1.sub(p0);
        Vec3 b = p2.sub(p1);
        Vec3 c = p3.sub(p2);
        return b.sub(a).lerp(c.sub(b), t).mul(6);
    }
}
