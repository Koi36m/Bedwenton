package workshop.vfx.math.time;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared helpers for the M2 tests. Independent of the production classes: golden files are read as text, and
 * doubles are written as hexadecimal floating-point literals so they round-trip exactly.
 */
final class M2Support {
    private M2Support() {}

    /** Bitwise equality, so -0.0 and 0.0 differ, and so do any two different doubles. */
    static void assertBits(double expected, double actual, String label) {
        if (Double.doubleToRawLongBits(expected) != Double.doubleToRawLongBits(actual)) {
            fail(label + ": expected " + expected + " (" + Double.toHexString(expected) + ") but was " + actual
                    + " (" + Double.toHexString(actual) + ")");
        }
    }

    static double hex(String s) {
        return Double.parseDouble(s);
    }

    /** Loads src/test/resources/m2/golden/{name}: '#' lines and the header line are skipped. */
    static List<String[]> golden(String name) {
        String path = "/m2/golden/" + name;
        try (InputStream in = M2Support.class.getResourceAsStream(path)) {
            if (in == null) {
                fail("missing test resource " + path);
            }
            List<String[]> rows = new ArrayList<>();
            boolean header = false;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
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
            }
            return rows;
        } catch (IOException e) {
            fail("cannot read " + path + ": " + e);
            return List.of();
        }
    }

    static byte[] resourceBytes(String path) {
        try (InputStream in = M2Support.class.getResourceAsStream(path)) {
            if (in == null) {
                fail("missing test resource " + path);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            fail("cannot read " + path + ": " + e);
            return new byte[0];
        }
    }

    static TimePoint at(TimeDomain domain, long value) {
        return domain == TimeDomain.GAME ? TimePoint.gameTick(value) : TimePoint.cosmeticNanos(value);
    }

    static TimePoint tick(long t) {
        return TimePoint.gameTick(t);
    }
}
