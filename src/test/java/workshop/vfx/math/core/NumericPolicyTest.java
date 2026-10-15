package workshop.vfx.math.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static workshop.vfx.math.core.TestSupport.assertFinite;
import static workshop.vfx.math.core.TestSupport.assertFrameValid;
import static workshop.vfx.math.core.TestSupport.assertVec;
import static workshop.vfx.math.core.TestSupport.randomRotation;
import static workshop.vfx.math.core.TestSupport.randomUnit;
import static workshop.vfx.math.core.TestSupport.randomVec;
import static workshop.vfx.math.core.TestSupport.rodrigues;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Pins the numerical policy of IMPLEMENTATION-CONTRACT.md revision 2 (Amendment 1), section 0A:
 *
 * <ol>
 *   <li>Tier 1, raw arithmetic: IEEE-754 binary64 semantics. Overflow gives infinity, NaN propagates, nothing is
 *       clamped, sanitized or thrown.
 *   <li>Tier 2, degeneracy operations: total, with a finite valid result for EVERY double input.
 *   <li>Tier 3, validated types: invalid input throws IllegalArgumentException; a Pose.then/inverse result that is
 *       not representable throws ArithmeticException. Never clamped.
 *   <li>Supported magnitude 1e100: single operations are finite and accurate inside it.
 * </ol>
 */
@Tag("numeric-policy")
class NumericPolicyTest {
    private static final long SEED = 20261014L;
    private static final double M = 1e100;
    private static final double INF = Double.POSITIVE_INFINITY;
    private static final double NAN = Double.NaN;

    // ------------------------------------------------------------ helpers

    private static double maxAbs(Vec3 v) {
        return Math.max(Math.abs(v.x()), Math.max(Math.abs(v.y()), Math.abs(v.z())));
    }

    private static boolean finite(Vec3 v) {
        return Double.isFinite(v.x()) && Double.isFinite(v.y()) && Double.isFinite(v.z());
    }

    /** A direction vector the contract treats as usable: finite and with largest component at least 1e-12. */
    private static boolean validDirection(Vec3 v) {
        return finite(v) && maxAbs(v) >= 1e-12;
    }

    /** Independent unit-vector computation that cannot overflow: scale by the largest component first. */
    private static Vec3 unitOf(Vec3 v) {
        double m = maxAbs(v);
        double x = v.x() / m;
        double y = v.y() / m;
        double z = v.z() / m;
        double n = Math.sqrt(x * x + y * y + z * z);
        return new Vec3(x / n, y / n, z / n);
    }

    private static double length(Vec3 v) {
        return Math.sqrt(v.x() * v.x() + v.y() * v.y() + v.z() * v.z());
    }

    private static double quatNorm(Quat q) {
        double m = Math.max(Math.max(Math.abs(q.w()), Math.abs(q.x())), Math.max(Math.abs(q.y()), Math.abs(q.z())));
        double a = q.w() / m;
        double b = q.x() / m;
        double c = q.y() / m;
        double d = q.z() / m;
        return m * Math.sqrt(a * a + b * b + c * c + d * d);
    }

    private static boolean finiteQ(Quat q) {
        return Double.isFinite(q.w()) && Double.isFinite(q.x()) && Double.isFinite(q.y()) && Double.isFinite(q.z());
    }

    private static boolean allZero(Quat q) {
        return q.w() == 0.0 && q.x() == 0.0 && q.y() == 0.0 && q.z() == 0.0;
    }

    private static void assertUnitFiniteQuat(Quat q, String label) {
        if (!finiteQ(q)) {
            fail(label + ": non-finite quaternion " + q);
        }
        assertEquals(1.0, quatNorm(q), 1e-12, label + " norm");
    }

    private static void assertIdentity(Quat q, String label) {
        if (q.w() != 1.0 || q.x() != 0.0 || q.y() != 0.0 || q.z() != 0.0) {
            fail(label + ": expected IDENTITY but was " + q);
        }
    }

    /** Finite-and-near comparison with an explicit absolute tolerance chosen from the operand magnitudes. */
    private static void assertNear(Vec3 expected, Vec3 actual, double tolAbs, String label) {
        assertFinite(actual, label);
        assertVec(expected, actual, tolAbs, label);
    }

