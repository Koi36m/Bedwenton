package workshop.vfx.math.curve;

import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Vec3;

/** A local-space path and its analytic derivatives with respect to its parameter. */
public interface Curve {
    Vec3 point(double t);
    Vec3 derivative(double t);
    Vec3 secondDerivative(double t);

    default Curve transformed(Pose pose) {
        return new PosedCurve(this, pose);
    }
}
