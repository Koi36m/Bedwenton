package workshop.vfx.math.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static workshop.vfx.math.core.TestSupport.assertFinite;
import static workshop.vfx.math.core.TestSupport.assertSameRotation;
import static workshop.vfx.math.core.TestSupport.assertVec;
import static workshop.vfx.math.core.TestSupport.randomRotation;
import static workshop.vfx.math.core.TestSupport.randomUnit;
import static workshop.vfx.math.core.TestSupport.randomVec;
import static workshop.vfx.math.core.TestSupport.rodrigues;

import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Pins the Revision 3 clarifications of IMPLEMENTATION-CONTRACT.md (Amendment 2):
 * <ul>
 *   <li>A: a caller-supplied fallback is returned unchanged; finiteness or unit length of the fallback path is the
 *       caller's responsibility. {@code MathUtil.clamp} is bounded by its bounds, so infinite bounds can give
 *       infinite results.</li>
 *   <li>B: the supported magnitude 1e100 is defined per operand, including scalar multipliers, interpolation
 *       parameters, angles and axes. Outside the documented domain plain IEEE arithmetic applies.</li>
 * </ul>
 */
@Tag("numeric-policy")
class PolicyClarificationTest {
    private static final long SEED = 20261014L;
    private static final double M = 1e100;
    private static final double INF = Double.POSITIVE_INFINITY;
    private static final double NAN = Double.NaN;

    private static double maxAbs(Vec3 v) {
        return Math.max(Math.abs(v.x()), Math.max(Math.abs(v.y()), Math.abs(v.z())));
    }

    private static double norm(Quat q) {
        return Math.sqrt(q.w() * q.w() + q.x() * q.x() + q.y() * q.y() + q.z() * q.z());
    }

    // ------------------------------------------------------------ Clarification A: normalizedOr fallback

    @Test
    @DisplayName("A: normalizedOr returns the caller's fallback object unchanged, even when it is NaN, infinite, zero or not unit")
    void normalizedOrPreservesCallerFallback() {
        Vec3[] fallbacks = {
            new Vec3(NAN, NAN, NAN),
            new Vec3(INF, 0, -INF),
            new Vec3(0, 0, 0),
            new Vec3(0, 0, 5),
            new Vec3(1e300, 1e300, 1e300),
            new Vec3(-0.0, 0.0, -0.0),
        };
        Vec3[] degenerate = {new Vec3(0, 0, 0), new Vec3(1e-13, 0, 0), new Vec3(NAN, 1, 1), new Vec3(INF, 0, 0), new Vec3(1e-300, 0, 0)};
        for (Vec3 fb : fallbacks) {
            for (Vec3 v : degenerate) {
                assertSame(fb, v.normalizedOr(fb), "fallback must come back as the same object for input " + v + " and fallback " + fb);
            }
            // a usable direction never touches the fallback, whatever it contains
            Vec3 n = new Vec3(3, 4, 0).normalizedOr(fb);
            assertFinite(n, "normalized result with fallback " + fb);
            assertVec(new Vec3(0.6, 0.8, 0), n, 1e-12, "direction with fallback " + fb);
        }
    }

    @Test
    @DisplayName("A: finiteness and unit length of the fallback path belong to the caller")
    void fallbackGuaranteesAreConditionalOnTheCallersFallback() {
        Vec3 degenerate = new Vec3(0, 0, 0);
        // finite fallback gives a finite result, but it is not normalized for the caller
        Vec3 finiteFallback = new Vec3(0, 0, 5);
        Vec3 r1 = degenerate.normalizedOr(finiteFallback);
        assertFinite(r1, "finite fallback");
        assertEquals(5.0, r1.length(), 0.0, "fallback is not normalized");
        // a unit fallback gives a unit result
        Vec3 unitFallback = new Vec3(0, 1, 0);
        assertEquals(1.0, degenerate.normalizedOr(unitFallback).length(), 1e-15);
        // a non-finite fallback gives a non-finite result: the contract does not repair the caller's value
        assertFalse(degenerate.normalizedOr(new Vec3(NAN, 0, 0)).isFinite());
        assertFalse(degenerate.normalizedOr(new Vec3(INF, 0, 0)).isFinite());
    }

