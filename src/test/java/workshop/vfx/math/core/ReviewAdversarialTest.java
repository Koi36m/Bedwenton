package workshop.vfx.math.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static workshop.vfx.math.core.TestSupport.angleBetween;
import static workshop.vfx.math.core.TestSupport.assertFrameValid;
import static workshop.vfx.math.core.TestSupport.assertVec;
import static workshop.vfx.math.core.TestSupport.randomRotation;
import static workshop.vfx.math.core.TestSupport.randomUnit;
import static workshop.vfx.math.core.TestSupport.rodrigues;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Independent adversarial review of M1 (Claude Code), written against contract revision 3 only.
 *
 * <p>Every assertion here follows from an unambiguous clause of IMPLEMENTATION-CONTRACT.md (revision 3); the
 * clause is named in each display name. Tolerances are the contract's own figures (1e-12 unless a clause states
 * another). Two tolerances are derived rather than quoted, and justified inline: the conditioning bound for the
 * alignY x axis, and the Pose round trips at the world border, which are measured in world units (local error
 * times scale). References are independent of the production code: exact BigDecimal
 * arithmetic for normalization and Gram-Schmidt, Rodrigues' formula for rotations, analytic great-circle
 * positions for slerp. The supplied 97 tests are not modified or relied on.
 */
@Tag("review-adversarial")
class ReviewAdversarialTest {
    private static final long SEED = 0x5EED_2026_1015L;
    private static final MathContext MC = new MathContext(60);

    // ------------------------------------------------------------ independent references

    /** Exact unit vector of (x, y, z) computed in 60-digit decimal arithmetic, then rounded once. */
    private static Vec3 exactUnit(Vec3 v) {
        BigDecimal x = new BigDecimal(v.x()), y = new BigDecimal(v.y()), z = new BigDecimal(v.z());
        BigDecimal n = x.multiply(x).add(y.multiply(y)).add(z.multiply(z)).sqrt(MC);
        return new Vec3(x.divide(n, MC).doubleValue(), y.divide(n, MC).doubleValue(), z.divide(n, MC).doubleValue());
    }

    /** Exact component of `hint` perpendicular to unit `axis` (60 digits), normalized, rounded once. */
    private static Vec3 exactPerpendicularUnit(Vec3 axis, Vec3 hint) {
        BigDecimal[] a = {new BigDecimal(axis.x()), new BigDecimal(axis.y()), new BigDecimal(axis.z())};
        BigDecimal[] h = {new BigDecimal(hint.x()), new BigDecimal(hint.y()), new BigDecimal(hint.z())};
        BigDecimal ah = a[0].multiply(h[0]).add(a[1].multiply(h[1])).add(a[2].multiply(h[2]));
        BigDecimal aa = a[0].multiply(a[0]).add(a[1].multiply(a[1])).add(a[2].multiply(a[2]));
        BigDecimal k = ah.divide(aa, MC);
        BigDecimal[] p = new BigDecimal[3];
        for (int i = 0; i < 3; i++) {
            p[i] = h[i].subtract(a[i].multiply(k));
        }
        BigDecimal n = p[0].multiply(p[0]).add(p[1].multiply(p[1])).add(p[2].multiply(p[2])).sqrt(MC);
        return new Vec3(p[0].divide(n, MC).doubleValue(), p[1].divide(n, MC).doubleValue(), p[2].divide(n, MC).doubleValue());
    }

    private static double maxAbsDiff(Vec3 a, Vec3 b) {
        return Math.max(Math.abs(a.x() - b.x()), Math.max(Math.abs(a.y() - b.y()), Math.abs(a.z() - b.z())));
    }

    private static double norm(Quat q) {
        return Math.sqrt(q.w() * q.w() + q.x() * q.x() + q.y() * q.y() + q.z() * q.z());
    }

    private static Quat neg(Quat q) {
        return new Quat(-q.w(), -q.x(), -q.y(), -q.z());
    }

