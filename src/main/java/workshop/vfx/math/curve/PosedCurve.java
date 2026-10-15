package workshop.vfx.math.curve;

import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Vec3;

/** Uniform-scale placement: derivatives are scaled directions, never translated. */
public record PosedCurve(Curve curve, Pose pose) implements Curve {
    public PosedCurve {
        if (curve == null || pose == null) {
            throw new IllegalArgumentException("PosedCurve: curve and pose must not be null");
        }
    }

    public Vec3 point(double t) {
        return pose.toWorld(curve.point(t));
    }

    public Vec3 derivative(double t) {
        return pose.rotation().rotate(curve.derivative(t)).mul(pose.scale());
    }

    public Vec3 secondDerivative(double t) {
        return pose.rotation().rotate(curve.secondDerivative(t)).mul(pose.scale());
    }
}
