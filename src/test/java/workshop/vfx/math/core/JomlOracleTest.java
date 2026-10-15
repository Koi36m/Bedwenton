package workshop.vfx.math.core;

import static org.junit.jupiter.api.Assertions.fail;
import static workshop.vfx.math.core.TestSupport.assertSameRotation;
import static workshop.vfx.math.core.TestSupport.assertVec;
import static workshop.vfx.math.core.TestSupport.randomRotation;
import static workshop.vfx.math.core.TestSupport.randomVec;

import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Agreement with JOML as an independent reference implementation (test scope only). */
@Tag("joml-oracle")
class JomlOracleTest {
    private static final long SEED = 20261014L;

    @Test
    @DisplayName("Quat.rotate agrees with JOML Quaterniond on 10,000 seeded rotations")
    void rotateAgrees() {
        SplittableRandom r = new SplittableRandom(SEED);
        for (int i = 0; i < 10_000; i++) {
            Quat q = randomRotation(r);
            Vec3 v = randomVec(r, 10);
            assertVec(JomlOracle.rotate(q, v), q.rotate(v), 1e-12, "rotate " + i);
        }
    }

    @Test
    @DisplayName("Quat.mul agrees with JOML's Hamilton product (same operand order)")
    void mulAgrees() {
        SplittableRandom r = new SplittableRandom(SEED + 1);
        for (int i = 0; i < 10_000; i++) {
            Quat a = randomRotation(r);
            Quat b = randomRotation(r);
            Quat mine = a.mul(b);
            Quat theirs = JomlOracle.mul(a, b);
            assertSameRotation(theirs, mine, 1e-12, "mul " + i);
            // components should also agree exactly in sign, since both compute the same product
            if (Math.abs(mine.w() - theirs.w()) > 1e-12 || Math.abs(mine.x() - theirs.x()) > 1e-12
                    || Math.abs(mine.y() - theirs.y()) > 1e-12 || Math.abs(mine.z() - theirs.z()) > 1e-12) {
                fail("mul components differ from JOML at " + i + ": " + mine + " vs " + theirs);
            }
        }
    }

    @Test
    @DisplayName("Quat.slerp agrees with JOML slerp (as rotations) away from the near-parallel regime")
    void slerpAgrees() {
        SplittableRandom r = new SplittableRandom(SEED + 2);
        int compared = 0;
        for (int i = 0; i < 5_000; i++) {
            Quat a = randomRotation(r);
            Quat b = randomRotation(r);
            double angle = a.angleTo(b);
            if (angle < 0.05 || angle > Math.PI - 0.05) {
                continue;
            }
            double t = r.nextDouble();
            assertSameRotation(JomlOracle.slerp(a, b, t), Quat.slerp(a, b, t), 1e-9, "slerp " + i);
            compared++;
        }
        if (compared < 1000) {
            fail("too few slerp comparisons: " + compared);
        }
    }

    @Test
    @DisplayName("Records which application order JOML's rotationXYZ uses; it must match one of the two candidates")
    void jomlEulerOrderIsPinned() {
        SplittableRandom r = new SplittableRandom(SEED + 3);
        boolean xFirst = true;
        boolean zFirst = true;
        for (int i = 0; i < 200; i++) {
            double x = r.nextDouble(-3, 3);
            double y = r.nextDouble(-3, 3);
            double z = r.nextDouble(-3, 3);
            Vec3 v = randomVec(r, 5);
            Vec3 jomlResult = JomlOracle.rotationXYZ(x, y, z).rotate(v);
            Vec3 applyXThenYThenZ = Quat.fromEulerApplyXThenYThenZ(x, y, z).rotate(v);
            Vec3 applyZThenYThenX = Quat.axisAngle(Vec3.X, x).mul(Quat.axisAngle(Vec3.Y, y)).mul(Quat.axisAngle(Vec3.Z, z)).rotate(v);
            xFirst &= close(jomlResult, applyXThenYThenZ);
            zFirst &= close(jomlResult, applyZThenYThenX);
        }
        System.out.println("[M1 JOML report] rotationXYZ applies X first = " + xFirst + ", applies Z first = " + zFirst);
        if (!xFirst && !zFirst) {
            fail("JOML rotationXYZ matches neither X-then-Y-then-Z nor Z-then-Y-then-X; investigate before relying on it");
        }
    }

    private static boolean close(Vec3 a, Vec3 b) {
        return Math.abs(a.x() - b.x()) < 1e-9 && Math.abs(a.y() - b.y()) < 1e-9 && Math.abs(a.z() - b.z()) < 1e-9;
    }
}