    /** A value drawn from the hard cases of IEEE-754 binary64, with random sign. */
    private static double extreme(SplittableRandom r) {
        double[] m = {0.0, Double.MIN_VALUE, 1e-320, Double.MIN_NORMAL, 1e-300, 1e-13, 1e-12, 1.0000001e-12, 1e-11,
                1e-8, 1.0, 3.0e7, 1e100, 1.3e154, 1e200, 1e300, Double.MAX_VALUE};
        double v = m[r.nextInt(m.length)];
        if (r.nextBoolean() && v != Double.MAX_VALUE) {
            v *= r.nextDouble(0.5, 1.0);
        }
        return r.nextBoolean() ? v : -v;
    }

    private static double special(SplittableRandom r) {
        switch (r.nextInt(8)) {
            case 0: return Double.NaN;
            case 1: return Double.POSITIVE_INFINITY;
            case 2: return Double.NEGATIVE_INFINITY;
            case 3: return -0.0;
            default: return extreme(r);
        }
    }

    // ------------------------------------------------------------ environment

    @Test
    @DisplayName("Environment: the test JVM is Java 25 (contract header: Java 25)")
    void runsOnJava25() {
        assertEquals(25, Runtime.version().feature(), "tests must run on Java 25, found " + Runtime.version());
    }

    // ------------------------------------------------------------ Vec3 / Quat normalization (sections 2 and 3)

    @Test
    @DisplayName("normalizedOr: 1e-12 of the exact unit vector for 200,000 extreme finite inputs; same fallback reference below LENGTH_EPS (section 2)")
    void normalizedOrAgainstExactReference() {
        SplittableRandom r = new SplittableRandom(SEED);
        Vec3 fallback = new Vec3(Double.NaN, 2, -3);
        double worst = 0;
        for (int i = 0; i < 200_000; i++) {
            Vec3 v = new Vec3(extreme(r), extreme(r), extreme(r));
            Vec3 n = v.normalizedOr(fallback);
            double m = Math.max(Math.abs(v.x()), Math.max(Math.abs(v.y()), Math.abs(v.z())));
            if (m < MathUtil.LENGTH_EPS) {
                assertSame(fallback, n, "fallback reference for " + v);
                continue;
            }
            double err = maxAbsDiff(exactUnit(v), n);
            worst = Math.max(worst, err);
            if (err > 1e-12) {
                fail("normalizedOr(" + v + ") = " + n + ", error " + err);
            }
        }
        System.out.println("[M1 review] normalizedOr worst component error vs exact: " + worst);
    }

    @Test
    @DisplayName("Quat.normalized: 1e-12 of the exact unit quaternion for 100,000 extreme inputs; exact IDENTITY for zero (section 3)")
    void quatNormalizedAgainstExactReference() {
        SplittableRandom r = new SplittableRandom(SEED + 1);
        for (int i = 0; i < 100_000; i++) {
            double w = extreme(r), x = extreme(r), y = extreme(r), z = extreme(r);
            Quat q = new Quat(w, x, y, z).normalized();
            BigDecimal bw = new BigDecimal(w), bx = new BigDecimal(x), by = new BigDecimal(y), bz = new BigDecimal(z);
            BigDecimal n2 = bw.multiply(bw).add(bx.multiply(bx)).add(by.multiply(by)).add(bz.multiply(bz));
            if (n2.signum() == 0) {
                assertSame(Quat.IDENTITY, q, "zero quaternion");
                continue;
            }
            BigDecimal n = n2.sqrt(MC);
            double err = Math.max(Math.max(Math.abs(q.w() - bw.divide(n, MC).doubleValue()), Math.abs(q.x() - bx.divide(n, MC).doubleValue())),
                    Math.max(Math.abs(q.y() - by.divide(n, MC).doubleValue()), Math.abs(q.z() - bz.divide(n, MC).doubleValue())));
            if (err > 1e-12) {
                fail("normalized(" + w + ", " + x + ", " + y + ", " + z + ") = " + q + ", error " + err);
            }
        }
    }

    // ------------------------------------------------------------ Tier 1 / Tier 2 totality fuzz (section 0A)