    // ------------------------------------------------------------ Clarification A: clamp with infinite bounds

    @Test
    @DisplayName("A: clamp always returns a value inside its bounds (or the lower bound for NaN); infinite bounds are allowed")
    void clampWithInfiniteBounds() {
        assertEquals(5.0, MathUtil.clamp(5.0, -INF, INF), 0.0);
        assertEquals(1e300, MathUtil.clamp(1e300, -INF, INF), 0.0);
        assertEquals(INF, MathUtil.clamp(INF, 0.0, INF), 0.0);
        assertEquals(-INF, MathUtil.clamp(-INF, -INF, 0.0), 0.0);
        assertEquals(INF, MathUtil.clamp(INF, -INF, INF), 0.0);
        assertEquals(5.0, MathUtil.clamp(INF, -INF, 5.0), 0.0);
        assertEquals(-5.0, MathUtil.clamp(-INF, -5.0, INF), 0.0);
        assertEquals(INF, MathUtil.clamp(2.0, INF, INF), 0.0);
        // NaN maps to the lower bound, even when that bound is infinite, so the result is then not finite
        assertEquals(-INF, MathUtil.clamp(NAN, -INF, 1.0), 0.0);
        assertEquals(0.0, MathUtil.clamp(NAN, 0.0, INF), 0.0);
        // invalid ranges still throw
        assertThrows(IllegalArgumentException.class, () -> MathUtil.clamp(0.0, INF, -INF));
        assertThrows(IllegalArgumentException.class, () -> MathUtil.clamp(0.0, 1.0, 0.0));
        assertThrows(IllegalArgumentException.class, () -> MathUtil.clamp(0.0, NAN, 1.0));
        // result lies within the bounds for a sweep of special values
        double[] values = {-INF, -1e300, -1.0, -0.0, 0.0, Double.MIN_VALUE, 1.0, 1e300, INF, NAN};
        for (double lo : new double[] {-INF, -2.0, 0.0, 3.0}) {
            for (double hi : new double[] {3.0, 7.0, INF}) {
                if (lo > hi) {
                    continue;
                }
                for (double v : values) {
                    double r = MathUtil.clamp(v, lo, hi);
                    assertTrue(r >= lo && r <= hi, "clamp(" + v + ", " + lo + ", " + hi + ") = " + r + " is outside the bounds");
                }
            }
        }
    }

    @Test
    @DisplayName("A: approxEquals is false when the difference is NaN (equal infinities) and never throws")
    void approxEqualsWithInfinities() {
        assertFalse(MathUtil.approxEquals(INF, INF, 1.0), "inf - inf is NaN");
        assertFalse(MathUtil.approxEquals(-INF, -INF, 1.0));
        assertFalse(MathUtil.approxEquals(INF, 1.0, 1e300));
        assertTrue(MathUtil.approxEquals(1.0, 2.0, INF));
        assertFalse(MathUtil.approxEquals(1.0, 2.0, NAN));
    }

    // ------------------------------------------------------------ Clarification B: operand domains

