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

    // Amendment A3: every bound has an absolute underflow term in units of ETA = Double.MIN_VALUE, and every tolerance is
    // computed with upward-rounded arithmetic. Plain double arithmetic can round a tolerance DOWN, by up to ETA / 2 per
    // product in the subnormal range (2^-49 * Double.MIN_VALUE is 0.0), which would make a test stricter than the
    // contract. For non-negative a and b, nextUp(fl(a op b)) >= a op b, also for subnormal results. The tolerances are
    // therefore never below the contract bounds (checked against BigDecimal in M3UnderflowTest).

    static final double ETA = Double.MIN_VALUE;
    /** An upper bound of pi (Math.PI is below pi). */
    static final double PI_UP = Math.nextUp(Math.PI);

    static double upAdd(double a, double b) {
        return Math.nextUp(a + b);
    }

    static double upMul(double a, double b) {
        return a == 0 || b == 0 ? 0 : Math.nextUp(a * b); // a zero factor gives an exact zero
    }

    /** Upper bound of abs(a - b). */
    static double upAbsDiff(double a, double b) {
        return Math.nextUp(Math.abs(a - b));
    }

    static double upPow(double g, int n) {
        double p = 1;
        for (int i = 0; i < n; i++) {
            p = upMul(p, g);
        }
        return p;
    }

    /** Upper bound of G = |1 - t| + |t|. */
    static double upG(double t) {
        return upAdd(upAbsDiff(1, t), Math.abs(t));
    }

    /** Bezier/line position bound: 2^-49 G^n max_j |P_j| + 2 n G^n ETA. */
    static double bezierPointTol(int degree, double t, double maxAbsControl) {
        double gn = upPow(upG(t), degree);
        return upAdd(upMul(upMul(0x1p-49, gn), maxAbsControl), upMul(upMul(2 * degree, gn), ETA));
    }

    /**
     * Derivative bound: 2^-48 n G^(n-1) max |P_{i+1} - P_i| + 2 n^2 G^n ETA. Each difference rounds relative to its own
     * value, so the relative term is in terms of the differences, not of max |P|.
     */
    static double bezierDerivativeTol(int degree, double t, double maxAbsDiff) {
        double g = upG(t);
        double rel = upMul(upMul(0x1p-48 * degree, upPow(g, Math.max(degree - 1, 0))), maxAbsDiff);
        return upAdd(rel, upMul(upMul(2 * degree * degree, upPow(g, degree)), ETA));
    }

    /** Second-derivative bound, 2^-46 4 n (n-1) G^(n-2) max |P| + 2 n^3 G^n ETA (second differences may cancel). */
    static double bezierSecondTol(int degree, double t, double maxAbsControl) {
        if (degree < 2) {
            return 0;
        }
        double g = upG(t);
        double rel = upMul(upMul(0x1p-46 * 4 * degree * (degree - 1), upPow(g, degree - 2)), maxAbsControl);
        return upAdd(rel, upMul(upMul(2 * degree * degree * degree, upPow(g, degree)), ETA));
    }

    /**
     * Spiral phase uncertainty in turns (contract 7.2, revision 2): 2^-52 (|turns t| + 2). There is no startTurn term:
     * T2 reduces startTurn exactly, so only the rounding of turns * t and of the sum of two reduced parts remain. The
     * underflow of turns * t (at most ETA / 2) is far below the constant term.
     */
    static double phaseErr(double turns, double t) {
        return upMul(0x1p-52, upAdd(upMul(Math.abs(turns), Math.abs(t)), 2));
    }

    /** Upper bound of dphi = 2 pi phaseErr, in radians. */
    static double upDphi(double turns, double t) {
        return upMul(2 * PI_UP, phaseErr(turns, t));
    }

    /**
     * Spiral radius scale S (contract 7.2, revision 2): startRadius when the radii are equal (the radius is then exact),
     * otherwise |1 - t| startRadius + |t| endRadius. On [0, 1], S equals the exact radius r(t). Under extrapolation
     * (1 - t) r0 + t r1 may cancel, and the radius error is relative to S, not to r(t). Rounded upward (amendment A3:
     * in the subnormal range a plain evaluation gives 1 q instead of 1.5 q for r0 = q, r1 = 2q, t = 1/2).
     */
    static double spiralScale(double startRadius, double endRadius, double t) {
        if (startRadius == endRadius) {
            return startRadius;
        }
        return upAdd(upMul(upAbsDiff(1, t), startRadius), upMul(Math.abs(t), endRadius));
    }

    /** Spiral point bound: 2^-49 (S + |rise t|) + S dphi + 4 ETA. */
    static double spiralPointTol(double r0, double r1, double rise, double turns, double t) {
        double s = spiralScale(r0, r1, t);
        double rel = upAdd(upMul(0x1p-49, upAdd(s, upMul(Math.abs(rise), Math.abs(t)))), upMul(s, upDphi(turns, t)));
        return upAdd(rel, 4 * ETA);
    }

    /** Spiral derivative bound: 2^-48 W + W dphi + 4 (1 + w + S) ETA, W = |dr| + w S + |rise|. */
    static double spiralDerivativeTol(double r0, double r1, double rise, double turns, double t) {
        double s = spiralScale(r0, r1, t);
        double w = upMul(2 * PI_UP, Math.abs(turns));
        double big = upAdd(upAdd(upAbsDiff(r1, r0), upMul(w, s)), Math.abs(rise));
        double rel = upAdd(upMul(0x1p-48, big), upMul(big, upDphi(turns, t)));
        return upAdd(rel, upMul(4 * ETA, upAdd(upAdd(1, w), s)));
    }

    /** Spiral second-derivative bound: 2^-47 W2 + W2 dphi + 4 (1 + |dr| + S + w S + w^2) ETA, W2 = 2 |dr| w + w^2 S. */
    static double spiralSecondTol(double r0, double r1, double turns, double t) {
        double s = spiralScale(r0, r1, t);
        double w = upMul(2 * PI_UP, Math.abs(turns));
        double dr = upAbsDiff(r1, r0);
        double w2 = upMul(w, w);
        double big = upAdd(upMul(upMul(2, dr), w), upMul(w2, s));
        double rel = upAdd(upMul(0x1p-47, big), upMul(big, upDphi(turns, t)));
        double abs = upAdd(upAdd(upAdd(upAdd(1, dr), s), upMul(w, s)), w2);
        return upAdd(rel, upMul(4 * ETA, abs));
    }

    static Vec3 randomVec(SplittableRandom r, double mag) {
        return new Vec3(r.nextDouble(-mag, mag), r.nextDouble(-mag, mag), r.nextDouble(-mag, mag));
    }
}