    @Test
    @DisplayName("Tier 1 never throws and Tier 2 is total: NaN, infinities, -0.0, subnormals and MAX_VALUE in every operand (section 0A)")
    void tierTotalityFuzz() {
        SplittableRandom r = new SplittableRandom(SEED + 2);
        for (int i = 0; i < 100_000; i++) {
            Vec3 a = new Vec3(special(r), special(r), special(r));
            Vec3 b = new Vec3(special(r), special(r), special(r));
            Quat q = new Quat(special(r), special(r), special(r), special(r));
            Quat p = new Quat(special(r), special(r), special(r), special(r));
            double s = special(r);
            try {
                // Tier 1: plain IEEE, must not throw
                a.add(b); a.sub(b); a.mul(s); a.negate(); a.dot(b); a.cross(b);
                a.lengthSquared(); a.length(); a.distance(b); a.lerp(b, s);
                q.mul(p); q.conjugate(); q.dot(p); q.rotate(a);
                Frame.WORLD.toWorld(a); Frame.WORLD.toLocal(a);
                Pose.IDENTITY.toWorld(a); Pose.IDENTITY.toLocal(a); Pose.IDENTITY.directionToWorld(a);
                // Tier 2: valid results for every operand value
                Vec3 n = a.normalizedOr(Vec3.Y);
                assertTrue(n == Vec3.Y || Math.abs(n.length() - 1) <= 1e-12, "normalizedOr " + a);
                assertUnit(q.normalized(), "normalized " + q);
                assertUnit(q.inverse(), "inverse " + q);
                assertUnit(Quat.shortestArc(a, b), "shortestArc " + a + " " + b);
                assertFrameValid(Frame.alignY(a, b), 1e-12, "alignY " + a + " " + b);
                assertFrameValid(Frame.alignZ(a, b), 1e-12, "alignZ " + a + " " + b);
                assertFrameValid(Frame.fromHeading(a, b), 1e-12, "fromHeading " + a + " " + b);
                Frame prev = Frame.alignY(randomUnit(r), randomUnit(r));
                assertFrameValid(Frame.transportY(prev, a), 1e-12, "transportY " + a);
                Frame f = Frame.alignZ(randomUnit(r), randomUnit(r));
                assertUnit(Quat.fromFrame(f), "fromFrame");
                if (!Double.isNaN(s)) {
                    double lo = Math.min(s, 0), hi = Math.max(s, 0);
                    double c = MathUtil.clamp(special(r), lo, hi);
                    assertTrue(c >= lo && c <= hi, "clamp in range");
                }
            } catch (RuntimeException e) {
                fail("threw " + e + " for a=" + a + " b=" + b + " q=" + q + " p=" + p + " s=" + s);
            }
        }
    }

    private static void assertUnit(Quat q, String label) {
        assertTrue(Double.isFinite(q.w()) && Double.isFinite(q.x()) && Double.isFinite(q.y()) && Double.isFinite(q.z()), label + " finite: " + q);
        assertEquals(1.0, norm(q), 1e-12, label + " unit: " + q);
    }

    // ------------------------------------------------------------ Frame factories (section 4)

    @Test
    @DisplayName("Frame factories: valid at 1e-12 for extreme magnitudes and hints within a factor 2 of the 1e-8 threshold (section 4)")
    void frameFactoriesAdversarial() {
        SplittableRandom r = new SplittableRandom(SEED + 3);
        for (int i = 0; i < 100_000; i++) {
            Vec3 axis = r.nextInt(3) == 0
                    ? new Vec3(extreme(r), extreme(r), extreme(r))
                    : randomUnit(r).mul(Math.pow(10, r.nextInt(-11, 300)));
            Vec3 unitAxis = axis.normalizedOr(Vec3.Y);
            Vec3 perp = unitAxis.cross(randomUnit(r)).normalizedOr(Vec3.X);
            double eps = r.nextBoolean() ? 1e-8 * r.nextDouble(0.5, 2.0) : Math.pow(10, -r.nextInt(1, 17));
            Vec3 hint = unitAxis.add(perp.mul(eps)).mul(r.nextBoolean() ? 1.0 : -1e5);
            String label = "axis=" + axis + " hint=" + hint;
            assertFrameValid(Frame.alignY(axis, hint), 1e-12, "alignY " + label);
            assertFrameValid(Frame.alignZ(axis, hint), 1e-12, "alignZ " + label);
            assertFrameValid(Frame.fromHeading(axis, hint), 1e-12, "fromHeading " + label);
            assertFrameValid(Frame.transportY(Frame.alignY(randomUnit(r), randomUnit(r)), axis), 1e-12, "transportY " + label);
        }
    }