    // ------------------------------------------------------------ Tier 1: raw arithmetic is IEEE-754

    @Test
    @DisplayName("Tier 1: Vec3 and Quat overflow to infinity with no exception and no saturation")
    void rawArithmeticOverflowsToInfinity() {
        Vec3 big = new Vec3(Double.MAX_VALUE, 0, 0);
        Vec3 doubled = big.mul(2);
        assertTrue(Double.isInfinite(doubled.x()) && doubled.x() > 0, "MAX_VALUE * 2 must be +Infinity, was " + doubled.x());
        assertEquals(0.0, doubled.y(), 0.0);
        assertEquals(0.0, doubled.z(), 0.0);
        Vec3 negDoubled = big.mul(-2);
        assertTrue(Double.isInfinite(negDoubled.x()) && negDoubled.x() < 0, "MAX_VALUE * -2 must be -Infinity");
        assertTrue(Double.isInfinite(big.add(big).x()), "MAX_VALUE + MAX_VALUE must overflow");
        assertTrue(Double.isInfinite(big.negate().sub(big).x()), "-MAX_VALUE - MAX_VALUE must overflow");
        assertTrue(Double.isInfinite(new Vec3(1e200, 0, 0).dot(new Vec3(1e200, 0, 0))), "1e200 * 1e200 must overflow in dot");
        assertTrue(Double.isInfinite(new Vec3(1e200, 0, 0).lengthSquared()), "lengthSquared must overflow");
        // a non-overflowing component stays untouched while its neighbour overflows
        Vec3 mixed = new Vec3(Double.MAX_VALUE, 3.0, -4.0).mul(2);
        assertEquals(6.0, mixed.y(), 0.0);
        assertEquals(-8.0, mixed.z(), 0.0);
        // quaternion product overflows the same way
        Quat product = new Quat(Double.MAX_VALUE, 0, 0, 0).mul(new Quat(2, 0, 0, 0));
        assertTrue(Double.isInfinite(product.w()) && product.w() > 0, "Quat.mul overflow must be +Infinity");
        // overflow is not an error and not silently repaired
        assertFalse(doubled.isFinite(), "result of an overflowing operation is reported non-finite by isFinite()");
    }

    @Test
    @DisplayName("Tier 1: NaN and infinity propagate by IEEE rules (no sanitizing, no exceptions)")
    void rawArithmeticPropagatesNaNAndInfinity() {
        assertTrue(Double.isNaN(new Vec3(INF, 0, 0).sub(new Vec3(INF, 0, 0)).x()), "inf - inf is NaN");
        assertTrue(Double.isNaN(new Vec3(INF, 0, 0).mul(0).x()), "inf * 0 is NaN");
        assertTrue(Double.isNaN(new Vec3(NAN, 1, 1).add(new Vec3(1, 1, 1)).x()), "NaN + 1 is NaN");
        assertEquals(2.0, new Vec3(NAN, 1, 1).add(new Vec3(1, 1, 1)).y(), 0.0);
        assertTrue(Double.isNaN(new Vec3(NAN, 0, 0).dot(new Vec3(1, 1, 1))), "dot with NaN is NaN");
        assertTrue(Double.isInfinite(new Vec3(INF, 0, 0).add(new Vec3(1, 0, 0)).x()), "inf + 1 is inf");
        assertTrue(Double.isNaN(new Quat(NAN, 0, 0, 0).mul(Quat.IDENTITY).w()), "Quat.mul propagates NaN");
    }

    // ------------------------------------------------------------ supported magnitude 1e100

