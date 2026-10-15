package workshop.vfx.math.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static workshop.vfx.math.core.TestSupport.angleBetween;
import static workshop.vfx.math.core.TestSupport.assertFinite;
import static workshop.vfx.math.core.TestSupport.assertFrameValid;
import static workshop.vfx.math.core.TestSupport.assertSameRotation;
import static workshop.vfx.math.core.TestSupport.assertVec;
import static workshop.vfx.math.core.TestSupport.golden;
import static workshop.vfx.math.core.TestSupport.randomRotation;
import static workshop.vfx.math.core.TestSupport.randomUnit;
import static workshop.vfx.math.core.TestSupport.randomVec;
import static workshop.vfx.math.core.TestSupport.rodrigues;

import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuatTest {
    private static final long SEED = 20261014L;
    private static final double TOL = 1e-12;

    private static Vec3 vec(double[] row, int from) {
        return new Vec3(row[from], row[from + 1], row[from + 2]);
    }

    private static Quat q(double[] row, int from) {
        return new Quat(row[from], row[from + 1], row[from + 2], row[from + 3]);
    }

    private static Quat negated(Quat q) {
        return new Quat(-q.w(), -q.x(), -q.y(), -q.z());
    }

    private static double norm(Quat q) {
        return Math.sqrt(q.w() * q.w() + q.x() * q.x() + q.y() * q.y() + q.z() * q.z());
    }

    // ------------------------------------------------------------ golden (independent Python oracle)

    @Test
    @DisplayName("Golden: axisAngle(axis, angle).rotate(v) equals Rodrigues from the Python oracle (2,042 rows)")
    void goldenRotation() {
        List<double[]> rows = golden("rotation_cases.csv");
        assertTrue(rows.size() >= 2000, "unexpected golden row count " + rows.size());
        int n = 0;
        for (double[] row : rows) {
            Quat quat = Quat.axisAngle(vec(row, 0), row[3]);
            assertVec(vec(row, 7), quat.rotate(vec(row, 4)), TOL, "rotation row " + n);
            n++;
        }
    }

    @Test
    @DisplayName("Golden: qa.mul(qb).rotate(v) equals Rodrigues(a, Rodrigues(b, v)) (1,500 rows)")
    void goldenComposition() {
        List<double[]> rows = golden("compose_cases.csv");
        assertTrue(rows.size() >= 1500, "unexpected golden row count " + rows.size());
        int n = 0;
        for (double[] row : rows) {
            Quat qa = Quat.axisAngle(vec(row, 0), row[3]);
            Quat qb = Quat.axisAngle(vec(row, 4), row[7]);
            assertVec(vec(row, 11), qa.mul(qb).rotate(vec(row, 8)), TOL, "compose row " + n);
            n++;
        }
    }

    @Test
    @DisplayName("Golden: slerp action matches the oracle, also with q1 negated (shortest path)")
    void goldenSlerp() {
        List<double[]> rows = golden("slerp_cases.csv");
        assertTrue(rows.size() >= 1500, "unexpected golden row count " + rows.size());
        int n = 0;
        for (double[] row : rows) {
            Quat q0 = q(row, 0);
            Quat q1 = q(row, 4);
            double t = row[8];
            Vec3 v = vec(row, 9);
            Vec3 expected = vec(row, 12);
            assertVec(expected, Quat.slerp(q0, q1, t).rotate(v), 1e-11, "slerp row " + n);
            assertVec(expected, Quat.slerp(q0, negated(q1), t).rotate(v), 1e-11, "slerp (-q1) row " + n);
            n++;
        }
    }

    // ------------------------------------------------------------ construction and algebra

    @Test
    @DisplayName("axisAngle normalizes non-unit axes and rejects zero or non-finite input")
    void axisAngleValidation() {
        Quat unitAxis = Quat.axisAngle(new Vec3(0, 1, 0), 1.0);
        Quat longAxis = Quat.axisAngle(new Vec3(0, 40, 0), 1.0);
        assertSameRotation(unitAxis, longAxis, TOL, "non-unit axis");
        assertEquals(1.0, norm(longAxis), TOL);
        assertThrows(IllegalArgumentException.class, () -> Quat.axisAngle(new Vec3(0, 0, 0), 1.0));
        assertThrows(IllegalArgumentException.class, () -> Quat.axisAngle(new Vec3(1e-300, 0, 0), 1.0));
        assertThrows(IllegalArgumentException.class, () -> Quat.axisAngle(new Vec3(Double.NaN, 0, 1), 1.0));
        assertThrows(IllegalArgumentException.class, () -> Quat.axisAngle(new Vec3(0, 1, 0), Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Quat.axisAngle(new Vec3(0, 1, 0), Double.POSITIVE_INFINITY));
    }

    @Test
    @DisplayName("Identity, conjugate, inverse and normalization")
    void identityAndInverse() {
        assertEquals(1.0, Quat.IDENTITY.w(), 0.0);
        assertEquals(0.0, Quat.IDENTITY.x(), 0.0);
        SplittableRandom r = new SplittableRandom(SEED);
        for (int i = 0; i < 5_000; i++) {
            Quat a = randomRotation(r);
            Vec3 v = randomVec(r, 10);
            assertSameRotation(Quat.IDENTITY, a.mul(a.inverse()), TOL, "q * q^-1");
            assertSameRotation(Quat.IDENTITY, a.inverse().mul(a), TOL, "q^-1 * q");
            assertVec(v, a.inverse().rotate(a.rotate(v)), 1e-11, "inverse undoes rotate");
            // q and -q are the same rotation
            assertVec(a.rotate(v), negated(a).rotate(v), TOL, "q and -q");
            // normalized() of a scaled quaternion restores the unit quaternion
            Quat scaled = new Quat(a.w() * 7.5, a.x() * 7.5, a.y() * 7.5, a.z() * 7.5);
            assertEquals(1.0, norm(scaled.normalized()), TOL);
            assertSameRotation(a, scaled.normalized(), TOL, "normalized keeps the rotation");
            // conjugate of a unit quaternion equals its inverse
            assertSameRotation(a.conjugate(), a.inverse(), TOL, "conjugate = inverse for unit q");
        }
    }

    @Test
    @DisplayName("Quaternion multiplication is associative (as rotations) and composes rotations")
    void associativityAndComposition() {
        SplittableRandom r = new SplittableRandom(SEED + 1);
        for (int i = 0; i < 5_000; i++) {
            Quat a = randomRotation(r);
            Quat b = randomRotation(r);
            Quat c = randomRotation(r);
            Vec3 v = randomVec(r, 10);
            assertSameRotation(a.mul(b).mul(c), a.mul(b.mul(c)), 1e-12, "associativity");
            assertVec(a.rotate(b.rotate(v)), a.mul(b).rotate(v), 1e-11, "(a*b).rotate(v) = a.rotate(b.rotate(v))");
        }
    }

    @Test
    @DisplayName("Degenerate quaternions: zero and non-finite normalize to identity (M0: no NaN propagation)")
    void normalizeDegenerate() {
        assertSameRotation(Quat.IDENTITY, new Quat(0, 0, 0, 0).normalized(), 0.0, "zero");
        assertSameRotation(Quat.IDENTITY, new Quat(Double.NaN, 0, 0, 1).normalized(), 0.0, "NaN");
        assertSameRotation(Quat.IDENTITY, new Quat(1, Double.POSITIVE_INFINITY, 0, 0).normalized(), 0.0, "infinity");
        Quat huge = new Quat(1e200, 1e200, 0, 0).normalized();
        assertEquals(1.0, norm(huge), TOL);
        Quat tiny = new Quat(0, 0, 3e-200, 0).normalized();
        assertEquals(1.0, norm(tiny), TOL);
        assertFinite(Quat.IDENTITY.rotate(new Vec3(1, 2, 3)), "identity rotate");
    }

    // ------------------------------------------------------------ angleTo

    @Test
    @DisplayName("angleTo recovers the rotation angle, including tiny angles and near pi")
    void angleTo() {
        double[] angles = {0.0, 1e-9, 1e-6, 1e-3, 0.1, 1.0, 2.0, 3.0, Math.PI - 1e-6, Math.PI};
        Vec3 axis = new Vec3(1, 2, 3);
        for (double a : angles) {
            double got = Quat.IDENTITY.angleTo(Quat.axisAngle(axis, a));
            assertEquals(a, got, 1e-12, "angle " + a);
            double sym = Quat.axisAngle(axis, a).angleTo(Quat.IDENTITY);
            assertEquals(a, sym, 1e-12, "symmetric angle " + a);
        }
        Quat q = Quat.axisAngle(axis, 0.7);
        assertEquals(0.0, q.angleTo(negated(q)), 1e-12, "q and -q are the same rotation");
        // angle is taken between rotations, so it is at most pi even for an angle of 5 radians
        assertEquals(2 * Math.PI - 5.0, Quat.IDENTITY.angleTo(Quat.axisAngle(axis, 5.0)), 1e-12);
    }

    // ------------------------------------------------------------ shortestArc

    @Test
    @DisplayName("shortestArc maps from-direction onto to-direction for random pairs")
    void shortestArcRandom() {
        SplittableRandom r = new SplittableRandom(SEED + 2);
        for (int i = 0; i < 10_000; i++) {
            Vec3 a = randomUnit(r);
            Vec3 b = randomUnit(r);
            Quat arc = Quat.shortestArc(a, b);
            assertEquals(1.0, norm(arc), TOL);
            assertVec(b, arc.rotate(a), 1e-11, "arc maps a to b");
            // lengths of the inputs do not matter
            Quat scaledArc = Quat.shortestArc(a.mul(37.0), b.mul(0.01));
            assertVec(b, scaledArc.rotate(a), 1e-11, "arc ignores input length");
            // it is the SHORTEST arc: the rotation angle equals the angle between the vectors
            assertEquals(angleBetween(a, b), Quat.IDENTITY.angleTo(arc), 1e-9, "shortest angle");
        }
    }

    @Test
    @DisplayName("shortestArc: identical, antiparallel, near-antiparallel and zero inputs (M0: robustness)")
    void shortestArcDegenerate() {
        // identical
        Quat same = Quat.shortestArc(new Vec3(0, 1, 0), new Vec3(0, 1, 0));
        assertSameRotation(Quat.IDENTITY, same, 1e-12, "identical vectors");
        // exactly antiparallel along each axis and along a diagonal
        Vec3[] axes = {Vec3.X, Vec3.Y, Vec3.Z, new Vec3(1, 1, 1), new Vec3(-2, 0.5, 3)};
        for (Vec3 a : axes) {
            Quat arc = Quat.shortestArc(a, a.negate());
            assertEquals(1.0, norm(arc), TOL);
            Vec3 unit = a.mul(1.0 / a.length());
            assertVec(unit.negate(), arc.rotate(unit), 1e-12, "antiparallel " + a);
            assertEquals(Math.PI, Quat.IDENTITY.angleTo(arc), 1e-9, "half turn for " + a);
        }
        // near-antiparallel: result must be finite, unit, and map a to b within an offset-dependent bound
        double[] offsets = {1e-3, 1e-4, 1e-5, 1e-6, 1e-7, 1e-9};
        double[] bounds = {1e-9, 1e-9, 1e-9, 1e-9, 1e-8, 1e-6};
        Vec3 a = new Vec3(0, 1, 0);
        for (int i = 0; i < offsets.length; i++) {
            double e = offsets[i];
            Vec3 b = new Vec3(Math.sin(e), -Math.cos(e), 0);
            Quat arc = Quat.shortestArc(a, b);
            assertEquals(1.0, norm(arc), TOL);
            assertFinite(arc.rotate(a), "near-antiparallel " + e);
            assertVec(b, arc.rotate(a), bounds[i], "near-antiparallel offset " + e);
        }
        // zero or non-finite inputs fall back to the identity rotation
        assertSameRotation(Quat.IDENTITY, Quat.shortestArc(new Vec3(0, 0, 0), new Vec3(1, 0, 0)), 0.0, "zero from");
        assertSameRotation(Quat.IDENTITY, Quat.shortestArc(new Vec3(1, 0, 0), new Vec3(0, 0, 0)), 0.0, "zero to");
        assertSameRotation(Quat.IDENTITY, Quat.shortestArc(new Vec3(Double.NaN, 0, 0), new Vec3(1, 0, 0)), 0.0, "NaN from");
    }

    // ------------------------------------------------------------ slerp / nlerp

    @Test
    @DisplayName("slerp: endpoints and constant angular speed, with extrapolation")
    void slerpConstantSpeed() {
        SplittableRandom r = new SplittableRandom(SEED + 3);
        for (int i = 0; i < 2_000; i++) {
            Quat a = randomRotation(r);
            Quat b = randomRotation(r);
            double total = a.angleTo(b);
            if (total < 1e-3 || total > Math.PI - 1e-3) {
                continue;
            }
            assertSameRotation(a, Quat.slerp(a, b, 0.0), 1e-12, "t=0");
            assertSameRotation(b, Quat.slerp(a, b, 1.0), 1e-11, "t=1");
            for (double t : new double[] {0.1, 0.25, 0.5, 0.8, 0.95}) {
                Quat m = Quat.slerp(a, b, t);
                assertEquals(1.0, norm(m), TOL);
                assertEquals(t * total, a.angleTo(m), 1e-9, "angle from a at t=" + t);
                assertEquals((1 - t) * total, m.angleTo(b), 1e-9, "angle to b at t=" + t);
            }
            Quat ahead = Quat.slerp(a, b, 2.0);
            assertEquals(Math.min(2 * total, 2 * Math.PI - 2 * total), a.angleTo(ahead), 1e-9, "t=2 extrapolates by 2x angle");
        }
    }

    @Test
    @DisplayName("slerp takes the shortest path: q1 and -q1 give the same result")
    void slerpShortestPath() {
        SplittableRandom r = new SplittableRandom(SEED + 4);
        for (int i = 0; i < 2_000; i++) {
            Quat a = randomRotation(r);
            Quat b = randomRotation(r);
            double t = r.nextDouble();
            Vec3 v = randomVec(r, 5);
            Quat viaB = Quat.slerp(a, b, t);
            Quat viaNegB = Quat.slerp(a, negated(b), t);
            assertSameRotation(viaB, viaNegB, 1e-9, "q1 vs -q1");
            // never passes through the long way round
            assertTrue(a.angleTo(viaB) <= a.angleTo(b) + 1e-9, "stays inside the short arc");
            assertFinite(viaB.rotate(v), "slerp result");
        }
    }

    @Test
    @DisplayName("slerp is continuous across the near-parallel (nlerp) threshold and never NaN")
    void slerpNearParallel() {
        Vec3 axis = new Vec3(1, 1, 0);
        Quat a = Quat.axisAngle(new Vec3(0, 0, 1), 0.3);
        double[] deltas = {0.0, 1e-15, 1e-12, 1e-10, 5e-9, 2e-8, 1e-7, 1e-6, 1e-4};
        for (double d : deltas) {
            Quat b = a.mul(Quat.axisAngle(axis, d == 0.0 ? 1e-30 : d));
            for (double t : new double[] {0.0, 0.3, 0.5, 1.0}) {
                Quat m = Quat.slerp(a, b, t);
                assertEquals(1.0, norm(m), TOL);
                assertEquals(t * d, a.angleTo(m), 1e-12, "tiny delta " + d + " at t=" + t);
            }
        }
        // identical quaternions
        Quat same = Quat.slerp(a, a, 0.37);
        assertSameRotation(a, same, TOL, "identical inputs");
    }

    @Test
    @DisplayName("nlerp: unit norm, endpoints, shortest sign")
    void nlerp() {
        SplittableRandom r = new SplittableRandom(SEED + 5);
        for (int i = 0; i < 2_000; i++) {
            Quat a = randomRotation(r);
            Quat b = randomRotation(r);
            assertSameRotation(a, Quat.nlerp(a, b, 0.0), 1e-12, "t=0");
            assertSameRotation(b, Quat.nlerp(a, b, 1.0), 1e-11, "t=1");
            double t = r.nextDouble();
            Quat m = Quat.nlerp(a, b, t);
            assertEquals(1.0, norm(m), TOL);
            assertSameRotation(m, Quat.nlerp(a, negated(b), t), 1e-9, "sign-insensitive");
        }
    }

    // ------------------------------------------------------------ Frame conversion

    @Test
    @DisplayName("toFrame / fromFrame round trip, including 180-degree rotations (all matrix-to-quaternion branches)")
    void frameRoundTrip() {
        SplittableRandom r = new SplittableRandom(SEED + 6);
        for (int i = 0; i < 10_000; i++) {
            Quat a = randomRotation(r);
            Frame f = a.toFrame();
            assertFrameValid(f, 1e-12, "toFrame");
            assertSameRotation(a, Quat.fromFrame(f), 1e-12, "round trip " + i);
        }
        Vec3[] axes = {Vec3.X, Vec3.Y, Vec3.Z, new Vec3(1, 1, 0), new Vec3(0, 1, -1), new Vec3(1, 1, 1), new Vec3(-3, 2, 1)};
        for (Vec3 axis : axes) {
            for (double angle : new double[] {Math.PI, -Math.PI, Math.PI - 1e-9, 2.0, 3.0}) {
                Quat a = Quat.axisAngle(axis, angle);
                assertSameRotation(a, Quat.fromFrame(a.toFrame()), 1e-12, "half turn about " + axis + " angle " + angle);
            }
        }
        // the identity frame maps to the identity rotation
        assertSameRotation(Quat.IDENTITY, Quat.fromFrame(Frame.WORLD), 0.0, "WORLD");
        // frame columns are the images of the basis vectors
        Quat a = Quat.axisAngle(new Vec3(0, 1, 0), Math.PI / 2);
        assertVec(new Vec3(0, 0, -1), a.toFrame().x(), 1e-12, "image of +X");
        assertVec(new Vec3(1, 0, 0), a.toFrame().z(), 1e-12, "image of +Z");
    }

    // ------------------------------------------------------------ Euler import helper (audit: Euler order)

    @Test
    @DisplayName("fromEulerApplyXThenYThenZ equals the explicit rotation sequence X, then Y, then Z about fixed axes")
    void eulerApplyOrder() {
        SplittableRandom r = new SplittableRandom(SEED + 7);
        for (int i = 0; i < 2_000; i++) {
            double rx = r.nextDouble(-Math.PI, Math.PI);
            double ry = r.nextDouble(-Math.PI, Math.PI);
            double rz = r.nextDouble(-Math.PI, Math.PI);
            Vec3 v = randomVec(r, 10);
            Vec3 stepwise = rodrigues(Vec3.Z, rz, rodrigues(Vec3.Y, ry, rodrigues(Vec3.X, rx, v)));
            assertVec(stepwise, Quat.fromEulerApplyXThenYThenZ(rx, ry, rz).rotate(v), 1e-11, "x then y then z");
        }
        // hand-derived: X applied first (+90 about X keeps +X), then Z (+90 about Z sends +X to +Y)
        assertVec(new Vec3(0, 1, 0), Quat.fromEulerApplyXThenYThenZ(Math.PI / 2, 0, Math.PI / 2).rotate(Vec3.X), 1e-12, "X first");
        // the order is observable: Z first then X would send +X to +Z instead
        assertVec(new Vec3(0, 0, 1), Quat.axisAngle(Vec3.X, Math.PI / 2).mul(Quat.axisAngle(Vec3.Z, Math.PI / 2)).rotate(Vec3.X),
                1e-12, "Z-then-X order differs");
    }

    // ------------------------------------------------------------ drift

    @Test
    @DisplayName("1,000,000 compositions: periodic renormalization keeps the norm within 1e-12; raw drift is reported")
    void driftOverMillionCompositions() {
        SplittableRandom r = new SplittableRandom(SEED + 8);
        Quat[] steps = new Quat[64];
        for (int i = 0; i < steps.length; i++) {
            steps[i] = Quat.axisAngle(randomUnit(r), r.nextDouble(-0.05, 0.05));
        }
        Quat raw = Quat.IDENTITY;
        Quat renormalized = Quat.IDENTITY;
        double worstBeforeRenormalize = 0.0;
        for (int i = 1; i <= 1_000_000; i++) {
            Quat s = steps[i & 63];
            raw = raw.mul(s);
            renormalized = renormalized.mul(s);
            if (i % 1000 == 0) {
                worstBeforeRenormalize = Math.max(worstBeforeRenormalize, Math.abs(norm(renormalized) - 1.0));
                renormalized = renormalized.normalized();
            }
        }
        System.out.println("[M1 drift report] raw |norm-1| after 1,000,000 compositions = " + Math.abs(norm(raw) - 1.0));
        System.out.println("[M1 drift report] worst |norm-1| between renormalizations (every 1000) = " + worstBeforeRenormalize);
        assertTrue(worstBeforeRenormalize < 1e-12, "norm drifted by " + worstBeforeRenormalize + " between renormalizations");
        assertEquals(1.0, norm(renormalized.normalized()), TOL);
    }
}