    @Test
    @DisplayName("alignY: x is the normalized perpendicular component of the hint, to within its conditioning (section 4)")
    void alignYUsesPerpendicularComponentOfHint() {
        SplittableRandom r = new SplittableRandom(SEED + 4);
        for (int i = 0; i < 50_000; i++) {
            Vec3 y = randomUnit(r).mul(Math.pow(10, r.nextInt(-6, 6)));
            Vec3 p = y.cross(randomUnit(r)).normalizedOr(Vec3.X);
            double eps = Math.pow(10, -r.nextDouble(0, 7.5)); // perpendicular fraction, above the 1e-8 threshold
            Vec3 hint = y.normalizedOr(Vec3.Y).mul(r.nextDouble(-1, 1)).add(p.mul(eps)).mul(Math.pow(10, r.nextInt(-3, 4)));
            Frame f = Frame.alignY(y, hint);
            assertVec(exactUnit(y), f.y(), 1e-12, "y axis");
            // the direction of a perpendicular component of relative size eps is determined to about 1e-16 / eps
            Vec3 expected = exactPerpendicularUnit(f.y(), hint);
            double tol = 1e-12 + 1e-14 / eps; // 1e-14 / eps: 100x headroom over the measured 2.2e-16 / eps
            assertVec(expected, f.x(), tol, "x axis, hint " + hint + ", eps " + eps);
            assertVec(f.x().cross(f.y()), f.z(), 1e-12, "z = x cross y");
        }
    }

    @Test
    @DisplayName("fromHeading equals the contract formula right = n(f x u), up = right x f, back = -f for well-conditioned input (section 4)")
    void fromHeadingMatchesContractFormula() {
        SplittableRandom r = new SplittableRandom(SEED + 5);
        for (int i = 0; i < 50_000; i++) {
            Vec3 f = randomUnit(r);
            Vec3 u = randomUnit(r);
            if (f.cross(u).length() < 1e-3) {
                continue;
            }
            Vec3 forward = f.mul(Math.pow(10, r.nextInt(-6, 6)));
            Vec3 up = u.mul(Math.pow(10, r.nextInt(-6, 6)));
            Vec3 fn = exactUnit(forward);
            Vec3 right = exactUnit(fn.cross(exactUnit(up)));
            Vec3 upAxis = right.cross(fn);
            Frame frame = Frame.fromHeading(forward, up);
            assertVec(right, frame.x(), 1e-12, "right");
            assertVec(upAxis, frame.y(), 1e-12, "up");
            assertVec(fn.negate(), frame.z(), 1e-12, "back");
            assertVec(frame.z(), frame.x().cross(frame.y()), 1e-12, "right x up = back");
        }
    }

    @Test
    @DisplayName("transportY: 200,000-step walk with an antiparallel flip every 1,000 steps stays valid; y within 1e-12 of normalize(newY) (section 4)")
    void transportYLongWalk() {
        SplittableRandom r = new SplittableRandom(SEED + 6);
        Frame f = Frame.WORLD;
        Vec3 dir = Vec3.Y;
        for (int i = 0; i < 200_000; i++) {
            dir = rodrigues(randomUnit(r), r.nextDouble(-0.3, 0.3), dir);
            if (i % 1000 == 0) {
                dir = dir.negate();
            }
            Vec3 y = dir.mul(Math.pow(10, r.nextInt(-11, 300))); // the input length must not matter
            Frame g = Frame.transportY(f, y);
            assertFrameValid(g, 1e-12, "step " + i);
            assertVec(exactUnit(y), g.y(), 1e-12, "step " + i + " y");
            f = g;
        }
    }

