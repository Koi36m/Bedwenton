package workshop.vfx.math.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static workshop.vfx.math.time.M2Support.assertBits;

import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Contract section 2: units and conversions. */
class TickTimeTest {

    @Test
    @DisplayName("Constants: 20 ticks per second, 50,000,000 ns per tick, and their product is exactly one second (section 2)")
    void constants() {
        assertEquals(20L, TickTime.TICKS_PER_SECOND);
        assertEquals(50_000_000L, TickTime.NANOS_PER_TICK);
        assertEquals(1_000_000_000L, TickTime.TICKS_PER_SECOND * TickTime.NANOS_PER_TICK);
    }

    @Test
    @DisplayName("ticksToSeconds is ticks / 20.0, the correctly rounded quotient, not ticks * 0.05 (section 2)")
    void ticksToSecondsIsCorrectlyRoundedDivision() {
        for (long t = -2000; t <= 200_000; t++) {
            assertBits(t / 20.0, TickTime.ticksToSeconds(t), "ticks " + t);
        }
        // the trap the contract rules out: 0.05 is not representable, so ticks * 0.05 rounds twice
        assertBits(0.15, TickTime.ticksToSeconds(3), "3 ticks is the double nearest 0.15");
        assertNotEquals(3 * 0.05, TickTime.ticksToSeconds(3), "3 * 0.05 = 0.15000000000000002");
        assertBits(3600.0, TickTime.ticksToSeconds(72_000), "one hour");
        assertBits(0.025, TickTime.ticksToSeconds(0.5), "half a tick");
    }

    @Test
    @DisplayName("secondsToTicks is seconds * 20.0; the seconds round trip is not exact for large counts, so authoritative ticks never come from seconds (section 2)")
    void secondsToTicksAndRoundTrip() {
        SplittableRandom r = new SplittableRandom(2);
        for (int i = 0; i < 100_000; i++) {
            double s = r.nextDouble(-1e6, 1e6) * Math.pow(10, r.nextInt(-6, 7));
            assertBits(s * 20.0, TickTime.secondsToTicks(s), "seconds " + s);
        }
        assertBits(40.0, TickTime.secondsToTicks(2.0), "2 s");
        assertBits(3.0, TickTime.secondsToTicks(0.15), "0.15 s");
        // established by search (tools/generate_m2_golden_vectors.py comment, docs/m2 section 2): fails near 2^53
        long t = 9_007_199_254_540_993L;
        assertNotEquals((double) t, TickTime.secondsToTicks(TickTime.ticksToSeconds(t)), "round trip at " + t);
    }

    @Test
    @DisplayName("Tier 1: conversions follow IEEE-754 for NaN and infinities and never throw (section 6)")
    void conversionsAreRawIeee() {
        assertTrue(Double.isNaN(TickTime.ticksToSeconds(Double.NaN)));
        assertEquals(Double.POSITIVE_INFINITY, TickTime.secondsToTicks(Double.POSITIVE_INFINITY));
        assertEquals(Double.POSITIVE_INFINITY, TickTime.secondsToTicks(Double.MAX_VALUE), "overflow, not saturation");
        assertBits(-0.0, TickTime.ticksToSeconds(-0.0), "signed zero preserved");
    }
}
