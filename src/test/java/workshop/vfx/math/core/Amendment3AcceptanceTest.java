package workshop.vfx.math.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static workshop.vfx.math.core.TestSupport.assertFrameValid;
import static workshop.vfx.math.core.TestSupport.assertVec;
import static workshop.vfx.math.core.TestSupport.randomRotation;
import static workshop.vfx.math.core.TestSupport.randomUnit;
import static workshop.vfx.math.core.TestSupport.rodrigues;

import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Executable acceptance criteria for docs/AMENDMENT-3-REVIEW-DECISIONS.md (contract revision 4, approved 2026-10-15).
 * Replaces the disabled ReviewFindingsTest. Each test names the decision it pins.
 *
 * <p>Against 8397c04, exactly three tests fail until Codex applies the approved code changes:
 * {@code d1InverseScaleFailureNamesScale}, {@code d1ThenScaleFailureNamesScale} and
 * {@code a3NullAxisIsIllegalArgument}. All other tests pin behaviour the implementation already has.
 */
@Tag("amendment-3")
class Amendment3AcceptanceTest {
    private static final long SEED = 0xA3_2026_1015L;

    // ------------------------------------------------------------ D1: exception attribution (section 5 [R4])

    @Test
    @DisplayName("D1: Pose.inverse names the scale when 1/s is not representable, whatever the translation")
    void d1InverseScaleFailureNamesScale() {
        for (double s : new double[] {1e-310, Double.MIN_VALUE, 1 / Double.MAX_VALUE}) {
            for (Vec3 t : new Vec3[] {Vec3.ZERO, new Vec3(1, 0, 0), new Vec3(-3e7, 64, 3e7)}) {
                ArithmeticException e = assertThrows(ArithmeticException.class, () -> new Pose(t, Quat.IDENTITY, s).inverse());
                assertTrue(e.getMessage().contains("Pose.inverse"), "operation named: " + e.getMessage());
                assertTrue(e.getMessage().contains("scale"), "scale named for s=" + s + ", t=" + t + ": " + e.getMessage());
            }
        }
    }

    @Test
    @DisplayName("D1: Pose.then names the scale when the scale product is not representable, even if the translation also overflows")
    void d1ThenScaleFailureNamesScale() {
        Pose big = new Pose(Vec3.ZERO, Quat.IDENTITY, 1e200);
        Pose bigFar = new Pose(new Vec3(1e200, 0, 0), Quat.IDENTITY, 1e200); // t = 1e400 and s = 1e400 both overflow
        ArithmeticException both = assertThrows(ArithmeticException.class, () -> big.then(bigFar));
        assertTrue(both.getMessage().contains("Pose.then") && both.getMessage().contains("scale"), both.getMessage());
        Pose tiny = new Pose(Vec3.ZERO, Quat.IDENTITY, 1e-200);
        ArithmeticException under = assertThrows(ArithmeticException.class, () -> tiny.then(tiny)); // 1e-400 underflows to 0
        assertTrue(under.getMessage().contains("scale"), under.getMessage());
    }

    @Test
    @DisplayName("D1: a translation-only failure names the translation, not the scale; the set of throwing inputs is unchanged")
    void d1TranslationFailureNamesTranslation() {
        ArithmeticException then = assertThrows(ArithmeticException.class,
                () -> new Pose(Vec3.ZERO, Quat.IDENTITY, 1e200).then(new Pose(new Vec3(1e200, 0, 0), Quat.IDENTITY, 1)));
        assertTrue(then.getMessage().contains("translation") && !then.getMessage().contains("scale"), then.getMessage());
        ArithmeticException inv = assertThrows(ArithmeticException.class,
                () -> new Pose(new Vec3(1e200, 0, 0), Quat.IDENTITY, 1e-200).inverse());
        assertTrue(inv.getMessage().contains("translation") && !inv.getMessage().contains("scale"), inv.getMessage());
        // representable results still do not throw (behaviour unchanged by the fix)
        assertEquals(1e-320, new Pose(Vec3.ZERO, Quat.IDENTITY, 1e-160).then(new Pose(Vec3.ZERO, Quat.IDENTITY, 1e-160)).scale(), 0.0);
        assertEquals(1e-100, new Pose(new Vec3(1e100, 0, 0), Quat.IDENTITY, 1e100).inverse().scale(), 1e-114);
    }

