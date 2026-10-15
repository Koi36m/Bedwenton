import workshop.vfx.math.core.*;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.SplittableRandom;

/** Adversarial probe: prints worst-case measurements; not a test. */
public class Probe {
    static final MathContext MC = new MathContext(60);
    static double worst;
    static String worstAt;

    static void track(double err, String at) { if (!(err <= worst)) { worst = err; worstAt = at; } }
    static void report(String name) { System.out.printf("%-48s worst=%.3e  at %s%n", name, worst, worstAt); worst = 0; worstAt = "-"; }

    // exact unit-vector reference via BigDecimal
    static double[] exactUnit(double x, double y, double z) {
        BigDecimal bx = new BigDecimal(x), by = new BigDecimal(y), bz = new BigDecimal(z);
        BigDecimal n2 = bx.multiply(bx).add(by.multiply(by)).add(bz.multiply(bz));
        BigDecimal n = n2.sqrt(MC);
        return new double[]{bx.divide(n, MC).doubleValue(), by.divide(n, MC).doubleValue(), bz.divide(n, MC).doubleValue()};
    }

    static double triple(Vec3 a, Vec3 b, Vec3 c) { return a.dot(b.cross(c)); }
    static double frameErr(Frame f) {
        double e = 0;
        e = Math.max(e, Math.abs(f.x().length() - 1));
        e = Math.max(e, Math.abs(f.y().length() - 1));
        e = Math.max(e, Math.abs(f.z().length() - 1));
        e = Math.max(e, Math.abs(f.x().dot(f.y())));
        e = Math.max(e, Math.abs(f.y().dot(f.z())));
        e = Math.max(e, Math.abs(f.x().dot(f.z())));
        e = Math.max(e, Math.abs(triple(f.x(), f.y(), f.z()) - 1));
        return e;
    }
    static double extreme(SplittableRandom r) {
        double[] mags = {0, 1e-320, 1e-300, 1e-13, 1e-12, 1e-11, 1e-8, 1, 3e7, 1e100, 1e154, 1e200, 1e300, Double.MAX_VALUE, Double.MIN_VALUE, Double.MIN_NORMAL};
        double m = mags[r.nextInt(mags.length)] * (r.nextBoolean() ? 1 : r.nextDouble(0.5, 1.0));
        return r.nextBoolean() ? m : -m;
    }
    static Vec3 unitRand(SplittableRandom r) {
        while (true) { double x = r.nextDouble(-1, 1), y = r.nextDouble(-1, 1), z = r.nextDouble(-1, 1);
            double l = Math.sqrt(x*x+y*y+z*z); if (l > 0.1 && l <= 1) return new Vec3(x/l, y/l, z/l); }
    }
    static Quat quatRand(SplittableRandom r) {
        while (true) { double w = r.nextDouble(-1,1), x = r.nextDouble(-1, 1), y = r.nextDouble(-1, 1), z = r.nextDouble(-1, 1);
            double l = Math.sqrt(w*w+x*x+y*y+z*z); if (l > 0.1 && l <= 1) return new Quat(w/l, x/l, y/l, z/l); }
    }
    /** Rodrigues reference (axis unit). */
    static Vec3 rod(Vec3 k, double a, Vec3 v) {
        double c = Math.cos(a), s = Math.sin(a);
        Vec3 kxv = k.cross(v); double kd = k.dot(v);
        return v.mul(c).add(kxv.mul(s)).add(k.mul(kd * (1 - c)));
    }

