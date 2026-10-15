package workshop.vfx.math.core;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Shared test helpers. Everything here is written independently of the production classes where
 * practical (Rodrigues' formula, triple products, unit quaternions from raw components), so a bug in
 * the implementation cannot silently cancel against the same bug in the oracle.
 */
final class TestSupport {
    private TestSupport() {}

    static final double TOL = 1e-12;

    // ---------------------------------------------------------------- assertions

    static void assertVec(Vec3 expected, Vec3 actual, double tol, String label) {
        boolean ok = Math.abs(expected.x() - actual.x()) <= tol
                && Math.abs(expected.y() - actual.y()) <= tol
                && Math.abs(expected.z() - actual.z()) <= tol;
        if (!ok) {
            fail(label + ": expected " + expected + " but was " + actual + " (tolerance " + tol + ")");
        }
    }

    static void assertVec(Vec3 expected, Vec3 actual, double tol) {
        assertVec(expected, actual, tol, "vector");
    }

    static void assertFinite(Vec3 v, String label) {
        if (!(Double.isFinite(v.x()) && Double.isFinite(v.y()) && Double.isFinite(v.z()))) {
            fail(label + ": non-finite vector " + v);
        }
    }

    /** Two quaternions represent the same rotation when they act identically on the basis vectors (sign-insensitive). */
    static void assertSameRotation(Quat expected, Quat actual, double tol, String label) {
        assertVec(expected.rotate(Vec3.X), actual.rotate(Vec3.X), tol, label + " (image of +X)");
        assertVec(expected.rotate(Vec3.Y), actual.rotate(Vec3.Y), tol, label + " (image of +Y)");
        assertVec(expected.rotate(Vec3.Z), actual.rotate(Vec3.Z), tol, label + " (image of +Z)");
    }

    /** Frame validity re-checked here, independently of the Frame constructor's own validation. */
    static void assertFrameValid(Frame f, double tol, String label) {
        assertFinite(f.x(), label + ".x");
        assertFinite(f.y(), label + ".y");
        assertFinite(f.z(), label + ".z");
        if (Math.abs(f.x().length() - 1) > tol || Math.abs(f.y().length() - 1) > tol || Math.abs(f.z().length() - 1) > tol) {
            fail(label + ": axes are not unit length: " + f);
        }
        if (Math.abs(f.x().dot(f.y())) > tol || Math.abs(f.y().dot(f.z())) > tol || Math.abs(f.x().dot(f.z())) > tol) {
            fail(label + ": axes are not orthogonal: " + f);
        }
        double det = triple(f.x(), f.y(), f.z());
        if (Math.abs(det - 1.0) > tol) {
            fail(label + ": frame is not right-handed (triple product " + det + "): " + f);
        }
    }

    // ---------------------------------------------------------------- independent math

    /** a . (b x c): +1 for a right-handed orthonormal triple, -1 for a left-handed one. */
    static double triple(Vec3 a, Vec3 b, Vec3 c) {
        double cx = b.y() * c.z() - b.z() * c.y();
        double cy = b.z() * c.x() - b.x() * c.z();
        double cz = b.x() * c.y() - b.y() * c.x();
        return a.x() * cx + a.y() * cy + a.z() * cz;
    }

    /** Rodrigues' rotation formula, active right-handed rotation; axis must be a unit vector. */
    static Vec3 rodrigues(Vec3 axisUnit, double angle, Vec3 v) {
        double c = Math.cos(angle);
        double s = Math.sin(angle);
        double kx = axisUnit.x(), ky = axisUnit.y(), kz = axisUnit.z();
        double kxvx = ky * v.z() - kz * v.y();
        double kxvy = kz * v.x() - kx * v.z();
        double kxvz = kx * v.y() - ky * v.x();
        double kdv = kx * v.x() + ky * v.y() + kz * v.z();
        return new Vec3(
                v.x() * c + kxvx * s + kx * kdv * (1 - c),
                v.y() * c + kxvy * s + ky * kdv * (1 - c),
                v.z() * c + kxvz * s + kz * kdv * (1 - c));
    }

    /** Angle between two non-zero vectors, stable for tiny and near-pi angles. */
    static double angleBetween(Vec3 a, Vec3 b) {
        double cx = a.y() * b.z() - a.z() * b.y();
        double cy = a.z() * b.x() - a.x() * b.z();
        double cz = a.x() * b.y() - a.y() * b.x();
        double cross = Math.sqrt(cx * cx + cy * cy + cz * cz);
        double dot = a.x() * b.x() + a.y() * b.y() + a.z() * b.z();
        return Math.atan2(cross, dot);
    }

    /** Minimal (shortest-arc) rotation taking unit vector `from` to unit vector `to`, applied to v. Not valid for antiparallel input. */
    static Vec3 rotateMinimal(Vec3 from, Vec3 to, Vec3 v) {
        double cx = from.y() * to.z() - from.z() * to.y();
        double cy = from.z() * to.x() - from.x() * to.z();
        double cz = from.x() * to.y() - from.y() * to.x();
        double len = Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (len < 1e-300) {
            return v;
        }
        Vec3 axis = new Vec3(cx / len, cy / len, cz / len);
        return rodrigues(axis, angleBetween(from, to), v);
    }

    /** Frame-to-frame twist about the new Y axis beyond the minimal rotation that carries the old Y to the new Y. */
    static double twist(Frame prev, Frame next) {
        Vec3 carried = rotateMinimal(prev.y(), next.y(), prev.x());
        return angleBetween(carried, next.x());
    }

    // ---------------------------------------------------------------- deterministic generators

    static Vec3 randomUnit(SplittableRandom r) {
        while (true) {
            double x = r.nextDouble(-1, 1);
            double y = r.nextDouble(-1, 1);
            double z = r.nextDouble(-1, 1);
            double l2 = x * x + y * y + z * z;
            if (l2 > 1e-4 && l2 <= 1.0) {
                double l = Math.sqrt(l2);
                return new Vec3(x / l, y / l, z / l);
            }
        }
    }

    static Vec3 randomVec(SplittableRandom r, double range) {
        return new Vec3(r.nextDouble(-range, range), r.nextDouble(-range, range), r.nextDouble(-range, range));
    }

    /** Uniform-ish random unit quaternion built from raw components (no production code involved). */
    static Quat randomRotation(SplittableRandom r) {
        while (true) {
            double w = r.nextDouble(-1, 1);
            double x = r.nextDouble(-1, 1);
            double y = r.nextDouble(-1, 1);
            double z = r.nextDouble(-1, 1);
            double l2 = w * w + x * x + y * y + z * z;
            if (l2 > 1e-4 && l2 <= 1.0) {
                double l = Math.sqrt(l2);
                return new Quat(w / l, x / l, y / l, z / l);
            }
        }
    }

    // ---------------------------------------------------------------- golden CSV loader

    /**
     * Loads src/test/resources/golden/{name}. Lines starting with '#' and blank lines are skipped;
     * the first remaining line is the header and is skipped; every other line is parsed as doubles.
     */
    static List<double[]> golden(String name) {
        String path = "/golden/" + name;
        try (InputStream in = TestSupport.class.getResourceAsStream(path)) {
            if (in == null) {
                fail("missing test resource " + path);
            }
            List<double[]> rows = new ArrayList<>();
            boolean headerSeen = false;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String t = line.trim();
                    if (t.isEmpty() || t.startsWith("#")) {
                        continue;
                    }
                    if (!headerSeen) {
                        headerSeen = true;
                        continue;
                    }
                    String[] cells = t.split(",");
                    double[] row = new double[cells.length];
                    for (int i = 0; i < cells.length; i++) {
                        row[i] = Double.parseDouble(cells[i]);
                    }
                    rows.add(row);
                }
            }
            return rows;
        } catch (IOException e) {
            fail("cannot read " + path + ": " + e);
            return List.of();
        }
    }
}