    // ------------------------------------------------------------ shortestArc (section 3)

    @Test
    @DisplayName("shortestArc near antiparallel: contract accuracy table, minimal angle, unit norm, half-turn axis rule (section 3)")
    void shortestArcNearAntiparallel() {
        SplittableRandom r = new SplittableRandom(SEED + 7);
        // offset from antiparallel -> allowed error of q.rotate(a) against b (contract table, QuatTest.shortestArcDegenerate)
        double[][] table = {{1e-1, 1e-9}, {1e-3, 1e-9}, {1e-6, 1e-9}, {1e-7, 1e-8}, {1e-9, 1e-6}};
        for (double[] row : table) {
            double off = row[0], tol = row[1];
            double worst = 0;
            for (int i = 0; i < 20_000; i++) {
                Vec3 a = randomUnit(r);
                Vec3 perp = a.cross(randomUnit(r)).normalizedOr(Vec3.X);
                Vec3 b = a.mul(-Math.cos(off)).add(perp.mul(Math.sin(off)));
                Quat q = Quat.shortestArc(a.mul(r.nextDouble(1e-6, 1e6)), b.mul(r.nextDouble(1e-6, 1e6)));
                assertEquals(1.0, norm(q), 1e-12, "unit");
                double err = q.rotate(a).sub(b).length();
                worst = Math.max(worst, err);
                assertTrue(err <= tol, "offset " + off + ": error " + err + " > " + tol);
                // minimal: the rotation angle equals the angle between the vectors
                double angle = 2 * Math.atan2(Math.sqrt(q.x() * q.x() + q.y() * q.y() + q.z() * q.z()), Math.abs(q.w()));
                assertEquals(Math.PI - off, angle, tol, "rotation angle at offset " + off);
            }
            System.out.println("[M1 review] shortestArc offset " + off + " worst error " + worst + " (allowed " + tol + ")");
        }
        // exactly antiparallel: half turn about unit(a x e), e = least-aligned world axis of a, ties X, Y, Z
        for (int i = 0; i < 20_000; i++) {
            Vec3 a = i < 8 ? new Vec3((i & 1) == 0 ? 1 : -1, (i & 2) == 0 ? 1 : -1, (i & 4) == 0 ? 1 : -1) : randomUnit(r);
            Vec3 au = exactUnit(a);
            double ax = Math.abs(au.x()), ay = Math.abs(au.y()), az = Math.abs(au.z());
            Vec3 e = ax <= ay && ax <= az ? Vec3.X : (ay <= az ? Vec3.Y : Vec3.Z);
            Vec3 axis = exactUnit(au.cross(e));
            Quat q = Quat.shortestArc(a, a.negate());
            assertEquals(0.0, q.w(), 0.0, "half turn has w = 0 for " + a);
            assertVec(axis, new Vec3(q.x(), q.y(), q.z()), 1e-12, "half-turn axis for " + a);
        }
    }

    // ------------------------------------------------------------ angleTo, slerp, nlerp (section 3)

    @Test
    @DisplayName("angleTo: 1e-12 absolute from 1e-9 to pi - 1e-6, sign-insensitive, both argument orders (section 3)")
    void angleToAccuracy() {
        SplittableRandom r = new SplittableRandom(SEED + 8);
        double[] angles = {1e-9, 3e-9, 1e-7, 1e-5, 1e-3, 0.1, 1, 2, 3, Math.PI - 1e-3, Math.PI - 1e-5, Math.PI - 1e-6};
        for (double angle : angles) {
            for (int i = 0; i < 10_000; i++) {
                Quat a = randomRotation(r);
                Quat b = a.mul(Quat.axisAngle(randomUnit(r), angle));
                if (r.nextBoolean()) {
                    b = neg(b);
                }
                assertEquals(angle, a.angleTo(b), 1e-12, "angleTo at " + angle);
                assertEquals(angle, b.angleTo(a), 1e-12, "angleTo (reversed) at " + angle);
            }
        }
    }

