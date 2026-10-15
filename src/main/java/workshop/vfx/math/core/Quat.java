package workshop.vfx.math.core;

/** Hamilton quaternion (w,x,y,z), active right-handed rotations. */
public record Quat(double w, double x, double y, double z) {
    public static final Quat IDENTITY = new Quat(1, 0, 0, 0);

    public static Quat axisAngle(Vec3 axis, double radians) {
        Vec3 unit = axis == null ? null : axis.normalizedOr(null);
        if (unit == null || !Double.isFinite(radians)) {
            throw new IllegalArgumentException("axisAngle: finite nondegenerate axis and finite angle required");
        }
        double s = Math.sin(radians / 2);
        return new Quat(Math.cos(radians / 2), s * unit.x(), s * unit.y(), s * unit.z());
    }

    public static Quat fromEulerApplyXThenYThenZ(double rx, double ry, double rz) {
        return axisAngle(Vec3.Z, rz).mul(axisAngle(Vec3.Y, ry)).mul(axisAngle(Vec3.X, rx));
    }

    /** Applies o first, then this; does not renormalize. */
    public Quat mul(Quat o) {
        return new Quat(w * o.w - x * o.x - y * o.y - z * o.z,
                w * o.x + x * o.w + y * o.z - z * o.y,
                w * o.y - x * o.z + y * o.w + z * o.x,
                w * o.z + x * o.y - y * o.x + z * o.w);
    }
    public Quat conjugate() { return new Quat(w, -x, -y, -z); }
    public Quat inverse() { return normalized().conjugate(); }

    public Quat normalized() {
        double m = Math.max(Math.max(Math.abs(w), Math.abs(x)), Math.max(Math.abs(y), Math.abs(z)));
        if (!finite() || m == 0) return IDENTITY;
        double sw = w / m, sx = x / m, sy = y / m, sz = z / m;
        double n = Math.sqrt(sw * sw + sx * sx + sy * sy + sz * sz);
        return new Quat(sw / n, sx / n, sy / n, sz / n);
    }

    public double dot(Quat o) { return w * o.w + x * o.x + y * o.y + z * o.z; }
    public double angleTo(Quat o) {
        Quat r = conjugate().mul(o);
        return 2 * Math.atan2(Math.sqrt(r.x * r.x + r.y * r.y + r.z * r.z), Math.abs(r.w));
    }
    public Vec3 rotate(Vec3 v) {
        Vec3 u = new Vec3(x, y, z);
        Vec3 uv = u.cross(v);
        return v.add(uv.mul(2 * w)).add(u.cross(uv).mul(2));
    }

    public static Quat shortestArc(Vec3 from, Vec3 to) {
        Vec3 a = from.normalizedOr(null), b = to.normalizedOr(null);
        if (a == null || b == null) return IDENTITY;
        Vec3 sum = a.add(b);
        if (sum.length() < 1e-10) {
            Vec3 axis = a.cross(Frame.leastAligned(a)).normalizedOr(Vec3.X);
            return new Quat(0, axis.x(), axis.y(), axis.z());
        }
        Vec3 h = sum.normalizedOr(Vec3.X);
        Vec3 v = a.cross(h);
        return new Quat(a.dot(h), v.x(), v.y(), v.z()).normalized();
    }

    public static Quat slerp(Quat a, Quat b, double t) {
        if (a.dot(b) < 0) b = b.scaled(-1);
        double dw = b.w - a.w, dx = b.x - a.x, dy = b.y - a.y, dz = b.z - a.z;
        double sw = b.w + a.w, sx = b.x + a.x, sy = b.y + a.y, sz = b.z + a.z;
        double theta = 2 * Math.atan2(Math.sqrt(dw * dw + dx * dx + dy * dy + dz * dz),
                Math.sqrt(sw * sw + sx * sx + sy * sy + sz * sz));
        if (theta < 1e-8) return nlerp(a, b, t);
        double sine = Math.sin(theta);
        return blend(a, b, Math.sin((1 - t) * theta) / sine, Math.sin(t * theta) / sine).normalized();
    }

    public static Quat nlerp(Quat a, Quat b, double t) {
        if (a.dot(b) < 0) b = b.scaled(-1);
        return blend(a, b, 1 - t, t).normalized();
    }
    public Frame toFrame() { return new Frame(rotate(Vec3.X), rotate(Vec3.Y), rotate(Vec3.Z)); }

    public static Quat fromFrame(Frame f) {
        // Frame axes are matrix columns. Select the largest stable diagonal branch.
        double m00 = f.x().x(), m01 = f.y().x(), m02 = f.z().x();
        double m10 = f.x().y(), m11 = f.y().y(), m12 = f.z().y();
        double m20 = f.x().z(), m21 = f.y().z(), m22 = f.z().z();
        double trace = m00 + m11 + m22;
        Quat q;
        if (trace > 0) {
            double s = 2 * Math.sqrt(trace + 1);
            q = new Quat(s / 4, (m21 - m12) / s, (m02 - m20) / s, (m10 - m01) / s);
        } else if (m00 >= m11 && m00 >= m22) {
            double s = 2 * Math.sqrt(1 + m00 - m11 - m22);
            q = new Quat((m21 - m12) / s, s / 4, (m01 + m10) / s, (m02 + m20) / s);
        } else if (m11 >= m22) {
            double s = 2 * Math.sqrt(1 + m11 - m00 - m22);
            q = new Quat((m02 - m20) / s, (m01 + m10) / s, s / 4, (m12 + m21) / s);
        } else {
            double s = 2 * Math.sqrt(1 + m22 - m00 - m11);
            q = new Quat((m10 - m01) / s, (m02 + m20) / s, (m12 + m21) / s, s / 4);
        }
        return q.normalized();
    }

    private boolean finite() {
        return Double.isFinite(w) && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }
    private Quat scaled(double s) { return new Quat(w * s, x * s, y * s, z * s); }
    private static Quat blend(Quat a, Quat b, double wa, double wb) {
        return new Quat(wa * a.w + wb * b.w, wa * a.x + wb * b.x,
                wa * a.y + wb * b.y, wa * a.z + wb * b.z);
    }
}