    @Test
    @DisplayName("Supported magnitude: Vec3, Quat.rotate and Frame stay finite and homogeneous up to 1e100")
    void supportedMagnitudeVectors() {
        SplittableRandom r = new SplittableRandom(SEED);
        for (int i = 0; i < 2_000; i++) {
            Vec3 a = randomVec(r, 10);
            Vec3 b = randomVec(r, 10);
            Vec3 aM = a.mul(M);
            Vec3 bM = b.mul(M);
            double sa = maxAbs(a);
            double sb = maxAbs(b);
            double t = r.nextDouble();
            double lin = 1e-13 * (sa + sb) * M;
            assertNear(a.add(b).mul(M), aM.add(bM), lin, "add");
            assertNear(a.sub(b).mul(M), aM.sub(bM), lin, "sub");
            assertNear(a.mul(2).mul(M), aM.mul(2), lin, "mul(2)");
            assertNear(a.negate().mul(M), aM.negate(), lin, "negate");
            assertNear(a.lerp(b, t).mul(M), aM.lerp(bM, t), lin, "lerp");
            assertNear(a.cross(b).mul(M * M), aM.cross(bM), 1e-13 * 4 * sa * sb * M * M, "cross");
            double dotActual = aM.dot(bM);
            assertTrue(Double.isFinite(dotActual), "dot finite at 1e100");
            assertEquals(a.dot(b) * (M * M), dotActual, 1e-13 * 4 * sa * sb * M * M, "dot");
            assertEquals(a.length() * M, aM.length(), 1e-13 * sa * 2 * M, "length");
            assertEquals(a.distance(b) * M, aM.distance(bM), lin * 2, "distance");
            Quat q = randomRotation(r);
            assertNear(q.rotate(a).mul(M), q.rotate(aM), 1e-13 * 4 * sa * M, "rotate");
            Frame f = q.toFrame();
            assertNear(f.toWorld(a).mul(M), f.toWorld(aM), 1e-13 * 4 * sa * M, "Frame.toWorld");
            assertNear(f.toLocal(a).mul(M), f.toLocal(aM), 1e-13 * 4 * sa * M, "Frame.toLocal");
        }
        // components exactly at the supported bound
        Vec3 corner = new Vec3(M, -M, M);
        Vec3 other = new Vec3(M, M, -M);
        assertFinite(corner.add(other), "add at the bound");
        assertFinite(corner.mul(2), "mul(2) at the bound");
        assertFinite(corner.cross(other), "cross at the bound");
        assertTrue(Double.isFinite(corner.dot(other)), "dot at the bound");
        assertTrue(Double.isFinite(corner.lengthSquared()), "lengthSquared at the bound");
        assertEquals(Math.sqrt(3.0) * M, corner.length(), 1e-14 * M, "length at the bound");
        assertFinite(Quat.axisAngle(Vec3.Y, 1.0).rotate(corner), "rotate at the bound");
    }

    @Test
    @DisplayName("Supported magnitude: Pose with scale and translation up to 1e100 is finite and accurate; compositions of two stay finite")
    void supportedMagnitudePose() {
        SplittableRandom r = new SplittableRandom(SEED + 1);
        for (int i = 0; i < 2_000; i++) {
            Vec3 axis = randomUnit(r);
            double angle = r.nextDouble(-Math.PI, Math.PI);
            Quat rot = Quat.axisAngle(axis, angle);
            Vec3 p = randomVec(r, 10);
            Vec3 t = randomVec(r, 10);
            double base = maxAbs(t) + 2 * maxAbs(p) + 1.0;

            // huge scale and translation, ordinary point: world = t*M + M * R p
            Pose big = new Pose(t.mul(M), rot, M);
            Vec3 expectedBig = t.mul(M).add(rodrigues(axis, angle, p).mul(M));
            assertNear(expectedBig, big.toWorld(p), 1e-13 * M * base, "toWorld at scale 1e100");
            assertVec(p, big.toLocal(big.toWorld(p)), 1e-12 * base, "round trip at scale 1e100");

            // tiny scale, huge point: world = t + R p
            Pose small = new Pose(t, rot, 1.0 / M);
            Vec3 pM = p.mul(M);
            Vec3 expectedSmall = t.add(rodrigues(axis, angle, p));
            assertNear(expectedSmall, small.toWorld(pM), 1e-13 * base, "toWorld at scale 1e-100 with a 1e100 point");
            assertVec(pM, small.toLocal(small.toWorld(pM)), 1e-12 * M * base, "round trip at scale 1e-100");

            // composition of two poses at the bound is finite and equals sequential application
            Vec3 tb = randomVec(r, 10);
            Pose a = new Pose(t.mul(M), rot, M);
            Pose b = new Pose(tb.mul(M), randomRotation(r), M);
            Pose ab = a.then(b);
            assertTrue(Double.isFinite(ab.scale()) && ab.scale() > 0, "composed scale finite and positive");
            assertEquals(M * M, ab.scale(), 1e-13 * M * M, "composed scale is the product");
            assertFinite(ab.translation(), "composed translation");
            Vec3 viaChain = a.toWorld(b.toWorld(p));
            assertNear(viaChain, ab.toWorld(p), 1e-12 * M * M * (maxAbs(t) + maxAbs(tb) + 2 * maxAbs(p) + 1.0), "then equals sequential at 1e100");

            // inverse at the bounds
            Pose inv = big.inverse();
            assertEquals(1.0 / M, inv.scale(), 1e-14 / M, "inverse scale of 1e100");
            assertFinite(inv.translation(), "inverse translation of a 1e100 pose");
            Pose invSmall = small.inverse();
            assertEquals(M, invSmall.scale(), 1e-14 * M, "inverse scale of 1e-100");
            assertFinite(invSmall.translation(), "inverse translation of a 1e-100 pose");
        }
    }