    @Test
    @DisplayName("slerp: action equals the analytic great-circle position for |t| <= 1e3 to 1e-9, incl. phi near 1e-8 and near pi (section 3, R3 domain)")
    void slerpExtrapolationAgainstAnalyticGreatCircle() {
        SplittableRandom r = new SplittableRandom(SEED + 9);
        double[] ts = {-1000, -999.9, -37.5, -1, -0.25, 0, 0.3, 0.5, 1, 1.75, 10, 999.9, 1000};
        double[] phis = {5e-9, 1e-8, 2.000001e-8, 1e-6, 0.3, 2.0, Math.PI - 1e-6, Math.PI - 1e-12};
        double worst = 0;
        for (double phi : phis) {
            for (double t : ts) {
                for (int i = 0; i < 300; i++) {
                    Quat a = randomRotation(r);
                    Vec3 axis = randomUnit(r);
                    Quat b = a.mul(Quat.axisAngle(axis, phi));
                    if (r.nextBoolean()) {
                        b = neg(b); // slerp must take the shortest path regardless of sign
                    }
                    Quat s = Quat.slerp(a, b, t);
                    assertEquals(1.0, norm(s), 1e-12, "unit result");
                    Vec3 v = randomUnit(r);
                    Vec3 expected = a.rotate(rodrigues(axis, t * phi, v));
                    double err = s.rotate(v).sub(expected).length();
                    worst = Math.max(worst, err);
                    assertTrue(err <= 1e-9, "phi=" + phi + " t=" + t + " error " + err);
                }
            }
        }
        System.out.println("[M1 review] slerp |t|<=1e3 worst action error vs analytic: " + worst);
    }

    @Test
    @DisplayName("slerp: constant angular speed; slerp(a, b, t) and slerp(a, -b, t) are the same rotation (section 3)")
    void slerpConstantSpeedAndSignInvariance() {
        SplittableRandom r = new SplittableRandom(SEED + 10);
        for (int i = 0; i < 20_000; i++) {
            Quat a = randomRotation(r);
            double phi = r.nextDouble(1e-6, Math.PI - 1e-6);
            Quat b = a.mul(Quat.axisAngle(randomUnit(r), phi));
            double t = r.nextDouble(-1, 2);
            double expected = Math.abs(t) * phi;
            if (expected <= Math.PI - 1e-6) {
                assertEquals(expected, a.angleTo(Quat.slerp(a, b, t)), 1e-9, "angle travelled at t=" + t);
            }
            TestSupport.assertSameRotation(Quat.slerp(a, b, t), Quat.slerp(a, neg(b), t), 1e-12, "sign of b");
        }
    }

    @Test
    @DisplayName("nlerp: shortest-sign (1-t)a + tb normalized, including extrapolation to |t| = 1e3 (section 3)")
    void nlerpMatchesIndependentFormula() {
        SplittableRandom r = new SplittableRandom(SEED + 11);
        for (int i = 0; i < 50_000; i++) {
            Quat a = randomRotation(r), b = randomRotation(r);
            double t = r.nextDouble(-1000, 1000);
            Quat bb = a.dot(b) < 0 ? neg(b) : b;
            double w = (1 - t) * a.w() + t * bb.w(), x = (1 - t) * a.x() + t * bb.x();
            double y = (1 - t) * a.y() + t * bb.y(), z = (1 - t) * a.z() + t * bb.z();
            double l = Math.sqrt(w * w + x * x + y * y + z * z);
            Quat n = Quat.nlerp(a, b, t);
            assertEquals(w / l, n.w(), 1e-12, "w");
            assertEquals(x / l, n.x(), 1e-12, "x");
            assertEquals(y / l, n.y(), 1e-12, "y");
            assertEquals(z / l, n.z(), 1e-12, "z");
        }
    }

