package workshop.vfx.math.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static workshop.vfx.math.core.TestSupport.assertFinite;
import static workshop.vfx.math.core.TestSupport.assertVec;
import static workshop.vfx.math.core.TestSupport.randomVec;

import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class Vec3Test {
    private static final long SEED = 20261014L;
    private static final double TOL = 1e-12;

    @Test
    @DisplayName("Constants and basic arithmetic")
    void basics() {
        Vec3 a = new Vec3(1, 2, 3);
        Vec3 b = new Vec3(-4, 5, 0.5);
        assertVec(new Vec3(-3, 7, 3.5), a.add(b), 0.0, "add");
        assertVec(new Vec3(5, -3, 2.5), a.sub(b), 0.0, "sub");
        assertVec(new Vec3(2, 4, 6), a.mul(2), 0.0, "mul");
        assertVec(new Vec3(-1, -2, -3), a.negate(), 0.0, "negate");
        assertEquals(-4 + 10 + 1.5, a.dot(b), 0.0);
        assertEquals(14.0, a.lengthSquared(), 0.0);
        assertEquals(Math.sqrt(14.0), a.length(), TOL);
        assertEquals(5.0, new Vec3(3, 4, 0).length(), TOL);
        assertEquals(5.0, new Vec3(0, 0, 0).distance(new Vec3(0, 3, 4)), TOL);
        assertVec(new Vec3(0, 0, 0), Vec3.ZERO, 0.0, "ZERO");
        assertVec(new Vec3(1, 0, 0), Vec3.X, 0.0, "X");
        assertVec(new Vec3(0, 1, 0), Vec3.Y, 0.0, "Y");
        assertVec(new Vec3(0, 0, 1), Vec3.Z, 0.0, "Z");
    }

    @Test
    @DisplayName("Cross product: hand-derived values and right-hand rule")
    void crossHandValues() {
        assertVec(Vec3.Z, Vec3.X.cross(Vec3.Y), 0.0, "X x Y");
        assertVec(Vec3.X, Vec3.Y.cross(Vec3.Z), 0.0, "Y x Z");
        assertVec(Vec3.Y, Vec3.Z.cross(Vec3.X), 0.0, "Z x X");
        assertVec(Vec3.Z.negate(), Vec3.Y.cross(Vec3.X), 0.0, "Y x X");
        assertVec(new Vec3(-3, 6, -3), new Vec3(1, 2, 3).cross(new Vec3(4, 5, 6)), 0.0, "(1,2,3) x (4,5,6)");
    }

    @Test
    @DisplayName("Algebraic identities on 10,000 seeded random vectors")
    void identities() {
        SplittableRandom r = new SplittableRandom(SEED);
        for (int i = 0; i < 10_000; i++) {
            Vec3 a = randomVec(r, 10);
            Vec3 b = randomVec(r, 10);
            Vec3 c = randomVec(r, 10);
            Vec3 axb = a.cross(b);
            double scale = a.length() * b.length() + 1.0;
            assertEquals(0.0, axb.dot(a), 1e-12 * scale * scale);
            assertEquals(0.0, axb.dot(b), 1e-12 * scale * scale);
            // Lagrange: |a x b|^2 + (a.b)^2 = |a|^2 |b|^2
            double lhs = axb.lengthSquared() + a.dot(b) * a.dot(b);
            double rhs = a.lengthSquared() * b.lengthSquared();
            assertEquals(rhs, lhs, 1e-12 * (rhs + 1.0));
            // Scalar triple product is cyclic: a.(b x c) = b.(c x a)
            assertEquals(a.dot(b.cross(c)), b.dot(c.cross(a)), 1e-12 * 1e3);
            // Anti-commutativity
            assertVec(axb.negate(), b.cross(a), 1e-12 * scale, "anti-commutative");
            // Triangle inequality
            assertTrue(a.add(b).length() <= a.length() + b.length() + 1e-12, "triangle inequality");
        }
    }

    @Test
    @DisplayName("lerp: endpoints, midpoint, extrapolation (no clamping)")
    void lerp() {
        Vec3 a = new Vec3(1, 2, 3);
        Vec3 b = new Vec3(3, 6, 11);
        assertVec(a, a.lerp(b, 0.0), 0.0, "t=0");
        assertVec(b, a.lerp(b, 1.0), 0.0, "t=1");
        assertVec(new Vec3(2, 4, 7), a.lerp(b, 0.5), TOL, "t=0.5");
        assertVec(new Vec3(5, 10, 19), a.lerp(b, 2.0), TOL, "t=2 extrapolates");
        assertVec(new Vec3(-1, -2, -5), a.lerp(b, -1.0), TOL, "t=-1 extrapolates");
    }

    @Test
    @DisplayName("isFinite detects NaN and infinities; Vec3 itself never validates")
    void finiteness() {
        assertTrue(new Vec3(1, 2, 3).isFinite());
        assertFalse(new Vec3(Double.NaN, 0, 0).isFinite());
        assertFalse(new Vec3(0, Double.POSITIVE_INFINITY, 0).isFinite());
        assertFalse(new Vec3(0, 0, Double.NEGATIVE_INFINITY).isFinite());
    }

    @Test
    @DisplayName("normalizedOr returns unit vectors for ordinary input")
    void normalizeOrdinary() {
        Vec3 fb = new Vec3(0, 1, 0);
        assertVec(new Vec3(0.6, 0.8, 0), new Vec3(3, 4, 0).normalizedOr(fb), TOL, "(3,4,0)");
        assertVec(new Vec3(0, 0, -1), new Vec3(0, 0, -7.5).normalizedOr(fb), TOL, "(0,0,-7.5)");
        SplittableRandom r = new SplittableRandom(SEED + 1);
        for (int i = 0; i < 10_000; i++) {
            Vec3 v = randomVec(r, 100);
            if (v.length() < 1e-6) {
                continue;
            }
            Vec3 n = v.normalizedOr(fb);
            assertEquals(1.0, n.length(), TOL);
            assertVec(v.mul(1.0 / v.length()), n, 1e-12, "direction preserved");
        }
    }

    @Test
    @DisplayName("normalizedOr returns the fallback for zero, tiny, NaN and infinite input (M0: Full Heal outward fallback)")
    void normalizeDegenerate() {
        Vec3 fb = new Vec3(0.25, 0.5, 0.75);
        Vec3[] degenerate = {
            new Vec3(0, 0, 0),
            new Vec3(-0.0, 0.0, -0.0),
            new Vec3(1e-13, 0, 0),
            new Vec3(0, -9e-13, 0),
            new Vec3(1e-300, 1e-300, 1e-300),
            new Vec3(Double.MIN_VALUE, 0, 0),
            new Vec3(Double.NaN, 1, 1),
            new Vec3(1, Double.POSITIVE_INFINITY, 1),
            new Vec3(1, 1, Double.NEGATIVE_INFINITY),
        };
        for (Vec3 v : degenerate) {
            assertSame(fb, v.normalizedOr(fb), "fallback must be returned as given for " + v);
        }
    }

    @Test
    @DisplayName("normalizedOr is accurate at both ends of the finite range")
    void normalizeExtremeMagnitudes() {
        Vec3 fb = new Vec3(0, 1, 0);
        assertVec(new Vec3(1, 0, 0), new Vec3(1e200, 0, 0).normalizedOr(fb), TOL, "1e200 (squares overflow)");
        assertVec(new Vec3(0.6, 0.8, 0), new Vec3(3e150, 4e150, 0).normalizedOr(fb), TOL, "3e150,4e150");
        assertVec(new Vec3(1, 0, 0), new Vec3(1e-11, 0, 0).normalizedOr(fb), TOL, "1e-11 is above the 1e-12 threshold");
        assertVec(new Vec3(0, 0, 1), new Vec3(0, 0, 3e-12).normalizedOr(fb), TOL, "3e-12 is above the threshold");
        Vec3 n = new Vec3(1e300, -1e300, 1e300).normalizedOr(fb);
        assertFinite(n, "1e300 triple");
        assertEquals(1.0, n.length(), TOL);
    }

    @Test
    @DisplayName("Full Heal outward-direction pattern: zero offset yields a finite unit fallback for any yaw")
    void outwardFallbackPattern() {
        for (double yaw : new double[] {0, Math.PI / 2, Math.PI, -1.3}) {
            Vec3 fallback = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
            Vec3 out = new Vec3(0, 0, 0).normalizedOr(fallback);
            assertEquals(1.0, out.length(), TOL);
            assertFinite(out, "outward fallback at yaw " + yaw);
        }
        assertVec(new Vec3(0.6, 0, 0.8), new Vec3(3, 0, 4).normalizedOr(Vec3.X), TOL, "ordinary outward");
    }
}
