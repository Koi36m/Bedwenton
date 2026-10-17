package workshop.vfx.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Quat;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.time.Playback;
import workshop.vfx.math.time.TimePoint;

/**
 * The four example scenes (specification section 4) against frames computed independently by
 * tools/generate_m5_reference_frames.py (40-digit decimal, rotation matrices, exact rational time).
 * Structure (stages, layers, styles, cycles, point counts, local ticks) must match exactly; progress within 2^-50;
 * coordinates within the validated tolerance of M5TestSupport.tolerance.
 */
class DemoReferenceTest {
    private static String resource(String name) throws IOException {
        try (InputStream in = DemoReferenceTest.class.getResourceAsStream("/m5/reference/" + name)) {
            if (in == null) {
                throw new IOException("missing resource " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<String[]> rows(String name) throws IOException {
        List<String[]> out = new ArrayList<>();
        boolean header = false;
        for (String line : resource(name).split("\n")) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            if (!header) {
                header = true;
                continue;
            }
            out.add(line.split(",", -1));
        }
        return out;
    }

    private static double d(String s) {
        return Double.parseDouble(s);
    }

    /** Compares every frame of one fixture file; returns the number of points compared. */
    private static int check(String file, EffectScene scene, double maxTurns) throws IOException {
        Map<String, List<String[]>> frames = new LinkedHashMap<>();
        for (String[] r : rows(file)) {
            frames.computeIfAbsent(r[0] + "@" + r[1] + "@" + r[2], k -> new ArrayList<>()).add(r);
        }
        int points = 0;
        for (Map.Entry<String, List<String[]>> e : frames.entrySet()) {
            List<String[]> rs = e.getValue();
            String[] r0 = rs.get(0);
            String label = file + " " + e.getKey();
            String[] pb = r0[2].split(";");
            Playback playback = new Playback(new TimePoint(scene.domain(), Long.parseLong(pb[0])), d(pb[1]), d(pb[2]), pb[3].equals("paused"));
            Pose anchor = new Pose(new Vec3(d(r0[3]), d(r0[4]), d(r0[5])), new Quat(d(r0[6]), d(r0[7]), d(r0[8]), d(r0[9])), d(r0[10]));
            EffectFrame f = scene.evaluate(anchor, playback, new TimePoint(scene.domain(), Long.parseLong(r0[1])));
            assertEquals(r0[11], f.stage().name(), label + " frame stage");
            assertEquals(d(r0[12]), f.localTicks(), label + " local ticks");
            List<String[]> expectedPoints = r0[13].equals("-1") ? List.of() : rs;
            assertEquals(expectedPoints.size(), f.pointCount(), label + " point count");
            int row = 0;
            for (FrameBatch b : f.batches()) {
                for (int i = 0; i < b.size(); i++, row++) {
                    String[] r = expectedPoints.get(row);
                    String at = label + " layer " + r[14] + " point " + r[19];
                    assertEquals(Integer.parseInt(r[13]), b.layerIndex(), at);
                    assertEquals(r[14], b.layer(), at);
                    assertEquals(r[15], b.style().value(), at);
                    assertEquals(r[16], b.timing().stage().name(), at);
                    assertEquals(Long.parseLong(r[17]), b.timing().cycle(), at);
                    assertEquals(d(r[18]), b.timing().progress(), 0x1p-50, at + " progress");
                    assertEquals(Integer.parseInt(r[19]), i, at + " point index");
                    double x = d(r[20]), y = d(r[21]), z = d(r[22]);
                    double tol = M5TestSupport.tolerance(M5TestSupport.magnitude(x, y, z, anchor.translation().x(), anchor.translation().y(), anchor.translation().z()), maxTurns);
                    assertEquals(x, b.x(i), tol, at + " x");
                    assertEquals(y, b.y(i), tol, at + " y");
                    assertEquals(z, b.z(i), tol, at + " z");
                    points++;
                }
            }
            assertEquals(expectedPoints.size(), row, label + " all reference points matched");
        }
        return points;
    }

    @Test
    @DisplayName("MANIFEST.sha256 matches the four reference files")
    void manifest() throws Exception {
        for (String line : resource("MANIFEST.sha256").split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("  ");
            byte[] bytes = resource(parts[1].trim()).getBytes(StandardCharsets.UTF_8);
            assertEquals(parts[0], HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), parts[1]);
        }
    }

    @Test
    @DisplayName("A. Healing ritual: three separated expanding, rising ring pulses; forward, reversed, rotated-and-scaled anchor, paused and half-rate playback; the scene ends at local tick 60")
    void healing() throws IOException {
        assertTrue(check("healing.csv", M5TestSupport.healing(), 0) > 400);
    }

    @Test
    @DisplayName("B. Orbiting sparkles: four phase-offset sparks and a three-ghost trail on a moving, turning player anchor; COSMETIC nanoseconds across the long wrap; before the start")
    void orbit() throws IOException {
        assertTrue(check("orbit.csv", M5TestSupport.orbit(), 1) > 40);
    }

    @Test
    @DisplayName("C. Moving trident: an arc-length-sampled helix drawing itself (PREFIX) and spinning, on seven trident pose snapshots; the scene ends at local tick 30")
    void trident() throws IOException {
        assertTrue(check("trident.csv", M5TestSupport.trident(), 2) > 80);
    }

    @Test
    @DisplayName("D. Astral Prism constellation: a spinning, tilted five-star ring and five staged pentagram links (PREFIX reveal, HOLD_AFTER); seek; the scene ends at local tick 120")
    void constellation() throws IOException {
        assertTrue(check("constellation.csv", M5TestSupport.constellation(), 1) > 400);
    }
}