    // ------------------------------------------------------------ fromFrame (section 3)

    @Test
    @DisplayName("fromFrame(q.toFrame()) is q (as a rotation) for half turns about arbitrary and diagonal axes and near-half turns (section 3)")
    void fromFrameHalfTurnsAndNearHalfTurns() {
        SplittableRandom r = new SplittableRandom(SEED + 12);
        for (int i = 0; i < 100_000; i++) {
            Vec3 axis;
            double angle;
            switch (i % 4) {
                case 0:
                    axis = randomUnit(r);
                    angle = Math.PI;
                    break;
                case 1:
                    axis = randomUnit(r);
                    angle = Math.PI - Math.pow(10, -r.nextInt(1, 16));
                    break;
                case 2:
                    axis = new Vec3(r.nextInt(-1, 2), r.nextInt(-1, 2), r.nextInt(-1, 2));
                    if (axis.lengthSquared() == 0) {
                        axis = Vec3.Z;
                    }
                    angle = r.nextInt(-8, 9) * Math.PI / 4;
                    break;
                default:
                    axis = randomUnit(r);
                    angle = r.nextDouble(-Math.PI, Math.PI);
            }
            Quat q = Quat.axisAngle(axis, angle);
            Frame f = q.toFrame();
            Quat back = Quat.fromFrame(f);
            assertEquals(1.0, norm(back), 1e-12, "unit");
            assertVec(f.x(), back.rotate(Vec3.X), 1e-12, "image of X, axis " + axis + " angle " + angle);
            assertVec(f.y(), back.rotate(Vec3.Y), 1e-12, "image of Y, axis " + axis + " angle " + angle);
            assertVec(f.z(), back.rotate(Vec3.Z), 1e-12, "image of Z, axis " + axis + " angle " + angle);
        }
    }

    // ------------------------------------------------------------ Quat.rotate at the domain edge (sections 0A, 6)

    @Test
    @DisplayName("Quat.rotate of exactly-normalized quaternions matches Rodrigues to 1e-13 * |v| for |v| up to 1e100 (sections 0A, 6)")
    void rotateAtDomainEdge() {
        SplittableRandom r = new SplittableRandom(SEED + 13);
        for (int i = 0; i < 50_000; i++) {
            Vec3 axis = randomUnit(r);
            double angle = r.nextDouble(-Math.PI, Math.PI);
            Quat q = Quat.axisAngle(axis, angle);
            double mag = Math.pow(10, r.nextInt(-100, 101));
            Vec3 v = randomUnit(r).mul(mag);
            Vec3 expected = rodrigues(axis, angle, v);
            assertVec(expected, q.rotate(v), 1e-13 * mag, "rotate |v|=" + mag);
        }
    }

    // ------------------------------------------------------------ Pose (section 5)

    @Test
    @DisplayName("Pose near Minecraft's world border (|x|,|z| <= 3e7): composition 1e-7, inverse and toLocal round trips 1e-6 (section 6)")
    void poseNearWorldBorder() {
        SplittableRandom r = new SplittableRandom(SEED + 14);
        double border = 2.9999984e7; // vanilla world border radius
        for (int i = 0; i < 100_000; i++) {
            Pose a = new Pose(new Vec3(r.nextDouble(-border, border), r.nextDouble(-64, 320), r.nextDouble(-border, border)),
                    randomRotation(r), Math.pow(10, r.nextDouble(-3, 3)));
            Pose b = new Pose(randomUnit(r).mul(r.nextDouble(0, 100)), randomRotation(r), Math.pow(10, r.nextDouble(-3, 3)));
            Vec3 p = randomUnit(r).mul(r.nextDouble(0, 50));
            assertVec(a.toWorld(b.toWorld(p)), a.then(b).toWorld(p), 1e-7, "then at the border");
            Vec3 w = a.toWorld(p);
            // 1e-6 in world units: a local-space error e corresponds to e * scale blocks in the world
            assertVec(p, a.toLocal(w), 1e-6 / a.scale(), "toLocal round trip");
            assertVec(p, a.inverse().toWorld(w), 1e-6 / a.scale(), "inverse round trip");
            Pose id = a.then(a.inverse());
            assertVec(Vec3.ZERO, id.translation(), 1e-6, "a.then(a.inverse()) translation");
            assertEquals(1.0, id.scale(), 1e-12, "a.then(a.inverse()) scale");
            assertTrue(id.rotation().angleTo(Quat.IDENTITY) < 1e-12, "a.then(a.inverse()) rotation");
        }
    }

