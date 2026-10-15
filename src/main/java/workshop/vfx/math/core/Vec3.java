package workshop.vfx.math.core;

/** Immutable vector. Raw arithmetic deliberately follows IEEE-754. */
public record Vec3(double x, double y, double z) {
    public static final Vec3 ZERO = new Vec3(0, 0, 0);
    public static final Vec3 X = new Vec3(1, 0, 0);
    public static final Vec3 Y = new Vec3(0, 1, 0);
    public static final Vec3 Z = new Vec3(0, 0, 1);

    public Vec3 add(Vec3 o) { return new Vec3(x + o.x, y + o.y, z + o.z); }
    public Vec3 sub(Vec3 o) { return new Vec3(x - o.x, y - o.y, z - o.z); }
    public Vec3 mul(double s) { return new Vec3(x * s, y * s, z * s); }
    public Vec3 negate() { return new Vec3(-x, -y, -z); }
    public double dot(Vec3 o) { return x * o.x + y * o.y + z * o.z; }
    public Vec3 cross(Vec3 o) {
        return new Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
    }
    public double lengthSquared() { return dot(this); }
    public double length() { return Math.sqrt(lengthSquared()); }
    public double distance(Vec3 o) { return sub(o).length(); }
    public Vec3 lerp(Vec3 to, double t) { return mul(1 - t).add(to.mul(t)); }
    public boolean isFinite() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    public Vec3 normalizedOr(Vec3 fallback) {
        double m = Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z)));
        if (!isFinite() || m < MathUtil.LENGTH_EPS) return fallback;
        // Divide components individually: the reciprocal of m could overflow.
        Vec3 scaled = new Vec3(x / m, y / m, z / m);
        return scaled.mul(1 / scaled.length());
    }
}
