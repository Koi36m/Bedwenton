package workshop.vfx.math.core;

import org.joml.Quaterniond;
import org.joml.Vector3d;

/**
 * The ONLY class that touches JOML. JOML is a test-scope reference implementation and is never a production
 * dependency. If a JOML signature differs from what is written here, fix it in this file only and record the
 * change in the verification report; do not change expected values anywhere else.
 */
final class JomlOracle {
    private JomlOracle() {}

    private static Quaterniond toJoml(Quat q) {
        return new Quaterniond(q.x(), q.y(), q.z(), q.w()); // JOML constructor order is (x, y, z, w)
    }

    private static Quat fromJoml(Quaterniond q) {
        return new Quat(q.w, q.x, q.y, q.z);
    }

    static Vec3 rotate(Quat q, Vec3 v) {
        Vector3d out = toJoml(q).transform(new Vector3d(v.x(), v.y(), v.z()));
        return new Vec3(out.x, out.y, out.z);
    }

    /** Hamilton product a*b (b applied first), as JOML's mul computes this = this * other. */
    static Quat mul(Quat a, Quat b) {
        return fromJoml(toJoml(a).mul(toJoml(b)));
    }

    static Quat slerp(Quat a, Quat b, double t) {
        return fromJoml(toJoml(a).slerp(toJoml(b), t));
    }

    /** JOML's own rotationXYZ(x, y, z); its application order is deliberately NOT assumed by the tests. */
    static Quat rotationXYZ(double x, double y, double z) {
        return fromJoml(new Quaterniond().rotationXYZ(x, y, z));
    }
}