    // ------------------------------------------------------------ Tier 1: Pose evaluation is IEEE, not validated

    @Test
    @DisplayName("Tier 1: Pose.toWorld overflows to infinity (no exception, no clamping); Frame.toWorld likewise")
    void poseEvaluationFollowsIeee() {
        Pose scaled = new Pose(Vec3.ZERO, Quat.IDENTITY, 1e200);
        Vec3 world = scaled.toWorld(new Vec3(1e200, 1.0, 0.0));
        assertTrue(Double.isInfinite(world.x()) && world.x() > 0, "1e200 * 1e200 must be +Infinity, was " + world.x());
        assertEquals(1e200, world.y(), 1e185);
        Vec3 local = new Pose(Vec3.ZERO, Quat.IDENTITY, 1e-200).toLocal(new Vec3(1e200, 0, 0));
        assertTrue(Double.isInfinite(local.x()), "toLocal overflows to infinity, was " + local.x());
        Vec3 viaFrame = Frame.WORLD.toWorld(new Vec3(Double.MAX_VALUE, Double.MAX_VALUE, 0)).mul(2);
        assertTrue(Double.isInfinite(viaFrame.x()));
    }

    // ------------------------------------------------------------ Tier 3: derived poses are checked, never clamped

    @Test
    @DisplayName("Tier 3: Pose.then throws ArithmeticException when scale overflows or underflows to zero; two valid poses never give an invalid one")
    void poseThenRejectsNonRepresentableScale() {
        Pose huge = new Pose(Vec3.ZERO, Quat.IDENTITY, 1e200);
        Pose tiny = new Pose(Vec3.ZERO, Quat.IDENTITY, 1e-200);
        // both operands are individually valid
        assertEquals(1e200, huge.scale(), 0.0);
        assertEquals(1e-200, tiny.scale(), 0.0);
        assertThrows(ArithmeticException.class, () -> huge.then(huge));
        assertThrows(ArithmeticException.class, () -> tiny.then(tiny));
        // gradual underflow is representable and therefore accepted (not rejected, not flushed to zero)
        Pose a = new Pose(Vec3.ZERO, Quat.IDENTITY, 1e-160);
        Pose subnormalProduct = a.then(a);
        assertTrue(subnormalProduct.scale() > 0.0 && subnormalProduct.scale() < Double.MIN_NORMAL, "1e-160 * 1e-160 is a positive subnormal, was " + subnormalProduct.scale());
        // the successful composition is untouched by the failed ones: an in-range composition still works
        Pose ok = huge.then(tiny);
        assertEquals(1.0, ok.scale(), 1e-12);
    }

    @Test
    @DisplayName("Tier 3: Pose.then throws ArithmeticException when the composed translation overflows")
    void poseThenRejectsNonRepresentableTranslation() {
        Pose scaled = new Pose(Vec3.ZERO, Quat.IDENTITY, 1e200);
        Pose shifted = new Pose(new Vec3(1e200, 0, 0), Quat.IDENTITY, 1.0);
        assertThrows(ArithmeticException.class, () -> scaled.then(shifted));
        Pose edgeA = new Pose(new Vec3(Double.MAX_VALUE, 0, 0), Quat.IDENTITY, 1.0);
        Pose edgeB = new Pose(new Vec3(Double.MAX_VALUE, 0, 0), Quat.IDENTITY, 1.0);
        assertThrows(ArithmeticException.class, () -> edgeA.then(edgeB));
        // the same poses composed with a cancelling translation are fine
        Pose cancel = new Pose(new Vec3(-Double.MAX_VALUE, 0, 0), Quat.IDENTITY, 1.0);
        assertEquals(0.0, edgeA.then(cancel).translation().x(), 0.0);
    }

