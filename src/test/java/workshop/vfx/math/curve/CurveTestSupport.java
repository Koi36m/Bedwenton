package workshop.vfx.math.curve;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import workshop.vfx.math.core.Vec3;

/**
 * Helpers for the M3 tests. Error bounds are the ones in docs/m3/M3-IMPLEMENTATION-CONTRACT.md section 7. Nothing
 * here evaluates a curve the way the contract recommends; oracles are the generator's exact or high-precision values,
 * closed-form identities, or independent formulations (Bernstein form, quaternion rotation, finite differences).
 */
final class CurveTestSupport {
    private CurveTestSupport() {}

    static final double EPS = 0x1p-53;

    static double hex(String s) {
        return Double.parseDouble(s);
    }

    static Vec3 vec(String[] r, int i) {
        return new Vec3(hex(r[i]), hex(r[i + 1]), hex(r[i + 2]));
    }

    /** Numerical equality per component (== treats -0.0 and 0.0 as equal). */
    static void assertExact(Vec3 expected, Vec3 actual, String label) {
        if (!(expected.x() == actual.x() && expected.y() == actual.y() && expected.z() == actual.z())) {
            fail(label + ": expected exactly " + expected + " but was " + actual);
        }
    }

    static void assertNear(Vec3 expected, Vec3 actual, double tol, String label) {
        double e = maxAbs(expected.sub(actual));
        if (!(e <= tol)) {
            fail(label + ": expected " + expected + " but was " + actual + " (error " + e + " > tolerance " + tol + ")");
        }
    }

    static double maxAbs(Vec3 v) {
        return Math.max(Math.abs(v.x()), Math.max(Math.abs(v.y()), Math.abs(v.z())));
    }

    static List<String[]> csv(String name) {
        String path = "/m3/reference/" + name;
        try (InputStream in = CurveTestSupport.class.getResourceAsStream(path)) {
            if (in == null) {
                fail("missing resource " + path);
            }
            List<String[]> rows = new ArrayList<>();
            boolean header = false;
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) {
                    continue;
                }
                if (!header) {
                    header = true;
                    continue;
                }
                rows.add(t.split(","));
            }
            return rows;
        } catch (IOException e) {
            fail("cannot read " + path + ": " + e);
            return List.of();
        }
    }

    static byte[] bytes(String path) {
        try (InputStream in = CurveTestSupport.class.getResourceAsStream(path)) {
            if (in == null) {
                fail("missing resource " + path);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            fail("cannot read " + path + ": " + e);
            return new byte[0];
        }
    }

    // ------------------------------------------------------------ contract error bounds (section 7)

    /** Bezier/line position bound: 2^-49 * sum_i |B_i(t)| * max_j |P_j|, where sum_i |B_i(t)| = (|1-t| + |t|)^n. */
    static double bezierPointTol(int degree, double t, double maxAbsControl) {
        return 0x1p-49 * Math.pow(Math.abs(1 - t) + Math.abs(t), degree) * maxAbsControl;
    }

    /**
     * Derivative bound: 2^-48 * n * (|1-t| + |t|)^(n-1) * max |P_{i+1} - P_i|. Each difference rounds relative to its
     * own value, so the bound is in terms of the differences, not of max |P|.
     */
    static double bezierDerivativeTol(int degree, double t, double maxAbsDiff) {
        double g = Math.pow(Math.abs(1 - t) + Math.abs(t), Math.max(degree - 1, 0));
        return 0x1p-48 * degree * g * maxAbsDiff;
    }

    /** Second-derivative bound, in terms of max |P| because second differences may cancel. */
    static double bezierSecondTol(int degree, double t, double maxAbsControl) {
        if (degree < 2) {
            return 0;
        }
        double g = Math.pow(Math.abs(1 - t) + Math.abs(t), degree - 2);
        return 0x1p-46 * degree * (degree - 1) * 4 * g * maxAbsControl;
    }

    /**
     * Spiral phase uncertainty in turns (contract 7.2, revision 2): 2^-52 (|turns t| + 2). There is no startTurn term:
     * T2 reduces startTurn exactly, so only the rounding of turns * t and of the sum of two reduced parts remain.
     */
    static double phaseErr(double turns, double t) {
        return 0x1p-52 * (Math.abs(turns * t) + 2);
    }

    /**
     * Spiral radius scale S (contract 7.2, revision 2): startRadius when the radii are equal (the radius is then exact),
     * otherwise |1 - t| startRadius + |t| endRadius. On [0, 1], S equals the exact radius r(t). Under extrapolation
     * (1 - t) r0 + t r1 may cancel, and the radius error is relative to S, not to r(t). All terms are non-negative, so
     * this computation has no cancellation; the factor (1 + 2^-50) covers its own rounding.
     */
    static double spiralScale(double startRadius, double endRadius, double t) {
        if (startRadius == endRadius) {
            return startRadius;
        }
        return (Math.abs(1 - t) * startRadius + Math.abs(t) * endRadius) * (1 + 0x1p-50);
    }

    /** Spiral point bound: 2^-49 (S + |rise t|) + S dphi. */
    static double spiralPointTol(double r0, double r1, double rise, double turns, double t) {
        double s = spiralScale(r0, r1, t);
        return 0x1p-49 * (s + Math.abs(rise * t)) + s * 2 * Math.PI * phaseErr(turns, t);
    }

    /** Spiral derivative bound: 2^-48 W + W dphi, W = |dr| + w S + |rise|. */
    static double spiralDerivativeTol(double r0, double r1, double rise, double turns, double t) {
        double w = 2 * Math.PI * Math.abs(turns);
        double big = Math.abs(r1 - r0) * (1 + 0x1p-52) + w * spiralScale(r0, r1, t) + Math.abs(rise);
        return 0x1p-48 * big + big * 2 * Math.PI * phaseErr(turns, t);
    }

    /** Spiral second-derivative bound: 2^-47 W2 + W2 dphi, W2 = 2 |dr| w + w^2 S. */
    static double spiralSecondTol(double r0, double r1, double turns, double t) {
        double w = 2 * Math.PI * Math.abs(turns);
        double big = 2 * Math.abs(r1 - r0) * (1 + 0x1p-52) * w + w * w * spiralScale(r0, r1, t);
        return 0x1p-47 * big + big * 2 * Math.PI * phaseErr(turns, t);
    }

    static Vec3 randomVec(SplittableRandom r, double mag) {
        return new Vec3(r.nextDouble(-mag, mag), r.nextDouble(-mag, mag), r.nextDouble(-mag, mag));
    }
}