    @Test
    @DisplayName("B: Vec3.mul(s) and Vec3.lerp(to, t) are finite for components and scalars up to 1e100; outside that, IEEE")
    void scalarMultiplierAndInterpolationDomain() {
        SplittableRandom r = new SplittableRandom(SEED);
        for (int i = 0; i < 2_000; i++) {
            Vec3 a = randomVec(r, 10).mul(M / 10);   // components up to 1e100
            Vec3 b = randomVec(r, 10).mul(M / 10);
            for (double s : new double[] {M, -M, 1.0 / M, 0.0, 0.5}) {
                Vec3 m = a.mul(s);
                assertFinite(m, "mul(" + s + ")");
                assertEquals(a.x() * s, m.x(), 0.0, "mul is the plain IEEE product");
            }
            for (double t : new double[] {M, -M, 1e50, 0.0, 1.0, 0.3}) {
                Vec3 l = a.lerp(b, t);
                assertFinite(l, "lerp t=" + t);
                double expectedX = (1 - t) * a.x() + t * b.x();
                // either common formulation agrees to rounding; compare with a tolerance relative to the largest term
                double scale = (Math.abs(1 - t) + Math.abs(t)) * Math.max(Math.abs(a.x()), Math.abs(b.x())) + 1e-300;
                assertEquals(expectedX, l.x(), 1e-13 * scale, "lerp value t=" + t);
            }
        }
        // the extreme corner: component 1e100 with multiplier 1e100 is 1e200, finite
        assertFinite(new Vec3(M, -M, M).mul(M), "corner mul");
        assertFinite(new Vec3(M, -M, M).lerp(new Vec3(-M, M, M), M), "corner lerp");
        assertFinite(new Vec3(M, -M, M).lerp(new Vec3(-M, M, M), -M), "corner lerp negative t");
        // outside the documented domain plain IEEE applies (no saturation): 1e100 * 1e300 overflows
        assertTrue(Double.isInfinite(new Vec3(M, 0, 0).mul(1e300).x()), "outside the domain the product overflows");
        assertTrue(Double.isInfinite(new Vec3(M, 0, 0).lerp(new Vec3(-M, 0, 0), 1e300).x()), "outside the domain lerp overflows");
    }

    @Test
    @DisplayName("B: Quat.mul and Quat.dot are finite for components up to 1e100 (not only unit quaternions)")
    void quaternionComponentDomain() {
        Quat big = new Quat(M, -M, M, -M);
        Quat other = new Quat(-M, M, M, M);
        Quat product = big.mul(other);
        assertTrue(Double.isFinite(product.w()) && Double.isFinite(product.x()) && Double.isFinite(product.y()) && Double.isFinite(product.z()),
                "Quat.mul at the bound: " + product);
        assertTrue(Double.isFinite(big.dot(other)), "Quat.dot at the bound");
        // hand-checked: (M,-M,M,-M) * (-M,M,M,M), scalar part = w1w2 - x1x2 - y1y2 - z1z2 = -M^2 + M^2 - M^2 + M^2 = 0
        assertEquals(0.0, product.w(), 1e-13 * M * M);
        // beyond the domain, IEEE: 1e200 * 1e200 overflows
        assertTrue(Double.isInfinite(new Quat(1e200, 0, 0, 0).mul(new Quat(1e200, 0, 0, 0)).w()));
    }

    @Test
    @DisplayName("B: Pose.toLocal with scale 1e-100 and world components at 1e100 is finite (domain edge)")
    void poseDomainEdges() {
        Pose smallScale = new Pose(new Vec3(M, -M, M), Quat.axisAngle(Vec3.Y, 0.7), 1.0 / M);
        Vec3 world = new Vec3(-M, M, M);
        Vec3 local = smallScale.toLocal(world);
        assertFinite(local, "toLocal at the domain edge");
        assertTrue(maxAbs(local) > 1e199 && maxAbs(local) < 4e200, "magnitude about 1e200, was " + maxAbs(local));
        Pose bigScale = new Pose(new Vec3(M, -M, M), Quat.axisAngle(Vec3.X, -1.1), M);
        assertFinite(bigScale.toWorld(new Vec3(M, M, -M)), "toWorld at the domain edge");
        assertFinite(bigScale.directionToWorld(new Vec3(M, M, -M)), "directionToWorld at the domain edge");
        // in-domain composition and inversion never throw
        Pose a = new Pose(new Vec3(M, M, M), Quat.axisAngle(new Vec3(1, 1, 1), 0.4), M);
        Pose b = new Pose(new Vec3(-M, M, -M), Quat.axisAngle(new Vec3(1, -1, 2), -2.0), M);
        Pose ab = a.then(b);
        assertTrue(ab.scale() > 0.9e200 && ab.scale() < 1.1e200, "composed scale about 1e200, was " + ab.scale());
        assertFinite(ab.translation(), "composed translation at the domain edge");
        Pose c = new Pose(new Vec3(-M, -M, M), Quat.axisAngle(Vec3.Z, 3.0), 1.0 / M);
        Pose cc = c.then(c);
        assertTrue(cc.scale() > 0.0 && cc.scale() < 1.1e-200 && cc.scale() > 0.9e-200, "composed scale about 1e-200, was " + cc.scale());
        assertFinite(a.inverse().translation(), "inverse translation at the domain edge");
        assertFinite(c.inverse().translation(), "inverse translation of the smallest-scale pose");
    }