    @Test
    @DisplayName("Tier 3: Pose.inverse throws ArithmeticException when the reciprocal scale or inverse translation is not representable")
    void poseInverseRejectsNonRepresentableResult() {
        // reciprocal of a subnormal scale overflows
        Pose subnormalScale = new Pose(Vec3.ZERO, Quat.IDENTITY, 1e-310);
        assertTrue(subnormalScale.scale() > 0.0);
        assertThrows(ArithmeticException.class, subnormalScale::inverse);
        // inverse translation overflows: t' = -(1/s) R^-1 t = -1e200 * 1e200
        Pose lever = new Pose(new Vec3(1e200, 0, 0), Quat.IDENTITY, 1e-200);
        assertThrows(ArithmeticException.class, lever::inverse);
        // reciprocal of the largest scale is a positive subnormal: representable, therefore accepted
        Pose maxScale = new Pose(Vec3.ZERO, Quat.IDENTITY, Double.MAX_VALUE);
        Pose inv = maxScale.inverse();
        assertTrue(inv.scale() > 0.0 && inv.scale() < Double.MIN_NORMAL, "1/MAX_VALUE is a positive subnormal, was " + inv.scale());
        // an ordinary pose still inverts
        assertEquals(0.5, new Pose(Vec3.ZERO, Quat.IDENTITY, 2.0).inverse().scale(), 0.0);
    }

    @Test
    @DisplayName("Tier 3: constructors keep throwing IllegalArgumentException for invalid inputs")
    void constructorsStillThrowIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new Pose(Vec3.ZERO, Quat.IDENTITY, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new Pose(new Vec3(NAN, 0, 0), Quat.IDENTITY, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new Frame(Vec3.X, Vec3.Y, new Vec3(0, 0, -1)));
        assertThrows(IllegalArgumentException.class, () -> Quat.axisAngle(new Vec3(0, 0, 0), 1.0));
    }

    // ------------------------------------------------------------ Tier 2: degeneracy operations are total

    private static List<Vec3> specialVectors() {
        double[] corners = {0.0, 1e-13, 1.0, 1e300, INF, NAN};
        double[] all = {0.0, -0.0, Double.MIN_VALUE, Double.MIN_NORMAL, 1e-300, 1e-13, 1e-11, 0.5, 1.0, -3.0, 1e150, 1e300,
            Double.MAX_VALUE, -Double.MAX_VALUE, INF, -INF, NAN};
        List<Vec3> list = new ArrayList<>();
        for (double x : corners) {
            for (double y : corners) {
                for (double z : corners) {
                    list.add(new Vec3(x, y, z));
                }
            }
        }
        for (double v : all) {
            list.add(new Vec3(v, v, v));
            list.add(new Vec3(v, 0, 0));
            list.add(new Vec3(0, v, 0));
            list.add(new Vec3(0, 0, v));
            list.add(new Vec3(-v, v, 1));
        }
        return list;
    }

    @Test
    @DisplayName("Tier 2: normalizedOr is total: fallback exactly for degenerate input, otherwise a finite unit vector")
    void normalizedOrIsTotal() {
        Vec3 fb = new Vec3(0.25, 0.5, 0.75);
        int checked = 0;
        for (Vec3 v : specialVectors()) {
            Vec3 n = v.normalizedOr(fb);
            if (!validDirection(v)) {
                assertSame(fb, n, "degenerate input " + v + " must give the fallback object");
            } else {
                assertFinite(n, "normalizedOr(" + v + ")");
                assertEquals(1.0, length(n), 1e-12, "unit length for " + v);
                assertVec(unitOf(v), n, 1e-12, "direction for " + v);
            }
            checked++;
        }
        assertTrue(checked > 250, "special vector set is unexpectedly small: " + checked);
    }

