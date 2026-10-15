package workshop.vfx.math.core;

/** Right-handed orthonormal axes, corresponding to right, up and back. */
public record Frame(Vec3 x, Vec3 y, Vec3 z) {
    public static final Frame WORLD = new Frame(Vec3.X, Vec3.Y, Vec3.Z);

    public Frame {
        if (x == null || y == null || z == null || !x.isFinite() || !y.isFinite() || !z.isFinite()
                || Math.abs(x.length() - 1) > 1e-9 || Math.abs(y.length() - 1) > 1e-9
                || Math.abs(z.length() - 1) > 1e-9 || Math.abs(x.dot(y)) > 1e-9
                || Math.abs(y.dot(z)) > 1e-9 || Math.abs(z.dot(x)) > 1e-9
                || x.cross(y).sub(z).length() > 1e-9) {
            throw new IllegalArgumentException("Frame: finite right-handed orthonormal axes required");
        }
    }

    public static Frame alignY(Vec3 y, Vec3 xHint) {
        Vec3 uy = y.normalizedOr(null);
        if (uy == null) return WORLD;
        Vec3 ux = perpendicular(uy, xHint);
        Vec3 uz = ux.cross(uy).normalizedOr(Vec3.Z);
        return new Frame(ux, uy, uz);
    }

    public static Frame alignZ(Vec3 z, Vec3 yHint) {
        Vec3 uz = z.normalizedOr(null);
        if (uz == null) return WORLD;
        Vec3 uy = perpendicular(uz, yHint);
        Vec3 ux = uy.cross(uz).normalizedOr(Vec3.X);
        return new Frame(ux, uy, uz);
    }

    public static Frame fromHeading(Vec3 forward, Vec3 up) {
        Vec3 f = forward.normalizedOr(null);
        if (f == null) return WORLD;
        Vec3 hint = up == null ? Vec3.Y : up.normalizedOr(Vec3.Y);
        return alignZ(f.negate(), hint);
    }

    public static Frame transportY(Frame prev, Vec3 newY) {
        Vec3 uy = newY.normalizedOr(null);
        if (uy == null) return prev;
        Quat arc = Quat.shortestArc(prev.y, uy);
        return alignY(uy, arc.rotate(prev.x));
    }

    public Vec3 toWorld(Vec3 local) {
        return x.mul(local.x()).add(y.mul(local.y())).add(z.mul(local.z()));
    }
    public Vec3 toLocal(Vec3 world) { return new Vec3(world.dot(x), world.dot(y), world.dot(z)); }

    static Vec3 leastAligned(Vec3 axis) {
        double ax = Math.abs(axis.x()), ay = Math.abs(axis.y()), az = Math.abs(axis.z());
        if (ax <= ay && ax <= az) return Vec3.X;
        return ay <= az ? Vec3.Y : Vec3.Z;
    }

    private static Vec3 perpendicular(Vec3 axis, Vec3 hint) {
        Vec3 h = hint == null ? null : hint.normalizedOr(null);
        Vec3 p = h == null ? Vec3.ZERO : h.sub(axis.mul(axis.dot(h)));
        if (p.length() < 1e-8) {
            h = leastAligned(axis);
            p = h.sub(axis.mul(axis.dot(h)));
        }
        // A second Gram-Schmidt pass removes cancellation left by a nearly parallel hint.
        p = p.sub(axis.mul(axis.dot(p)));
        return p.normalizedOr(leastAligned(axis));
    }
}