    @Test
    @DisplayName("Pose.then/inverse: inside the domain never throw; outside, only ArithmeticException, never IllegalArgumentException (section 5)")
    void poseExceptionTypesAcrossScaleRange() {
        SplittableRandom r = new SplittableRandom(SEED + 15);
        for (int i = 0; i < 50_000; i++) {
            double sa = Math.pow(10, r.nextDouble(-320, 308));
            double sb = Math.pow(10, r.nextDouble(-320, 308));
            if (!(sa > 0) || !(sb > 0) || !Double.isFinite(sa) || !Double.isFinite(sb)) {
                continue;
            }
            double ta = Math.pow(10, r.nextDouble(-10, 308));
            Pose a = new Pose(randomUnit(r).mul(ta), randomRotation(r), sa);
            Pose b = new Pose(randomUnit(r).mul(Math.pow(10, r.nextDouble(-10, 308))), randomRotation(r), sb);
            boolean inDomain = sa >= 1e-100 && sa <= 1e100 && sb >= 1e-100 && sb <= 1e100
                    && ta <= 1e100 && b.translation().length() <= 1e100;
            for (int op = 0; op < 2; op++) {
                try {
                    Pose c = op == 0 ? a.then(b) : a.inverse();
                    assertTrue(c.translation().isFinite() && c.scale() > 0 && Double.isFinite(c.scale()), "valid result");
                } catch (ArithmeticException e) {
                    if (inDomain) {
                        fail((op == 0 ? "then" : "inverse") + " threw inside the supported domain: " + a + " " + b);
                    }
                } catch (RuntimeException e) {
                    fail("wrong exception type " + e + " for " + a + " " + b);
                }
            }
        }
        // positive subnormal products are accepted, exact zero is rejected
        Pose tiny = new Pose(Vec3.ZERO, Quat.IDENTITY, 1e-160);
        assertEquals(1e-160 * 1e-160, tiny.then(tiny).scale(), 0.0, "subnormal scale accepted unchanged");
        assertThrows(ArithmeticException.class, () -> new Pose(Vec3.ZERO, Quat.IDENTITY, Double.MIN_VALUE).then(new Pose(Vec3.ZERO, Quat.IDENTITY, 0.25)));
    }

    // ------------------------------------------------------------ conventions cross-check (CONVENTIONS.md)

    @Test
    @DisplayName("Conventions: Euler helper equals Rodrigues X then Y then Z about fixed world axes for random angles (CONVENTIONS 3)")
    void eulerAgainstRodrigues() {
        SplittableRandom r = new SplittableRandom(SEED + 16);
        for (int i = 0; i < 20_000; i++) {
            double rx = r.nextDouble(-10, 10), ry = r.nextDouble(-10, 10), rz = r.nextDouble(-10, 10);
            Vec3 v = randomUnit(r).mul(r.nextDouble(0, 20));
            Vec3 expected = rodrigues(Vec3.Z, rz, rodrigues(Vec3.Y, ry, rodrigues(Vec3.X, rx, v)));
            assertVec(expected, Quat.fromEulerApplyXThenYThenZ(rx, ry, rz).rotate(v), 1e-12, "euler");
        }
        // right-hand rule spot check: +90 degrees about +Y sends +X to -Z (north)
        assertEquals(0.0, angleBetween(Quat.axisAngle(Vec3.Y, Math.PI / 2).rotate(Vec3.X), Vec3.Z.negate()), 1e-12);
    }
}
