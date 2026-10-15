package workshop.vfx.math.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static workshop.vfx.math.time.M2Support.assertBits;
import static workshop.vfx.math.time.M2Support.at;
import static workshop.vfx.math.time.M2Support.golden;
import static workshop.vfx.math.time.M2Support.hex;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Bitwise comparison against tools/generate_m2_golden_vectors.py. The generator mirrors the operation order of the
 * contract in Python (same IEEE-754 doubles) and cross-checks every case it can in exact rational arithmetic, so
 * these files pin both the specified algorithm and its mathematical claims.
 */
class M2GoldenVectorTest {

    @Test
    @DisplayName("Golden files are intact: MANIFEST.sha256 matches every file")
    void manifest() throws Exception {
        String manifest = new String(M2Support.resourceBytes("/m2/golden/MANIFEST.sha256"), StandardCharsets.UTF_8);
        int checked = 0;
        for (String line : manifest.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.trim().split("\\s+");
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(M2Support.resourceBytes("/m2/golden/" + parts[1]));
            assertEquals(parts[0], HexFormat.of().formatHex(digest), "SHA-256 of " + parts[1]);
            checked++;
        }
        assertEquals(4, checked, "four golden files");
    }

    @Test
    @DisplayName("tick_seconds.csv: ticksToSeconds and secondsToTicks bit for bit (section 2)")
    void tickSeconds() {
        List<String[]> rows = golden("tick_seconds.csv");
        assertTrue(rows.size() > 200);
        for (String[] r : rows) {
            long t = Long.parseLong(r[0]);
            assertBits(hex(r[1]), TickTime.ticksToSeconds(t), "seconds of " + t);
            assertBits(hex(r[3]), TickTime.secondsToTicks(TickTime.ticksToSeconds(t)), "back to ticks for " + t);
        }
    }

    @Test
    @DisplayName("timepoint.csv: ticksSince bit for bit, including the overflow rows (section 3)")
    void timePoints() {
        List<String[]> rows = golden("timepoint.csv");
        assertTrue(rows.size() > 150);
        for (String[] r : rows) {
            TimeDomain dom = TimeDomain.valueOf(r[0]);
            TimePoint v = at(dom, Long.parseLong(r[1]));
            TimePoint o = at(dom, Long.parseLong(r[2]));
            if (r[3].equals("OVERFLOW")) {
                assertThrows(ArithmeticException.class, () -> v.ticksSince(o), String.join(",", r));
            } else {
                assertBits(hex(r[3]), v.ticksSince(o), String.join(",", r));
            }
        }
    }

    @Test
    @DisplayName("timing.csv: stage, cycle and progress bit for bit over 1,692 cases (section 5)")
    void timing() {
        List<String[]> rows = golden("timing.csv");
        assertTrue(rows.size() > 1500);
        for (String[] r : rows) {
            Timing t = new Timing(hex(r[0]), hex(r[1]), RepeatMode.valueOf(r[2]), Long.parseLong(r[3]));
            TimingSample s = t.sample(hex(r[4]));
            String label = String.join(",", r);
            assertEquals(TimingSample.Stage.valueOf(r[5]), s.stage(), label);
            assertEquals(Long.parseLong(r[6]), s.cycle(), label);
            assertBits(hex(r[7]), s.progress(), label);
        }
    }

    @Test
    @DisplayName("playback.csv: 29 control scripts, every anchor and every evaluation bit for bit (section 4)")
    void playbackScripts() {
        List<String[]> rows = golden("playback.csv");
        assertTrue(rows.size() > 1000);
        Playback p = null;
        String script = "";
        int scripts = 0;
        for (String[] r : rows) {
            String label = String.join(",", r);
            if (!r[0].equals(script)) {
                script = r[0];
                scripts++;
            }
            TimePoint t = at(TimeDomain.valueOf(r[3]), Long.parseLong(r[4]));
            double a = hex(r[5]);
            double b = hex(r[6]);
            double expected = hex(r[7]);
            switch (r[2]) {
                case "START" -> {
                    p = Playback.start(t, a, b);
                    assertBits(expected, p.anchorTicks(), label);
                }
                case "EVAL" -> assertBits(expected, p.localTicks(t), label);
                case "PAUSE" -> {
                    p = p.pause(t);
                    assertBits(expected, p.anchorTicks(), label);
                }
                case "RESUME" -> {
                    p = p.resume(t);
                    assertBits(expected, p.anchorTicks(), label);
                }
                case "RATE" -> {
                    p = p.withRate(t, a);
                    assertBits(expected, p.anchorTicks(), label);
                }
                case "SEEK" -> {
                    p = p.seek(t, a);
                    assertBits(expected, p.anchorTicks(), label);
                }
                case "RESTART" -> {
                    p = p.restart(t);
                    assertBits(expected, p.anchorTicks(), label);
                }
                default -> fail("unknown op " + label);
            }
        }
        assertEquals(29, scripts, "script count");
    }
}