    // ------------------------------------------------------------ A1: transportY tolerance (section 4 [R4])

    @Test
    @DisplayName("A1: transportY's y is normalize(newY) to 1e-15 per component (not bitwise), including after antiparallel flips")
    void a1TransportYAxisTolerance() {
        Vec3 newY = new Vec3(2, 3, 7);
        assertVec(newY.normalizedOr(Vec3.Y), Frame.transportY(Frame.WORLD, newY).y(), 1e-15, "the review's (2, 3, 7) example");
        SplittableRandom r = new SplittableRandom(SEED);
        Frame f = Frame.WORLD;
        Vec3 dir = Vec3.Y;
        for (int i = 0; i < 100_000; i++) {
            dir = rodrigues(randomUnit(r), r.nextDouble(-0.3, 0.3), dir);
            if (i % 500 == 0) {
                dir = dir.negate();
            }
            Vec3 y = dir.mul(Math.pow(10, r.nextInt(-11, 300)));
            f = Frame.transportY(f, y);
            assertVec(y.normalizedOr(Vec3.Y), f.y(), 1e-15, "step " + i);
            assertFrameValid(f, 1e-12, "step " + i);
        }
    }

    // ------------------------------------------------------------ A2: rotation equality (section 3 [R4])

    @Test
    @DisplayName("A2: the inverse of a degenerate quaternion is the identity rotation numerically; signed zeros are equivalent")
    void a2DegenerateInverseIsIdentityRotation() {
        Quat[] degenerate = {new Quat(0, 0, 0, 0), new Quat(-0.0, -0.0, -0.0, -0.0), new Quat(Double.NaN, 0, 0, 0),
                new Quat(1, Double.POSITIVE_INFINITY, 0, 0), new Quat(0, 0, Double.NEGATIVE_INFINITY, 0)};
        Vec3 v = new Vec3(0.3, -2, 5);
        for (Quat q : degenerate) {
            Quat inv = q.inverse();
            assertTrue(inv.w() == 1.0 && inv.x() == 0.0 && inv.y() == 0.0 && inv.z() == 0.0, "identity components (== treats -0.0 as 0.0): " + inv);
            assertVec(v, inv.rotate(v), 0.0, "acts as the identity");
            assertEquals(0.0, inv.angleTo(Quat.IDENTITY), 0.0, "angle to IDENTITY");
        }
        // record equality is structural, not rotational: q and -q are one rotation but unequal records
        Quat q = new Quat(0.5, 0.5, 0.5, 0.5);
        Quat minus = new Quat(-0.5, -0.5, -0.5, -0.5);
        assertFalse(q.equals(minus), "record equality distinguishes q and -q");
        assertEquals(0.0, q.angleTo(minus), 1e-12, "but they are the same rotation");
    }

    // ------------------------------------------------------------ A3: null axis (section 0A [R4])