    @Test
    @DisplayName("Tier 2: Quat.normalized and inverse are total: finite unit result for every double input, IDENTITY for zero or non-finite")
    void quaternionNormalizationIsTotal() {
        double[] vals = {0.0, -0.0, 1e-300, Double.MIN_VALUE, 1.0, -2.0, 1e300, Double.MAX_VALUE, INF, -INF, NAN};
        int checked = 0;
        for (double w : vals) {
            for (double x : vals) {
                for (double y : vals) {
                    for (double z : vals) {
                        Quat q = new Quat(w, x, y, z);
                        Quat n = q.normalized();
                        Quat inv = q.inverse();
                        assertUnitFiniteQuat(n, "normalized(" + q + ")");
                        assertUnitFiniteQuat(inv, "inverse(" + q + ")");
                        if (!finiteQ(q) || allZero(q)) {
                            assertIdentity(n, "normalized(" + q + ")");
                        }
                        checked++;
                    }
                }
            }
        }
        assertEquals(11 * 11 * 11 * 11, checked);
    }

    @Test
    @DisplayName("Tier 2: shortestArc is total: unit result for every pair; IDENTITY for degenerate input; correct mapping otherwise")
    void shortestArcIsTotal() {
        List<Vec3> vs = specialVectors();
        int checked = 0;
        for (int i = 0; i < vs.size(); i += 2) {
            for (int j = 0; j < vs.size(); j += 3) {
                Vec3 a = vs.get(i);
                Vec3 b = vs.get(j);
                Quat arc = Quat.shortestArc(a, b);
                assertUnitFiniteQuat(arc, "shortestArc(" + a + ", " + b + ")");
                if (validDirection(a) && validDirection(b)) {
                    assertVec(unitOf(b), arc.rotate(unitOf(a)), 1e-9, "arc maps " + a + " to " + b);
                } else {
                    assertIdentity(arc, "shortestArc(" + a + ", " + b + ")");
                }
                checked++;
            }
        }
        assertTrue(checked > 2000, "too few pairs checked: " + checked);
    }

    @Test
    @DisplayName("Tier 2: Frame factories always return a valid right-handed frame, WORLD for a degenerate axis, for every double input")
    void frameFactoriesAreTotal() {
        List<Vec3> vs = specialVectors();
        Frame prev = Frame.alignY(new Vec3(1, 2, 3), new Vec3(0, 1, 0));
        int checked = 0;
        for (int i = 0; i < vs.size(); i += 2) {
            for (int j = 0; j < vs.size(); j += 3) {
                Vec3 a = vs.get(i);
                Vec3 h = vs.get(j);
                Frame fy = Frame.alignY(a, h);
                assertFrameValid(fy, 1e-12, "alignY(" + a + ", " + h + ")");
                Frame fz = Frame.alignZ(a, h);
                assertFrameValid(fz, 1e-12, "alignZ(" + a + ", " + h + ")");
                Frame fh = Frame.fromHeading(a, h);
                assertFrameValid(fh, 1e-12, "fromHeading(" + a + ", " + h + ")");
                if (validDirection(a)) {
                    Vec3 u = unitOf(a);
                    assertVec(u, fy.y(), 1e-12, "alignY keeps the axis for " + a);
                    assertVec(u, fz.z(), 1e-12, "alignZ keeps the axis for " + a);
                    assertVec(u.negate(), fh.z(), 1e-12, "fromHeading z is back for " + a);
                } else {
                    assertVec(Vec3.X, fy.x(), 0.0, "alignY degenerate axis x");
                    assertVec(Vec3.Y, fy.y(), 0.0, "alignY degenerate axis y");
                    assertVec(Vec3.Z, fz.z(), 0.0, "alignZ degenerate axis z");
                    assertVec(Vec3.Z, fh.z(), 0.0, "fromHeading degenerate heading z");
                    assertVec(Vec3.X, fh.x(), 0.0, "fromHeading degenerate heading x");
                }
                Frame moved = Frame.transportY(prev, a);
                assertFrameValid(moved, 1e-11, "transportY(" + a + ")");
                if (validDirection(a)) {
                    assertVec(unitOf(a), moved.y(), 1e-11, "transport honours " + a);
                } else {
                    assertSame(prev, moved, "transportY with degenerate axis " + a + " must return the previous frame");
                }
                checked++;
            }
        }
        assertTrue(checked > 2000, "too few pairs checked: " + checked);
    }
}
