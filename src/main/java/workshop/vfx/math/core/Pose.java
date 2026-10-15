package workshop.vfx.math.core;

/** Translation, rotation and positive uniform scale; parent.then(child) composes child first. */
public record Pose(Vec3 translation, Quat rotation, double scale) {
    public static final Pose IDENTITY = new Pose(Vec3.ZERO, Quat.IDENTITY, 1);

    public Pose {
        if (translation == null || !translation.isFinite() || !Double.isFinite(scale) || scale <= 0
                || rotation == null || !Double.isFinite(rotation.w()) || !Double.isFinite(rotation.x())
                || !Double.isFinite(rotation.y()) || !Double.isFinite(rotation.z())
                || (rotation.w() == 0 && rotation.x() == 0 && rotation.y() == 0 && rotation.z() == 0)) {
            throw new IllegalArgumentException("Pose: finite translation, nonzero finite rotation and positive finite scale required");
        }
        rotation = rotation.normalized();
    }
    public static Pose ofTranslation(Vec3 t) { return new Pose(t, Quat.IDENTITY, 1); }
    public Vec3 toWorld(Vec3 local) { return translation.add(rotation.rotate(local).mul(scale)); }
    public Vec3 toLocal(Vec3 world) {
        Vec3 v = rotation.conjugate().rotate(world.sub(translation));
        return new Vec3(v.x() / scale, v.y() / scale, v.z() / scale);
    }
    public Vec3 directionToWorld(Vec3 direction) { return rotation.rotate(direction); }

    public Pose then(Pose child) {
        Vec3 t = toWorld(child.translation);
        double s = scale * child.scale;
        checkResult("Pose.then", t, s);
        return new Pose(t, rotation.mul(child.rotation), s);
    }
    public Pose inverse() {
        Quat r = rotation.conjugate();
        double s = 1 / scale;
        Vec3 t = r.rotate(translation).mul(-s);
        checkResult("Pose.inverse", t, s);
        return new Pose(t, r, s);
    }
    private static void checkResult(String operation, Vec3 t, double s) {
        if (!Double.isFinite(s) || s <= 0) {
            throw new ArithmeticException(operation + ": scale is not representable");
        }
        if (!t.isFinite()) throw new ArithmeticException(operation + ": translation is not representable");
    }
}
