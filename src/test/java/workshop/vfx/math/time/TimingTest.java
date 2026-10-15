package workshop.vfx.math.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static workshop.vfx.math.time.M2Support.assertBits;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.math.time.TimingSample.Stage;

/** Contract section 5: shaping local ticks into stage, cycle and progress. */
class TimingTest {

    private static void assertSample(Stage stage, long cycle, double progress, TimingSample s, String label) {
        assertEquals(stage, s.stage(), label + " stage");
        assertEquals(cycle, s.cycle(), label + " cycle");
        assertBits(progress, s.progress(), label + " progress");
    }

    @Test
    @DisplayName("Validation: finite delay, finite positive duration, cycles >= 1, ONCE has exactly one cycle (sections 5, 7)")
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> Timing.once(0.0));
        assertThrows(IllegalArgumentException.class, () -> Timing.once(-1.0));
        assertThrows(IllegalArgumentException.class, () -> Timing.once(-0.0));
        assertThrows(IllegalArgumentException.class, () -> Timing.loop(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Timing.pingPong(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> Timing.once(1).withDelay(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Timing.once(1).withDelay(Double.NEGATIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> Timing.loop(1).withCycles(0));
        assertThrows(IllegalArgumentException.class, () -> Timing.loop(1).withCycles(-5));
        assertThrows(IllegalArgumentException.class, () -> Timing.once(1).withCycles(2), "ONCE is one cycle by definition");
        assertThrows(IllegalArgumentException.class, () -> new Timing(0, 1, null, 1));
        Timing t = Timing.pingPong(40).withDelay(-3.5).withCycles(6);
        assertBits(-3.5, t.delayTicks(), "delay");
        assertBits(40.0, t.durationTicks(), "duration");
        assertEquals(RepeatMode.PING_PONG, t.mode());
        assertEquals(6L, t.cycles());
        assertEquals(Long.MAX_VALUE, Timing.FOREVER);
        assertEquals(Timing.FOREVER, Timing.loop(1).cycles());
        assertEquals(1L, Timing.once(1).cycles());
        assertBits(Double.MIN_VALUE, Timing.once(Double.MIN_VALUE).durationTicks(), "any positive finite duration is valid");
    }

    @Test
    @DisplayName("TimingSample validates stage, cycle >= 0 and progress in [0, 1] (sections 5, 7)")
    void sampleValidation() {
        assertThrows(IllegalArgumentException.class, () -> new TimingSample(null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new TimingSample(Stage.ACTIVE, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new TimingSample(Stage.ACTIVE, 0, -0.5));
        assertThrows(IllegalArgumentException.class, () -> new TimingSample(Stage.ACTIVE, 0, Math.nextUp(1.0)));
        assertThrows(IllegalArgumentException.class, () -> new TimingSample(Stage.ACTIVE, 0, Double.NaN));
        assertEquals(1.0, new TimingSample(Stage.AFTER, 0, 1.0).progress());
    }

    @Test
    @DisplayName("ONCE: BEFORE below the delay, ACTIVE on [0, d) with progress u/d < 1, AFTER from d on with progress 1 (section 5)")
    void once() {
        Timing t = Timing.once(40);
        assertSample(Stage.BEFORE, 0, 0.0, t.sample(-1), "-1");
        assertSample(Stage.BEFORE, 0, 0.0, t.sample(-Double.MIN_VALUE), "just before");
        assertSample(Stage.ACTIVE, 0, 0.0, t.sample(0), "start");
        assertSample(Stage.ACTIVE, 0, 0.5, t.sample(20), "middle");
        assertTrue(t.sample(Math.nextDown(40.0)).progress() < 1.0, "the last double before the end is below 1");
        assertSample(Stage.AFTER, 0, 1.0, t.sample(40), "end is AFTER with the end value");
        assertSample(Stage.AFTER, 0, 1.0, t.sample(1e15), "long after");
    }

    @Test
    @DisplayName("LOOP: half-open cycles [n d, (n+1) d); finite cycles end in AFTER with cycle = cycles - 1 and progress 1 (section 5)")
    void loop() {
        Timing forever = Timing.loop(40);
        assertSample(Stage.ACTIVE, 0, 39.0 / 40.0, forever.sample(39), "39");
        assertSample(Stage.ACTIVE, 1, 0.0, forever.sample(40), "wraps to the next cycle at exactly d");
        assertSample(Stage.ACTIVE, 1, 0.025, forever.sample(41), "41");
        assertSample(Stage.ACTIVE, 25_000, 0.0, forever.sample(1e6), "1e6");
        Timing three = Timing.loop(40).withCycles(3);
        assertSample(Stage.ACTIVE, 2, 39.0 / 40.0, three.sample(119), "inside the last cycle");
        assertSample(Stage.AFTER, 2, 1.0, three.sample(120), "end of the last cycle");
        assertSample(Stage.AFTER, 2, 1.0, three.sample(1e12), "long after");
    }

    @Test
    @DisplayName("PING_PONG: odd cycles run backwards as (d - r) / d, values are continuous at every turn, the end value depends on parity (section 5)")
    void pingPong() {
        Timing t = Timing.pingPong(40);
        assertSample(Stage.ACTIVE, 0, 0.0, t.sample(0), "0");
        assertSample(Stage.ACTIVE, 0, 0.5, t.sample(20), "20");
        assertSample(Stage.ACTIVE, 1, 1.0, t.sample(40), "turn at the top: backward pass starts at 1");
        assertSample(Stage.ACTIVE, 1, 0.5, t.sample(60), "60");
        assertSample(Stage.ACTIVE, 2, 0.0, t.sample(80), "turn at the bottom");
        for (double turn : new double[] {40, 80, 120, 4000}) {
            double left = t.sample(Math.nextDown(turn)).progress();
            double right = t.sample(turn).progress();
            // the step from nextDown(turn) to turn is ulp(turn) ticks, i.e. ulp(turn) / d in progress
            assertEquals(right, left, 2 * Math.ulp(turn) / 40 + 1e-16, "continuity at " + turn);
        }
        assertSample(Stage.AFTER, 1, 0.0, t.withCycles(2).sample(80), "even count ends at 0");
        assertSample(Stage.AFTER, 2, 1.0, t.withCycles(3).sample(120), "odd count ends at 1");
        assertSample(Stage.ACTIVE, 2, 1.0 / 40.0, t.withCycles(3).sample(81), "last pass is forward");
    }

    @Test
    @DisplayName("Delay: u = local - delay; negative delays start part-way; -0.0 elapsed time gives +0.0 progress (section 5)")
    void delay() {
        Timing t = Timing.once(40).withDelay(10);
        assertSample(Stage.BEFORE, 0, 0.0, t.sample(9.999), "before the delay");
        assertSample(Stage.ACTIVE, 0, 0.0, t.sample(10), "starts at the delay");
        assertSample(Stage.ACTIVE, 0, 0.25, t.sample(20), "20");
        assertSample(Stage.AFTER, 0, 1.0, t.sample(50), "ends at delay + duration");
        Timing early = Timing.once(40).withDelay(-10);
        assertSample(Stage.ACTIVE, 0, 0.25, early.sample(0), "a negative delay starts a quarter in");
        assertSample(Stage.AFTER, 0, 1.0, early.sample(30), "and ends early");
        assertSample(Stage.ACTIVE, 0, 0.0, Timing.loop(40).sample(-0.0), "-0.0 - 0.0 is -0.0: progress must still be +0.0");
        assertSample(Stage.ACTIVE, 0, 0.0, Timing.loop(40).withDelay(-0.0).sample(-0.0), "-0.0 - -0.0");
    }

    @Test
    @DisplayName("sample rejects non-finite local time (IAE) and an unrepresentable elapsed time (ArithmeticException) (sections 5, 7)")
    void invalidSampleInput() {
        Timing t = Timing.loop(40);
        assertThrows(IllegalArgumentException.class, () -> t.sample(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> t.sample(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> t.sample(Double.NEGATIVE_INFINITY));
        Timing farBack = Timing.loop(40).withDelay(-Double.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> farBack.sample(Double.MAX_VALUE), "MAX - (-MAX) overflows");
    }

    @Test
    @DisplayName("Integer exactness: integer delay, duration and local time give the exact stage and cycle and a once-rounded progress (section 6.3)")
    void integerExactness() {
        SplittableRandom r = new SplittableRandom(6);
        for (int i = 0; i < 300_000; i++) {
            long delay = r.nextLong(-1_000_000, 1_000_000);
            long d = r.nextLong(1, 100_000);
            long local = r.nextBoolean() ? r.nextLong(-2_000_000, 50_000_000) : r.nextLong(-(1L << 50), 1L << 50);
            RepeatMode mode = RepeatMode.values()[r.nextInt(3)];
            long cycles = mode == RepeatMode.ONCE ? 1 : (r.nextBoolean() ? Timing.FOREVER : r.nextLong(1, 1000));
            TimingSample s = new Timing(delay, d, mode, cycles).sample(local);
            // independent integer reference
            long u = local - delay;
            String label = "delay " + delay + " d " + d + " " + mode + " x" + cycles + " local " + local;
            if (u < 0) {
                assertSample(Stage.BEFORE, 0, 0.0, s, label);
                continue;
            }
            long n = Math.floorDiv(u, d);
            long rem = Math.floorMod(u, d);
            if (n >= cycles) {
                double end = mode == RepeatMode.PING_PONG && cycles % 2 == 0 ? 0.0 : 1.0;
                assertSample(Stage.AFTER, cycles - 1, end, s, label);
            } else {
                long num = mode == RepeatMode.PING_PONG && (n & 1) == 1 ? d - rem : rem;
                assertSample(Stage.ACTIVE, n, (double) num / (double) d, s, label); // both < 2^53: correctly rounded
            }
        }
    }

    @Test
    @DisplayName("Phase precision does not depend on elapsed time: exact remainder, unlike frac(u / d) (sections 5, 6.4)")
    void phasePrecisionAtLargeTimes() {
        Timing t = Timing.loop(7);
        double u = 0x1p45 + 0.5;
        TimingSample s = t.sample(u);
        assertBits(new BigDecimal(u).remainder(BigDecimal.valueOf(7)).divide(BigDecimal.valueOf(7), new MathContext(40)).doubleValue(),
                s.progress(), "correctly rounded exact phase");
        double naive = u / 7.0 - Math.floor(u / 7.0);
        assertTrue(Math.abs(naive - s.progress()) > 1e-4, "the naive method is off by about 4e-4 here: " + naive);
        // cycle index stays exact up to elapsed / duration = 2^50
        long n = 1L << 50;
        TimingSample big = Timing.loop(3).sample(3.0 * n + 1.0);
        assertEquals(n, big.cycle(), "cycle at 2^50");
        assertBits(1.0 / 3.0, big.progress(), "progress at 2^50 cycles");
    }

    @Test
    @DisplayName("Fuzz: for every finite input the sample is valid, ACTIVE forward progress is below 1, nothing throws (sections 5, 6)")
    void totality() {
        SplittableRandom r = new SplittableRandom(7);
        double[] mags = {0, Double.MIN_VALUE, 1e-300, 1e-12, 1, 3e7, 1e15, 0x1p53, 1e100, 1e300};
        for (int i = 0; i < 300_000; i++) {
            double delay = mags[r.nextInt(mags.length)] * r.nextDouble(-1, 1);
            double d = mags[1 + r.nextInt(mags.length - 1)] * r.nextDouble(0.5, 1);
            double local = mags[r.nextInt(mags.length)] * r.nextDouble(-1, 1);
            RepeatMode mode = RepeatMode.values()[r.nextInt(3)];
            long cycles = mode == RepeatMode.ONCE ? 1 : (r.nextBoolean() ? Timing.FOREVER : r.nextLong(1, 10));
            if (!(d > 0) || !Double.isFinite(local - delay)) {
                continue;
            }
            try {
                TimingSample s = new Timing(delay, d, mode, cycles).sample(local);
                assertTrue(s.progress() >= 0 && s.progress() <= 1 && s.cycle() >= 0, "range");
                if (s.stage() == Stage.ACTIVE && !(mode == RepeatMode.PING_PONG && (s.cycle() & 1) == 1)) {
                    assertTrue(s.progress() < 1.0, "forward ACTIVE progress < 1");
                }
            } catch (RuntimeException e) {
                fail("threw " + e + " for delay " + delay + " d " + d + " " + mode + " local " + local);
            }
        }
    }

    @Test
    @DisplayName("Within one pass progress is monotone in local time: non-decreasing forward, non-increasing backward (section 5)")
    void monotoneWithinPass() {
        SplittableRandom r = new SplittableRandom(8);
        for (int k = 0; k < 200; k++) {
            double d = r.nextDouble(0.01, 500);
            Timing t = Timing.pingPong(d).withDelay(r.nextDouble(-100, 100));
            double x = t.delayTicks() + r.nextDouble(0, 50 * d);
            TimingSample prev = t.sample(x);
            for (int i = 0; i < 2000; i++) {
                x = Math.nextUp(x) + r.nextDouble(0, d / 300);
                TimingSample s = t.sample(x);
                if (s.cycle() == prev.cycle()) {
                    if ((s.cycle() & 1) == 0) {
                        assertTrue(s.progress() >= prev.progress(), "forward pass decreased at " + x);
                    } else {
                        assertTrue(s.progress() <= prev.progress(), "backward pass increased at " + x);
                    }
                }
                prev = s;
            }
        }
    }
}