    public static void main(String[] args) {
        System.out.println("java " + Runtime.version());
        SplittableRandom r = new SplittableRandom(20261015);

        // 1. normalizedOr vs exact reference over extreme component mixes
        for (int i = 0; i < 400000; i++) {
            Vec3 v = new Vec3(extreme(r), extreme(r), extreme(r));
            Vec3 fb = new Vec3(Double.NaN, 7, 7);
            Vec3 n = v.normalizedOr(fb);
            double m = Math.max(Math.abs(v.x()), Math.max(Math.abs(v.y()), Math.abs(v.z())));
            if (m < 1e-12) { if (n != fb) track(Double.POSITIVE_INFINITY, "fallback not same ref " + v); continue; }
            double[] e = exactUnit(v.x(), v.y(), v.z());
            track(Math.max(Math.abs(n.x()-e[0]), Math.max(Math.abs(n.y()-e[1]), Math.abs(n.z()-e[2]))), v.toString());
        }
        report("normalizedOr vs BigDecimal");

        // 2. Quat.normalized vs exact reference
        for (int i = 0; i < 200000; i++) {
            double w = extreme(r), x = extreme(r), y = extreme(r), z = extreme(r);
            Quat q = new Quat(w, x, y, z).normalized();
            BigDecimal bw = new BigDecimal(w), bx = new BigDecimal(x), by = new BigDecimal(y), bz = new BigDecimal(z);
            BigDecimal n2 = bw.multiply(bw).add(bx.multiply(bx)).add(by.multiply(by)).add(bz.multiply(bz));
            if (n2.signum() == 0) { if (!q.equals(Quat.IDENTITY)) track(Double.POSITIVE_INFINITY, "zero"); continue; }
            BigDecimal n = n2.sqrt(MC);
            double err = Math.max(Math.max(Math.abs(q.w() - bw.divide(n, MC).doubleValue()), Math.abs(q.x() - bx.divide(n, MC).doubleValue())),
                    Math.max(Math.abs(q.y() - by.divide(n, MC).doubleValue()), Math.abs(q.z() - bz.divide(n, MC).doubleValue())));
            track(err, q.toString());
        }
        report("Quat.normalized vs BigDecimal");

        // 3. Frame factories, extreme + near-threshold hints
        for (int i = 0; i < 300000; i++) {
            Vec3 a = r.nextInt(3) == 0 ? new Vec3(extreme(r), extreme(r), extreme(r)) : unitRand(r).mul(Math.pow(10, r.nextInt(-11, 300)));
            Vec3 h;
            int mode = r.nextInt(4);
            if (mode == 0) h = new Vec3(extreme(r), extreme(r), extreme(r));
            else {
                Vec3 au = a.normalizedOr(Vec3.Y);
                Vec3 p = au.cross(unitRand(r)).normalizedOr(Vec3.X);
                double eps = mode == 1 ? 1e-8 * r.nextDouble(0.5, 2.0) : Math.pow(10, -r.nextInt(1, 17));
                h = au.add(p.mul(eps)).mul(r.nextBoolean() ? 1 : -1e5);
            }
            double e = 0;
            e = Math.max(e, frameErr(Frame.alignY(a, h)));
            e = Math.max(e, frameErr(Frame.alignZ(a, h)));
            e = Math.max(e, frameErr(Frame.fromHeading(a, h)));
            Frame prev = Frame.alignY(unitRand(r), unitRand(r));
            e = Math.max(e, frameErr(Frame.transportY(prev, a)));
            track(e, "a=" + a + " h=" + h);
        }
        report("Frame factories orthonormality (all)");

        // 3b. alignY x-axis must be the normalized perpendicular component of the hint (when not falling back)
        for (int i = 0; i < 200000; i++) {
            Vec3 y = unitRand(r);
            Vec3 p = y.cross(unitRand(r)).normalizedOr(Vec3.X);
            double eps = Math.pow(10, -r.nextDouble(0, 7.9));
            Vec3 hint = y.mul(r.nextDouble(-1, 1)).add(p.mul(eps));
            Frame f = Frame.alignY(y, hint);
            // reference: exact perpendicular of hint, via BigDecimal Gram-Schmidt
            BigDecimal[] Y = {new BigDecimal(f.y().x()), new BigDecimal(f.y().y()), new BigDecimal(f.y().z())};
            BigDecimal[] H = {new BigDecimal(hint.x()), new BigDecimal(hint.y()), new BigDecimal(hint.z())};
            BigDecimal d = Y[0].multiply(H[0]).add(Y[1].multiply(H[1])).add(Y[2].multiply(H[2]));
            BigDecimal yy = Y[0].multiply(Y[0]).add(Y[1].multiply(Y[1])).add(Y[2].multiply(Y[2]));
            double[] P = new double[3];
            for (int k = 0; k < 3; k++) P[k] = H[k].subtract(Y[k].multiply(d).divide(yy, MC)).doubleValue();
            double[] u = exactUnit(P[0], P[1], P[2]);
            track(Math.max(Math.abs(f.x().x()-u[0]), Math.max(Math.abs(f.x().y()-u[1]), Math.abs(f.x().z()-u[2]))) / (1e-16 / Math.min(1, eps)),
                  "eps=" + eps + " (err in units of 1e-16/eps)");
        }
        report("alignY x direction error / conditioning");

        // 4. shortestArc: accuracy, minimality, unit norm, antiparallel sweep
        double[] offs = {1e-1, 1e-3, 1e-6, 2e-7, 1e-7, 3e-8, 1e-8, 3e-9, 1e-9, 5e-10, 2e-10, 1.01e-10, 0.99e-10, 1e-11, 1e-12, 0};
        for (double off : offs) {
            for (int i = 0; i < 20000; i++) {
                Vec3 a = unitRand(r);
                Vec3 perp = a.cross(unitRand(r)).normalizedOr(Vec3.X);
                // b at angle pi - off from a, exactly constructed
                Vec3 b = a.mul(-Math.cos(off)).add(perp.mul(Math.sin(off)));
                Quat q = Quat.shortestArc(a.mul(r.nextDouble(1e-6, 1e6)), b.mul(r.nextDouble(1e-6, 1e6)));
                double unit = Math.abs(Math.sqrt(q.dot(q)) - 1);
                Vec3 img = q.rotate(a);
                double err = img.sub(b).length();
                double ang = 2 * Math.atan2(Math.sqrt(q.x()*q.x()+q.y()*q.y()+q.z()*q.z()), Math.abs(q.w()));
                double minimalErr = Math.abs(ang - (Math.PI - off));
                track(Math.max(Math.max(err, unit), minimalErr), "off=" + off);
            }
            report("shortestArc antiparallel offset " + off);
        }

        // 5. angleTo accuracy across range incl. beyond pi-1e-6
        double[] angs = {1e-12, 1e-9, 1e-6, 1e-3, 1, 3, Math.PI - 1e-3, Math.PI - 1e-6, Math.PI - 1e-8, Math.PI};
        for (double ang : angs) {
            for (int i = 0; i < 20000; i++) {
                Quat a = quatRand(r);
                Vec3 ax = unitRand(r);
                Quat d = Quat.axisAngle(ax, ang);
                Quat b = a.mul(d);
                if (r.nextBoolean()) b = new Quat(-b.w(), -b.x(), -b.y(), -b.z());
                track(Math.abs(a.angleTo(b) - ang), "ang=" + ang);
            }
            report("angleTo angle " + ang);
        }

        // 6. slerp extrapolation vs analytic reference (rotation angle t*phi about fixed axis)
        double[] ts = {-1000, -37.5, -1, -0.25, 0, 0.3, 1, 1.75, 10, 999.9, 1000};
        double[] phis = {2e-8 * 0.5, 1e-8 * 1.5, 1e-6, 0.3, 2.0, Math.PI - 1e-6, Math.PI - 1e-12};
        for (double phi : phis) {
            for (double t : ts) {
                for (int i = 0; i < 2000; i++) {
                    Quat a = quatRand(r);
                    Vec3 ax = unitRand(r);
                    Quat b = a.mul(Quat.axisAngle(ax, phi));
                    if (r.nextBoolean()) b = new Quat(-b.w(), -b.x(), -b.y(), -b.z());
                    Quat s = Quat.slerp(a, b, t);
                    Vec3 v = unitRand(r);
                    // reference: a then rotation by t*phi about ax in a's local frame: a * axisAngle(ax, t*phi)
                    Vec3 ref = a.rotate(rod(ax, t * phi, v));
                    double err = s.rotate(v).sub(ref).length();
                    double unit = Math.abs(Math.sqrt(s.dot(s)) - 1);
                    track(Math.max(err, unit), "phi=" + phi + " t=" + t);
                }
            }
            report("slerp action phi=" + phi);
        }

        // 7. nlerp: equals normalized blend with shortest sign; check extrapolation finiteness near orthogonal
        for (int i = 0; i < 100000; i++) {
            Quat a = quatRand(r), b = quatRand(r);
            double t = r.nextDouble(-1000, 1000);
            Quat n = Quat.nlerp(a, b, t);
            Quat bb = a.dot(b) < 0 ? new Quat(-b.w(), -b.x(), -b.y(), -b.z()) : b;
            double w = (1-t)*a.w()+t*bb.w(), x=(1-t)*a.x()+t*bb.x(), y=(1-t)*a.y()+t*bb.y(), z=(1-t)*a.z()+t*bb.z();
            double l = Math.sqrt(w*w+x*x+y*y+z*z);
            track(Math.max(Math.max(Math.abs(n.w()-w/l), Math.abs(n.x()-x/l)), Math.max(Math.abs(n.y()-y/l), Math.abs(n.z()-z/l))), "t=" + t);
        }
        report("nlerp vs independent formula");

        // 8. fromFrame round trip incl. 180 deg and near-180, branch boundaries
        for (int i = 0; i < 200000; i++) {
            Quat q;
            int m = r.nextInt(4);
            if (m == 0) q = quatRand(r);
            else if (m == 1) q = Quat.axisAngle(unitRand(r), Math.PI - Math.pow(10, -r.nextInt(1, 16)));
            else if (m == 2) q = Quat.axisAngle(unitRand(r), Math.PI);
            else { Vec3 ax = new Vec3(r.nextInt(-1, 2), r.nextInt(-1, 2), r.nextInt(-1, 2)); if (ax.lengthSquared() == 0) ax = Vec3.X; q = Quat.axisAngle(ax, r.nextInt(0, 8) * Math.PI / 4 * (r.nextBoolean() ? 1 : 2)); }
            Frame f = q.toFrame();
            Quat back = Quat.fromFrame(f);
            double e = Math.max(back.rotate(Vec3.X).sub(f.x()).length(), Math.max(back.rotate(Vec3.Y).sub(f.y()).length(), back.rotate(Vec3.Z).sub(f.z()).length()));
            e = Math.max(e, Math.abs(Math.sqrt(back.dot(back)) - 1));
            track(e, "mode=" + m + " q=" + q);
        }
        report("fromFrame(toFrame(q)) action");

        // 9. Pose near the world border: then/inverse/toLocal round trips
        for (int i = 0; i < 200000; i++) {
            double B = 3.0e7;
            Pose a = new Pose(new Vec3(r.nextDouble(-B, B), r.nextDouble(-64, 320), r.nextDouble(-B, B)), quatRand(r), Math.pow(10, r.nextDouble(-3, 3)));
            Pose b = new Pose(unitRand(r).mul(r.nextDouble(0, 100)), quatRand(r), Math.pow(10, r.nextDouble(-3, 3)));
            Vec3 p = unitRand(r).mul(r.nextDouble(0, 50));
            Vec3 w1 = a.then(b).toWorld(p), w2 = a.toWorld(b.toWorld(p));
            double e1 = w1.sub(w2).length();
            Vec3 back = a.inverse().toWorld(a.toWorld(p));
            double e2 = back.sub(p).length() * a.scale();          // error in world units
            Vec3 loc = a.toLocal(a.toWorld(p));
            double e3 = loc.sub(p).length() * a.scale();
            track(Math.max(e1, Math.max(e2, e3)), "a=" + a);
        }
        report("Pose at |t|<=3e7: then/inverse/toLocal (world units)");

        // 10. transportY long walk: stays valid, y exact
        Frame f = Frame.WORLD;
        Vec3 y = Vec3.Y;
        double e = 0;
        for (int i = 0; i < 1_000_000; i++) {
            y = rod(unitRand(r), r.nextDouble(-0.2, 0.2), y);
            if (i % 1000 == 0) y = y.negate(); // antiparallel jumps
            Frame g = Frame.transportY(f, y);
            Vec3 yn = y.normalizedOr(null);
            if (!g.y().equals(yn)) e = Double.POSITIVE_INFINITY;
            e = Math.max(e, frameErr(g));
            f = g;
        }
        track(e, "walk"); report("transportY 1e6-step walk (incl. 1000 antiparallel flips)");

        // 11. Quat.rotate intermediate overflow with representable result
        Quat half = Quat.axisAngle(Vec3.Z, Math.PI);
        Vec3 big = new Vec3(Double.MAX_VALUE, 0, 0);
        System.out.println("halfturnZ.rotate(MAX,0,0) = " + half.rotate(big) + "   (exact: (-MAX,~0,0))");
        try { System.out.println("Pose.inverse: " + new Pose(big, half, 1).inverse()); }
        catch (ArithmeticException ex) { System.out.println("Pose(t=(MAX,0,0), R=180degZ, s=1).inverse() threw " + ex.getMessage()); }
        // smallest |v| where it happens
        double lo = 1e300, hi = Double.MAX_VALUE;
        for (int k = 0; k < 200; k++) { double mid = lo + (hi - lo) / 2; if (half.rotate(new Vec3(mid, 0, 0)).isFinite()) lo = mid; else hi = mid; }
        System.out.println("rotate overflow threshold for half turn: |v| > " + lo + "  (MAX/2 = " + Double.MAX_VALUE / 2 + ")");

        // 12. rotate with near-unit quaternion |q| = 1 + 1e-12 (inside rotate domain) vs 1e-13 accuracy row
        Quat qn = new Quat(1 + 1e-12, 0, 0, 0);
        Vec3 v1 = new Vec3(1e100, 0, 0);
        System.out.println("rotate with |q|=1+1e-12 on (1e100,0,0) relative error vs exact rotation: " + (qn.rotate(new Vec3(0, 1, 0)).sub(new Vec3(0,1,0)).length()));
        Quat qa = new Quat(Math.cos(0.5) * (1 + 1e-12), Math.sin(0.5) * (1 + 1e-12), 0, 0);
        System.out.println("rotate(Y) with 1 rad quat scaled by 1+1e-12: |result|-1 = " + (qa.rotate(Vec3.Y).length() - 1));

        // 13. Quat.inverse fallback identity sign
        Quat inv = new Quat(Double.NaN, 0, 0, 0).inverse();
        System.out.println("NaN.inverse() = " + inv + " equals(IDENTITY)=" + inv.equals(Quat.IDENTITY));

        // 14. null operands
        String[] names = {"axisAngle(null,1)", "alignY(null,X)", "alignZ(null,X)", "fromHeading(null,Y)", "transportY(WORLD,null)", "shortestArc(null,X)", "fromFrame(null)"};
        Runnable[] calls = {() -> Quat.axisAngle(null, 1), () -> Frame.alignY(null, Vec3.X), () -> Frame.alignZ(null, Vec3.X),
                () -> Frame.fromHeading(null, Vec3.Y), () -> Frame.transportY(Frame.WORLD, null), () -> Quat.shortestArc(null, Vec3.X), () -> Quat.fromFrame(null)};
        for (int k = 0; k < calls.length; k++) {
            try { calls[k].run(); System.out.println(names[k] + " -> returned"); }
            catch (RuntimeException ex) { System.out.println(names[k] + " -> " + ex.getClass().getSimpleName()); }
        }

        // 15. lerp(a, a, t) at large t
        Vec3 one = new Vec3(1, 1, 1);
        System.out.println("(1,1,1).lerp((1,1,1), 1e100) = " + one.lerp(one, 1e100) + ", t=1e16 -> " + one.lerp(one, 1e16));
        System.out.println("(1,1,1).lerp((1,1,1), 1e3) = " + one.lerp(one, 1e3) + "; (0.1,..).lerp(same, 0.7) == ? " + new Vec3(0.1,0.1,0.1).lerp(new Vec3(0.1,0.1,0.1), 0.7));

        // 16. fromHeading parallel threshold ambiguity: raw vs normalized cross magnitude
        Vec3 fwd = new Vec3(0, 1e-3, 0).add(new Vec3(1e-9, 0, 0));
        Frame fh = Frame.fromHeading(fwd, Vec3.Y);
        System.out.println("raw |forward x up| = " + fwd.cross(Vec3.Y).length() + ", normalized = " + fwd.normalizedOr(null).cross(Vec3.Y).length()
                + " -> fromHeading y = " + fh.y());

        // 17. Pose.inverse of 1/MAX scale
        try { System.out.println("Pose(scale=1/MAX).inverse().scale = " + new Pose(Vec3.ZERO, Quat.IDENTITY, 1 / Double.MAX_VALUE).inverse().scale()); }
        catch (ArithmeticException ex) { System.out.println("Pose(scale=1/MAX).inverse() threw: " + ex.getMessage()); }
    }
}
