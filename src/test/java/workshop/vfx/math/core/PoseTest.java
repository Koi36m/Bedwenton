package workshop.vfx.math.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static workshop.vfx.math.core.TestSupport.assertSameRotation;
import static workshop.vfx.math.core.TestSupport.assertVec;
import static workshop.vfx.math.core.TestSupport.golden;
import static workshop.vfx.math.core.TestSupport.randomRotation;
import static workshop.vfx.math.core.TestSupport.randomVec;

import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PoseTest {
    private static final long SEED = 20261014L;
    private static final double TOL = 1e-11;

    private static Pose randomPose(SplittableRandom r, double translationRange) {
        return new Pose(randomVec(r, translationRange), randomRotation(r), r.nextDouble(0.1, 4.0));
    }

    private static Pose parent(double[] row) {
        return new Pose(new Vec3(row[0], row[1], row[2]), Quat.axisAngle(new Vec3(row[3], row[4], row[5]), row[6]), row[7]);
    }

    private static Pose child(double[] row) {
        return new Pose(new Vec3(row[8], row[9], row[10]), Quat.axisAngle(new Vec3(row[11], row[12], row[13]), row[14]), row[15]);
    }

    // ------------------------------------------------------------ golden

    @Test
    @DisplayName("Golden: parent.then(child).toWorld(p) equals the Python oracle (1,500 rows)")
    void goldenComposition() {
        List<double[]> rows = golden("pose_cases.csv");
        assertTrue(rows.size() >= 1500, "unexpected golden row count " + rows.size());
        int n = 0;
        for (double[] row : rows) {
            Pose p = parent(row);
            Pose c = child(row);
            Vec3 point = new Vec3(row[16], row[17], row[18]);
            Vec3 expected = new Vec3(row[19], row[20], row[21]);
            assertVec(expected, p.then(c).toWorld(point), TOL, "composed row " + n);
            assertVec(expected, p.toWorld(c.toWorld(point)), TOL, "sequential row " + n);
            assertVec(point, p.then(c).toLocal(expected), 1e-9, "toLocal row " + n);
            n++;
        }
    }

    @Test
    @DisplayName("Golden: large coordinates near +/-3e7 blocks stay accurate to 1e-7 blocks")
    void goldenLargeCoordinates() {
        List<double[]> rows = golden("pose_large_cases.csv");
        assertTrue(rows.size() >= 500, "unexpected golden row count " + rows.size());
        int n = 0;
        for (double[] row : rows) {
            Pose p = parent(row);
            Pose c = child(row);
            Vec3 point = new Vec3(row[16], row[17], row[18]);
            Vec3 expected = new Vec3(row[19], row[20], row[21]);
            assertVec(expected, p.then(c).toWorld(point), 1e-7, "large composed row " + n);
            assertVec(point, p.then(c).toLocal(expected), 1e-6, "large toLocal row " + n);
            n++;
        }
    }

    // ------------------------------------------------------------ algebra

    @Test
    @DisplayName("Identity pose and pure translation")
    void identityAndTranslation() {
        Vec3 p = new Vec3(3, -4, 5);
        assertVec(p, Pose.IDENTITY.toWorld(p), 0.0, "identity toWorld");
        assertVec(p, Pose.IDENTITY.toLocal(p), 0.0, "identity toLocal");
        Pose t = Pose.ofTranslation(new Vec3(10, 20, 30));
        assertVec(new Vec3(13, 16, 35), t.toWorld(p), 0.0, "translation toWorld");
        assertEquals(1.0, t.scale(), 0.0);
    }

    @Test
    @DisplayName("toLocal(toWorld(p)) = p on 10,000 random poses")
    void roundTrip() {
        SplittableRandom r = new SplittableRandom(SEED);
        for (int i = 0; i < 10_000; i++) {
            Pose pose = randomPose(r, 1000);
            Vec3 p = randomVec(r, 100);
            assertVec(p, pose.toLocal(pose.toWorld(p)), 1e-9, "round trip");
            Vec3 w = randomVec(r, 100);
            assertVec(w, pose.toWorld(pose.toLocal(w)), 1e-9, "reverse round trip");
        }
    }

    @Test
    @DisplayName("a.then(b) applied equals b first then a; associativity")
    void compositionOrderAndAssociativity() {
        SplittableRandom r = new SplittableRandom(SEED + 1);
        for (int i = 0; i < 5_000; i++) {
            Pose a = randomPose(r, 50);
            Pose b = randomPose(r, 50);
            Pose c = randomPose(r, 50);
            Vec3 p = randomVec(r, 20);
            assertVec(a.toWorld(b.toWorld(p)), a.then(b).toWorld(p), 1e-9, "then = sequential");
            assertVec(a.then(b).then(c).toWorld(p), a.then(b.then(c)).toWorld(p), 1e-8, "associativity");
        }
        // The two orders really differ, so the test above is meaningful.
        Pose a = new Pose(new Vec3(10, 0, 0), Quat.axisAngle(Vec3.Y, Math.PI / 2), 1.0);
        Pose b = new Pose(new Vec3(1, 0, 0), Quat.IDENTITY, 1.0);
        assertVec(new Vec3(10, 0, -1), a.then(b).translation(), TOL, "a.then(b)");
        assertVec(new Vec3(11, 0, 0), b.then(a).translation(), TOL, "b.then(a)");
    }

    @Test
    @DisplayName("Five-deep chain equals nested sequential application; scale and rotation compose")
    void deepChain() {
        SplittableRandom r = new SplittableRandom(SEED + 2);
        for (int i = 0; i < 2_000; i++) {
            Pose[] chain = new Pose[5];
            for (int k = 0; k < chain.length; k++) {
                chain[k] = randomPose(r, 20);
            }
            Vec3 p = randomVec(r, 10);
            Vec3 nested = p;
            for (int k = chain.length - 1; k >= 0; k--) {
                nested = chain[k].toWorld(nested);
            }
            Pose combined = chain[0];
            for (int k = 1; k < chain.length; k++) {
                combined = combined.then(chain[k]);
            }
            assertVec(nested, combined.toWorld(p), 1e-8, "chain");
            double scale = 1.0;
            Quat rotation = Quat.IDENTITY;
            for (Pose c : chain) {
                scale *= c.scale();
                rotation = rotation.mul(c.rotation());
            }
            assertEquals(scale, combined.scale(), 1e-9 * scale);
            assertSameRotation(rotation, combined.rotation(), 1e-11, "rotation product");
        }
    }

    @Test
    @DisplayName("inverse: round trip, composition with the original is the identity")
    void inverse() {
        SplittableRandom r = new SplittableRandom(SEED + 3);
        for (int i = 0; i < 5_000; i++) {
            Pose pose = randomPose(r, 200);
            Pose inv = pose.inverse();
            Vec3 p = randomVec(r, 50);
            assertVec(p, inv.toWorld(pose.toWorld(p)), 1e-9, "inverse after pose");
            assertVec(p, pose.toWorld(inv.toWorld(p)), 1e-9, "pose after inverse");
            Pose identity = pose.then(inv);
            assertVec(Vec3.ZERO, identity.translation(), 1e-8, "translation of pose.then(inverse)");
            assertEquals(1.0, identity.scale(), 1e-12);
            assertSameRotation(Quat.IDENTITY, identity.rotation(), 1e-11, "rotation of pose.then(inverse)");
            // toLocal is exactly the inverse mapping
            assertVec(inv.toWorld(p), pose.toLocal(p), 1e-9, "toLocal equals inverse().toWorld");
        }
    }

    @Test
    @DisplayName("directionToWorld rotates only: ignores translation and scale")
    void directionToWorld() {
        SplittableRandom r = new SplittableRandom(SEED + 4);
        for (int i = 0; i < 2_000; i++) {
            Pose pose = randomPose(r, 1000);
            Vec3 d = randomVec(r, 5);
            assertVec(pose.rotation().rotate(d), pose.directionToWorld(d), 1e-12, "direction");
            assertEquals(d.length(), pose.directionToWorld(d).length(), 1e-11, "length preserved");
        }
    }

    // ------------------------------------------------------------ validation (M0: Astral Spec rejects NaN)

    @Test
    @DisplayName("Constructor validation: non-finite translation, bad scale, bad rotation")
    void validation() {
        Quat id = Quat.IDENTITY;
        Vec3 t = new Vec3(1, 2, 3);
        assertThrows(IllegalArgumentException.class, () -> new Pose(new Vec3(Double.NaN, 0, 0), id, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new Pose(new Vec3(0, Double.POSITIVE_INFINITY, 0), id, 1.0));
        assertThrows(IllegalArgumentException.class, () -> new Pose(t, id, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new Pose(t, id, -1.0));
        assertThrows(IllegalArgumentException.class, () -> new Pose(t, id, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new Pose(t, id, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new Pose(t, new Quat(0, 0, 0, 0), 1.0));
        assertThrows(IllegalArgumentException.class, () -> new Pose(t, new Quat(Double.NaN, 0, 0, 1), 1.0));
        assertThrows(IllegalArgumentException.class, () -> new Pose(t, new Quat(1, Double.POSITIVE_INFINITY, 0, 0), 1.0));
    }

    @Test
    @DisplayName("Constructor normalizes the rotation")
    void rotationIsNormalized() {
        Pose p = new Pose(Vec3.ZERO, new Quat(2, 0, 0, 0), 1.0);
        Quat q = p.rotation();
        assertEquals(1.0, Math.sqrt(q.w() * q.w() + q.x() * q.x() + q.y() * q.y() + q.z() * q.z()), 1e-15);
        Quat half = Quat.axisAngle(Vec3.Y, 1.0);
        Pose scaled = new Pose(Vec3.ZERO, new Quat(half.w() * 9, half.x() * 9, half.y() * 9, half.z() * 9), 1.0);
        assertVec(half.rotate(new Vec3(1, 2, 3)), scaled.toWorld(new Vec3(1, 2, 3)), 1e-12, "scaled quaternion acts as the unit rotation");
    }

    // ------------------------------------------------------------ large coordinates

    @Test
    @DisplayName("Round trip at the Minecraft world edge (29,999,984) stays within 1e-6 blocks")
    void worldEdgeRoundTrip() {
        SplittableRandom r = new SplittableRandom(SEED + 5);
        double edge = 29_999_984.0;
        for (int i = 0; i < 5_000; i++) {
            Pose pose = new Pose(new Vec3(edge * (r.nextBoolean() ? 1 : -1), 70, edge * (r.nextBoolean() ? 1 : -1)),
                    randomRotation(r), r.nextDouble(0.25, 4.0));
            Vec3 local = randomVec(r, 100);
            Vec3 world = pose.toWorld(local);
            assertVec(local, pose.toLocal(world), 1e-6, "world edge round trip");
        }
    }

    @Test
    @DisplayName("Why local-space doubles matter: float spacing is 2 blocks near the world edge and 0.0625 blocks at 1e6")
    void floatSpacingDocumentation() {
        assertEquals(2.0f, Math.ulp((float) 29_999_984.0), 0.0f);
        assertEquals(0.0625f, Math.ulp((float) 1_000_000.0), 0.0f);
        // The same small offset that doubles resolve exactly is destroyed by a float at the world edge.
        double edge = 29_999_984.0;
        double offset = 0.3;
        double viaDouble = (edge + offset) - edge;
        double viaFloat = (double) ((float) (edge + offset)) - (double) (float) edge;
        assertEquals(offset, viaDouble, 1e-8);
        assertTrue(Math.abs(viaFloat - offset) > 0.2, "float lost the offset: " + viaFloat);
    }
}