    @Test
    @DisplayName("A3: Tier 3 inputs reject null with IllegalArgumentException (axisAngle axis, Frame axes, Pose fields)")
    void a3NullAxisIsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> Quat.axisAngle(null, 1.0));
        assertThrows(IllegalArgumentException.class, () -> Quat.axisAngle(null, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new Frame(null, Vec3.Y, Vec3.Z));
        assertThrows(IllegalArgumentException.class, () -> new Pose(null, Quat.IDENTITY, 1));
        assertThrows(IllegalArgumentException.class, () -> new Pose(Vec3.ZERO, null, 1));
    }

    // ------------------------------------------------------------ A4: absent hints (section 4 [R4])

    @Test
    @DisplayName("A4: a hint that is null, non-finite or below LENGTH_EPS is absent; exactly LENGTH_EPS is present")
    void a4TinyHintsAreAbsent() {
        // alignY(+Z, hint): absent -> least-aligned world axis of +Z is +X (ties X, Y, Z), so x = +X
        Frame expectedAbsent = new Frame(Vec3.X, Vec3.Z, new Vec3(0, -1, 0));
        for (Vec3 hint : new Vec3[] {null, Vec3.ZERO, new Vec3(0, 1e-13, 0), new Vec3(0, Math.nextDown(MathUtil.LENGTH_EPS), 0), new Vec3(0, Double.NaN, 0)}) {
            assertFrameEquals(expectedAbsent, Frame.alignY(Vec3.Z, hint), "alignY(+Z, " + hint + ")");
        }
        assertFrameEquals(new Frame(Vec3.Y, Vec3.Z, Vec3.X), Frame.alignY(Vec3.Z, new Vec3(0, 1e-12, 0)), "alignY(+Z, (0, 1e-12, 0)) honours the hint");
        // alignZ(+X, hint): absent -> least-aligned world axis of +X is +Y, so y = +Y and x = y x z = -Z
        assertFrameEquals(new Frame(new Vec3(0, 0, -1), Vec3.Y, Vec3.X), Frame.alignZ(Vec3.X, new Vec3(0, 0, 1e-13)), "alignZ(+X, tiny +Z)");
        assertFrameEquals(new Frame(Vec3.Y, Vec3.Z, Vec3.X), Frame.alignZ(Vec3.X, new Vec3(0, 0, 1e-12)), "alignZ(+X, (0, 0, 1e-12)) honours the hint");
        // fromHeading: an absent up hint means +Y
        Frame southUp = new Frame(new Vec3(-1, 0, 0), Vec3.Y, new Vec3(0, 0, -1));
        for (Vec3 up : new Vec3[] {null, Vec3.ZERO, new Vec3(1e-13, 0, 0), new Vec3(Double.POSITIVE_INFINITY, 0, 0)}) {
            assertFrameEquals(southUp, Frame.fromHeading(Vec3.Z, up), "fromHeading(+Z, " + up + ")");
        }
        assertFrameEquals(new Frame(Vec3.Y, Vec3.X, new Vec3(0, 0, -1)), Frame.fromHeading(Vec3.Z, new Vec3(1e-12, 0, 0)), "fromHeading(+Z, (1e-12, 0, 0)) honours the hint");
    }

    private static void assertFrameEquals(Frame expected, Frame actual, String label) {
        assertFrameNear(expected, actual, 1e-12, label);
    }

    private static void assertFrameNear(Frame expected, Frame actual, double tol, String label) {
        assertVec(expected.x(), actual.x(), tol, label + " x");
        assertVec(expected.y(), actual.y(), tol, label + " y");
        assertVec(expected.z(), actual.z(), tol, label + " z");
    }

    // ------------------------------------------------------------ A5: fromHeading parallel rule (section 4 [R4])

    @Test
    @DisplayName("A5: fromHeading's parallel test uses the sine of the angle between the normalized vectors; worked examples")
    void a5FromHeadingExamples() {
        // facing south, up +Y
        assertFrameEquals(new Frame(new Vec3(-1, 0, 0), Vec3.Y, new Vec3(0, 0, -1)), Frame.fromHeading(Vec3.Z, Vec3.Y), "south");
        // straight up, up +Y: parallel, fallback up hint = least-aligned axis of +Y = +X
        assertFrameEquals(new Frame(new Vec3(0, 0, -1), Vec3.X, new Vec3(0, -1, 0)), Frame.fromHeading(Vec3.Y, Vec3.Y), "straight up");
        // sine 1e-6 >= 1e-8: the hint +Y is honoured, independent of the input lengths (review example A5)
        Frame honoured = new Frame(Vec3.Z, new Vec3(-0.9999999999995, 9.999999999995e-7, 0), new Vec3(-9.999999999995e-7, -0.9999999999995, 0));
        assertFrameEquals(honoured, Frame.fromHeading(new Vec3(1e-9, 1e-3, 0), Vec3.Y), "short forward, sine 1e-6");
        assertFrameEquals(honoured, Frame.fromHeading(new Vec3(1e-3, 1e3, 0), new Vec3(0, 7, 0)), "long forward, sine 1e-6");
        // sine 1e-9 < 1e-8: parallel; least-aligned axis of (1e-9, 1, 0) is +Z
        assertFrameEquals(new Frame(new Vec3(1, -1e-9, 0), Vec3.Z, new Vec3(-1e-9, -1, 0)), Frame.fromHeading(new Vec3(1e-9, 1, 0), Vec3.Y), "sine 1e-9");
        assertFrameEquals(new Frame(new Vec3(1, -1e-9, 0), Vec3.Z, new Vec3(-1e-9, -1, 0)), Frame.fromHeading(new Vec3(1e-7, 1e2, 0), new Vec3(0, 1e-5, 0)), "sine 1e-9, other lengths");
    }

    @Test
    @DisplayName("A5: fromHeading(f, u) depends only on the directions of f and u, and equals alignZ(-f, u) with an absent u read as +Y")
    void a5FromHeadingIsScaleInvariant() {
        SplittableRandom r = new SplittableRandom(SEED + 1);
        for (int i = 0; i < 50_000; i++) {
            Vec3 f = randomUnit(r);
            Vec3 u = r.nextInt(4) == 0 ? f.add(randomUnit(r).mul(Math.pow(10, -r.nextInt(5, 12)))).normalizedOr(Vec3.Y) : randomUnit(r);
            double sine = f.cross(u).length();
            if (sine > 0.5e-8 && sine < 2e-8) {
                continue; // too close to the threshold for rounding of the inputs to decide consistently
            }
            Frame reference = Frame.fromHeading(f, u);
            Frame scaled = Frame.fromHeading(f.mul(Math.pow(10, r.nextInt(-11, 300))), u.mul(Math.pow(10, r.nextInt(-11, 300))));
            // rescaling perturbs the normalized inputs by ulps, which turns the perpendicular by about 1e-16 / sine
            double tol = sine < 1e-8 ? 1e-12 : 1e-12 + 1e-14 / sine;
            assertFrameNear(reference, scaled, tol, "scale invariance, sine " + sine);
            assertFrameNear(Frame.alignZ(f.negate(), u), reference, tol, "alignZ(-f, u), sine " + sine);
            assertVec(reference.z(), reference.x().cross(reference.y()), 1e-12, "right x up = back");
        }
    }

    // ------------------------------------------------------------ A6: rotate bound (sections 0A, 6 [R4])

    @Test
    @DisplayName("A6: rotate(v) is within (2|k^2 - 1| + 1e-13)|v| of the rotation by q/|q|, k = |q| within 1e-12 of 1, and finite for |v| <= 1e100")
    void a6RotateBoundForNearUnitQuaternions() {
        // the review example: half turn about +Z with norm 1 + 1e-12
        double k0 = 1 + 1e-12;
        Vec3 img = new Quat(0, 0, 0, k0).rotate(Vec3.X);
        double bound0 = 2 * Math.abs(k0 * k0 - 1) + 1e-13;
        assertTrue(Math.abs(img.x() + 1) <= bound0, "example: error " + Math.abs(img.x() + 1) + " > bound " + bound0);
        SplittableRandom r = new SplittableRandom(SEED + 2);
        for (int i = 0; i < 100_000; i++) {
            Vec3 axis = randomUnit(r);
            double angle = r.nextDouble(-Math.PI, Math.PI);
            Quat unit = r.nextBoolean() ? Quat.axisAngle(axis, angle) : randomRotation(r);
            double k = 1 + r.nextDouble(-1e-12, 1e-12);
            Quat q = new Quat(unit.w() * k, unit.x() * k, unit.y() * k, unit.z() * k);
            double mag = Math.pow(10, r.nextInt(-100, 101));
            Vec3 v = randomUnit(r).mul(mag);
            Vec3 actual = q.rotate(v);
            assertTrue(actual.isFinite(), "finite inside the domain");
            Vec3 expected = unit.rotate(v); // reference: the same formula on the (already unit) quaternion
            Vec3 viaRodrigues = rodrigues(new Vec3(unit.x(), unit.y(), unit.z()).normalizedOr(Vec3.X),
                    2 * Math.atan2(Math.sqrt(unit.x() * unit.x() + unit.y() * unit.y() + unit.z() * unit.z()), unit.w()), v);
            assertVec(viaRodrigues, expected, 1e-13 * mag, "unit reference agrees with Rodrigues");
            double bound = (2 * Math.abs(k * k - 1) * (1 + 1e-9) + 1e-13) * mag;
            assertVec(viaRodrigues, actual, bound, "k=" + k + " |v|=" + mag);
        }
    }
}
