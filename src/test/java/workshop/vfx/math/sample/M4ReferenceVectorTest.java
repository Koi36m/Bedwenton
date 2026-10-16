package workshop.vfx.math.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static workshop.vfx.math.sample.SampleTestSupport.TIGHT;
import static workshop.vfx.math.sample.SampleTestSupport.csv;
import static workshop.vfx.math.sample.SampleTestSupport.hex;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.curve.Curve;
import workshop.vfx.math.curve.LineSegment;

/**
 * Independent reference vectors from tools/generate_m4_reference_vectors.py: exact and closed-form arc lengths, a
 * 1e-28 quadrature oracle for cubics (20-point Gauss-Legendre in 40-digit Decimal, not the contract's 5-point rule),
 * exact inversion targets, and the normative uniform-parameter and spacing-count definitions.
 */
class M4ReferenceVectorTest {
    private static final SampleLimits BIG = new SampleLimits(1 << 20, 1 << 22);

    @Test
    @DisplayName("Reference files are intact: MANIFEST.sha256 matches every file")
    void manifest() throws Exception {
        String manifest = new String(SampleTestSupport.bytes("/m4/reference/MANIFEST.sha256"), StandardCharsets.UTF_8);
        int n = 0;
        for (String line : manifest.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            String[] p = line.trim().split("\\s+");
            assertEquals(p[0], HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(SampleTestSupport.bytes("/m4/reference/" + p[1]))), p[1]);
            n++;
        }
        assertEquals(4, n);
    }

    @Test
    @DisplayName("arclength.csv: 336 lengths (lines, circles, helices, spirals, quadratic and cubic Beziers, cusps, stationary and collapsed curves, extrapolated ranges): converged, |L - L*| <= tau + rounding allowance")
    void arcLengths() {
        List<String[]> rows = csv("arclength.csv");
        assertEquals(336, rows.size());
        for (String[] r : rows) {
            double[] p = SampleTestSupport.params(r);
            double t0 = hex(r[13]), t1 = hex(r[14]), expected = hex(r[15]);
            Curve c = SampleTestSupport.curveOf(r[0], p);
            ArcLengthTable t = ArcLengthTable.build(c, t0, t1, TIGHT, BIG);
            String label = String.join(",", r);
            assertTrue(t.converged(), label + " converged");
            double allowance = (t1 - t0) * SampleTestSupport.speedErrorBound(r[0], p, t0, t1) + Math.ulp(expected);
            if (!(Math.abs(t.length() - expected) <= t.effectiveTolerance() + allowance)) {
                fail(label + ": length " + t.length() + ", error " + Math.abs(t.length() - expected) + " > tau " + t.effectiveTolerance() + " + " + allowance);
            }
        }
    }

    @Test
    @DisplayName("inverse.csv: 216 inversions (quadratic and cubic Beziers, spirals; distances 1/4, 1/2, 3/4 of L): |t - t*| <= (3 tau + allowance) / minimum speed")
    void inversions() {
        List<String[]> rows = csv("inverse.csv");
        assertEquals(216, rows.size());
        for (String[] r : rows) {
            double[] p = SampleTestSupport.params(r);
            double t0 = hex(r[13]), t1 = hex(r[14]), expectedT = hex(r[17]), vmin = hex(r[18]);
            int k = Integer.parseInt(r[15].substring(0, 1));
            Curve c = SampleTestSupport.curveOf(r[0], p);
            ArcLengthTable t = ArcLengthTable.build(c, t0, t1, TIGHT, BIG);
            double actual = t.parameterAt(t.length() * (k / 4.0));
            double allowance = (t1 - t0) * SampleTestSupport.speedErrorBound(r[0], p, t0, t1) + 1e-12 * t.length();
            double bound = (3 * t.effectiveTolerance() + allowance) / vmin + 4 * Math.ulp(Math.max(Math.abs(t0), Math.abs(t1)));
            if (!(Math.abs(actual - expectedT) <= bound)) {
                fail(String.join(",", r) + ": parameter " + actual + ", error " + Math.abs(actual - expectedT) + " > " + bound);
            }
        }
    }

    @Test
    @DisplayName("uniform.csv: 2,310 normative uniform parameters, compared bit for bit (counts up to 2^20, ranges from subnormal to 1e100, -0.0)")
    void uniformParameters() {
        List<String[]> rows = csv("uniform.csv");
        assertEquals(2310, rows.size());
        Map<String, SampleSet> sets = new LinkedHashMap<>();
        Curve line = new LineSegment(Vec3.ZERO, Vec3.X);
        for (String[] r : rows) {
            String key = r[0] + "," + r[1] + "," + r[2] + "," + r[3];
            SampleSet s = sets.computeIfAbsent(key, x -> Sampler.uniformParameter(line, hex(r[0]), hex(r[1]), Integer.parseInt(r[3]), Endpoints.valueOf(r[2]), BIG));
            double expected = hex(r[5]);
            double actual = s.parameter(Integer.parseInt(r[4]));
            assertTrue(Double.compare(expected, actual) == 0 || (expected == 0 && actual == 0 && Integer.parseInt(r[4]) > 0),
                String.join(",", r) + " got " + Double.toHexString(actual));
            if (sets.size() > 64) {
                sets.clear();
            }
        }
    }

    @Test
    @DisplayName("counts.csv: 202 normative spacing counts (round half up of fl(L / d), saturation at Long.MAX_VALUE)")
    void counts() {
        List<String[]> rows = csv("counts.csv");
        assertEquals(202, rows.size());
        for (String[] r : rows) {
            assertEquals(Long.parseLong(r[3]), Sampler.countForSpacing(hex(r[0]), hex(r[1]), Endpoints.valueOf(r[2])), String.join(",", r));
        }
    }
}