    @Test
    @DisplayName("B: slerp and nlerp are accurate for finite unit quaternions and |t| up to 1e3 (extrapolation included)")
    void interpolationParameterDomain() {
        SplittableRandom r = new SplittableRandom(SEED + 1);
        for (int i = 0; i < 2_000; i++) {
            Quat q0 = randomRotation(r);
            Vec3 relAxis = randomUnit(r);
            double phi = r.nextDouble(0.3, 2.8) * (r.nextBoolean() ? 1 : -1);
            Quat q1 = q0.mul(Quat.axisAngle(relAxis, phi));
            Vec3 v = randomVec(r, 10);
            for (double t : new double[] {1000.0, -1000.0, 250.5, -3.0, 0.5, 2.0}) {
                Quat s = Quat.slerp(q0, q1, t);
                assertEquals(1.0, norm(s), 1e-12, "slerp unit norm at t=" + t);
                Vec3 expected = q0.rotate(rodrigues(relAxis, t * phi, v));
                assertVec(expected, s.rotate(v), 1e-9 * (1.0 + maxAbs(v)), "slerp action at t=" + t);

                Quat n = Quat.nlerp(q0, q1, t);
                assertEquals(1.0, norm(n), 1e-12, "nlerp unit norm at t=" + t);
                double sign = q0.dot(q1) < 0 ? -1.0 : 1.0;
                double w = (1 - t) * q0.w() + t * sign * q1.w();
                double x = (1 - t) * q0.x() + t * sign * q1.x();
                double y = (1 - t) * q0.y() + t * sign * q1.y();
                double z = (1 - t) * q0.z() + t * sign * q1.z();
                double len = Math.sqrt(w * w + x * x + y * y + z * z);
                Quat expectedN = new Quat(w / len, x / len, y / len, z / len);
                assertSameRotation(expectedN, n, 1e-10, "nlerp value at t=" + t);
            }
        }
    }

    @Test
    @DisplayName("B: axisAngle accepts any finite angle and any finite axis whose largest component is at least 1e-12 (Tier 3 validation only)")
    void axisAngleDomain() {
        Vec3[] axes = {new Vec3(Double.MAX_VALUE, 0, 0), new Vec3(1e300, -1e300, 5), new Vec3(-Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE),
            new Vec3(1e-12, 0, 0), new Vec3(0, -3e-12, 1e-12), new Vec3(1, 2, 3)};
        double[] angles = {0.0, 1.0, -1.0, 1e10, -1e10, 1e300, -Double.MAX_VALUE};
        for (Vec3 axis : axes) {
            for (double angle : angles) {
                Quat q = Quat.axisAngle(axis, angle);
                assertTrue(Double.isFinite(q.w()) && Double.isFinite(q.x()) && Double.isFinite(q.y()) && Double.isFinite(q.z()), "finite for " + axis + ", " + angle);
                assertEquals(1.0, norm(q), 1e-12, "unit for " + axis + ", " + angle);
            }
        }
        // a large but finite angle is the rotation by that exact angle value
        Vec3 v = new Vec3(1, 2, 3);
        assertVec(rodrigues(Vec3.Z, 1e10, v), Quat.axisAngle(Vec3.Z, 1e10).rotate(v), 1e-9, "angle 1e10");
        // non-finite angles and unusable axes are still rejected
        assertThrows(IllegalArgumentException.class, () -> Quat.axisAngle(Vec3.Y, INF));
        assertThrows(IllegalArgumentException.class, () -> Quat.axisAngle(new Vec3(9e-13, 0, 0), 1.0));
        assertThrows(IllegalArgumentException.class, () -> Quat.fromEulerApplyXThenYThenZ(NAN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> Quat.fromEulerApplyXThenYThenZ(0, -INF, 0));
    }
}
