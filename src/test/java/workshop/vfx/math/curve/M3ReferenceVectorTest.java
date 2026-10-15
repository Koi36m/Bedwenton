package workshop.vfx.math.curve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static workshop.vfx.math.curve.CurveTestSupport.assertExact;
import static workshop.vfx.math.curve.CurveTestSupport.assertNear;
import static workshop.vfx.math.curve.CurveTestSupport.csv;
import static workshop.vfx.math.curve.CurveTestSupport.hex;
import static workshop.vfx.math.curve.CurveTestSupport.maxAbs;
import static workshop.vfx.math.curve.CurveTestSupport.phaseErr;
import static workshop.vfx.math.curve.CurveTestSupport.vec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.math.core.Vec3;

/**
 * Independent reference vectors from tools/generate_m3_reference_vectors.py (exact rationals, the Bernstein form and
 * 60-digit Taylor series; none of the contract's recommended algorithms). EXACT rows compare with ==; ROUNDED rows
 * use the error bounds of contract section 7.
 */
class M3ReferenceVectorTest {

    @Test
    @DisplayName("Reference files are intact: MANIFEST.sha256 matches every file")
    void manifest() throws Exception {
        String manifest = new String(CurveTestSupport.bytes("/m3/reference/MANIFEST.sha256"), StandardCharsets.UTF_8);
        int n = 0;
        for (String line : manifest.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            String[] p = line.trim().split("\\s+");
            assertEquals(p[0], HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(CurveTestSupport.bytes("/m3/reference/" + p[1]))), p[1]);
            n++;
        }
        assertEquals(3, n);
    }

    @Test
    @DisplayName("easing.csv: 1,155 cases, 450 exact, the rest within 2^-50 of the exact rational value (section 6)")
    void easing() {
        List<String[]> rows = csv("easing.csv");
        assertTrue(rows.size() > 1000);
        for (String[] r : rows) {
            Easing e = Easing.valueOf(r[0]);
            double t = hex(r[1]);
            double expected = hex(r[3]);
            double v = e.apply(t);
            String label = String.join(",", r);
            if (r[2].equals("EXACT")) {
                assertTrue(v == expected, label + " got " + v);
            } else if (!(Math.abs(v - expected) <= 0x1p-50 + Math.ulp(expected) / 2)) {
                fail(label + " got " + v);
            }
        }
    }

    @Test
    @DisplayName("bezier.csv: 645 line, quadratic and cubic cases (point, derivative, second derivative), 189 exact, extrapolated t included (section 4)")
    void bezier() {
        List<String[]> rows = csv("bezier.csv");
        assertTrue(rows.size() > 600);
        for (String[] r : rows) {
            double t = hex(r[1]);
            Vec3 p0 = vec(r, 3), p1 = vec(r, 6), p2 = vec(r, 9), p3 = vec(r, 12);
            int degree;
            Curve c = switch (r[0]) {
                case "LINE" -> { degree = 1; yield new LineSegment(p0, p1); }
                case "QUAD" -> { degree = 2; yield new QuadraticBezier(p0, p1, p2); }
                default -> { degree = 3; yield new CubicBezier(p0, p1, p2, p3); }
            };
            Vec3[] ctrl = degree == 1 ? new Vec3[] {p0, p1} : degree == 2 ? new Vec3[] {p0, p1, p2} : new Vec3[] {p0, p1, p2, p3};
            double maxP = 0, maxD = 0;
            for (int i = 0; i < ctrl.length; i++) {
                maxP = Math.max(maxP, maxAbs(ctrl[i]));
                if (i > 0) {
                    maxD = Math.max(maxD, maxAbs(ctrl[i].sub(ctrl[i - 1])) * (1 + 0x1p-52));
                }
            }
            Vec3 ep = vec(r, 15), ed = vec(r, 18), es = vec(r, 21);
            String label = String.join(",", r);
            if (r[2].equals("EXACT")) {
                assertExact(ep, c.point(t), label + " point");
                assertExact(ed, c.derivative(t), label + " derivative");
                assertExact(es, c.secondDerivative(t), label + " second");
            } else {
                double slack = 1 + 0x1p-50; // the expected value itself is rounded once
                assertNear(ep, c.point(t), CurveTestSupport.bezierPointTol(degree, t, maxP) * slack + Math.ulp(maxAbs(ep)), label + " point");
                assertNear(ed, c.derivative(t), CurveTestSupport.bezierDerivativeTol(degree, t, maxD) * slack + Math.ulp(maxAbs(ed)), label + " derivative");
                assertNear(es, c.secondDerivative(t), CurveTestSupport.bezierSecondTol(degree, t, maxP) + Math.ulp(maxAbs(es)), label + " second");
            }
        }
    }

    @Test
    @DisplayName("spiral.csv: 549 spiral cases against 60-digit Taylor references; exact quarter-turn points; error bounds include phase rounding (sections 5, 7)")
    void spiral() {
        List<String[]> rows = csv("spiral.csv");
        assertTrue(rows.size() > 500);
        for (String[] r : rows) {
            double r0 = hex(r[0]), r1 = hex(r[1]), rise = hex(r[2]), turns = hex(r[3]), start = hex(r[4]), t = hex(r[5]);
            Spiral s = new Spiral(r0, r1, rise, turns, start);
            Vec3 ep = vec(r, 7), ed = vec(r, 10), es = vec(r, 13);
            String label = String.join(",", r);
            double rad = Math.abs(r0 == r1 ? r0 : (1 - t) * r0 + t * r1);
            double dr = Math.abs(r1 - r0);
            double w = 2 * Math.PI * Math.abs(turns);
            double dphi = 2 * Math.PI * phaseErr(turns, start, t); // radians
            if (r[6].equals("EXACT")) {
                assertExact(ep, s.point(t), label + " point");
            } else {
                double tolP = 0x1p-49 * (rad + Math.abs(rise * t)) + rad * dphi + Math.ulp(maxAbs(ep));
                assertNear(ep, s.point(t), tolP, label + " point");
            }
            double wd = dr + w * rad + Math.abs(rise);
            assertNear(ed, s.derivative(t), 0x1p-48 * wd + wd * dphi + Math.ulp(maxAbs(ed)), label + " derivative");
            double ws = 2 * dr * w + w * w * rad;
            assertNear(es, s.secondDerivative(t), 0x1p-47 * ws + ws * dphi + Math.ulp(maxAbs(es)), label + " second");
        }
    }
}
