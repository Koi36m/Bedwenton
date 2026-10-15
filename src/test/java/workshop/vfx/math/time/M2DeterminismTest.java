package workshop.vfx.math.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static workshop.vfx.math.time.M2Support.assertBits;
import static workshop.vfx.math.time.M2Support.tick;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.math.time.TimingSample.Stage;

/**
 * Contract section 6: determinism, path independence, irregular and missing ticks, low TPS, discontinuities,
 * long-running behaviour and drift.
 */
class M2DeterminismTest {

    private static long bits(double x) {
        return Double.doubleToRawLongBits(x);
    }

    @Test
    @DisplayName("Determinism: equal inputs give bit-identical outputs, across repeated calls and freshly built equal objects (section 6.1)")
    void bitIdenticalRepetition() {
        SplittableRandom r = new SplittableRandom(9);
        for (int i = 0; i < 20_000; i++) {
            long a = r.nextLong(-1_000_000_000L, 1_000_000_000L);
            double off = r.nextDouble(-1e3, 1e3);
            double rate = r.nextDouble(-4, 4);
            Playback p1 = Playback.start(TimePoint.cosmeticNanos(a), off, rate).withRate(TimePoint.cosmeticNanos(a + 77), rate * 0.5);
            Playback p2 = Playback.start(TimePoint.cosmeticNanos(a), off, rate).withRate(TimePoint.cosmeticNanos(a + 77), rate * 0.5);
            assertEquals(p1, p2, "equal construction histories give equal values");
            TimePoint t = TimePoint.cosmeticNanos(a + r.nextLong(0, 10_000_000_000L));
            double l1 = p1.localTicks(t);
            assertEquals(bits(l1), bits(p2.localTicks(t)));
            assertEquals(bits(l1), bits(p1.localTicks(t)), "repeated call");
            Timing tm = Timing.pingPong(r.nextDouble(0.5, 100)).withDelay(r.nextDouble(-50, 50)).withCycles(r.nextLong(1, 50));
            assertEquals(tm.sample(l1), tm.sample(l1), "samples are values");
            assertEquals(bits(tm.sample(l1).progress()), bits(new Timing(tm.delayTicks(), tm.durationTicks(), tm.mode(), tm.cycles()).sample(l1).progress()));
        }
    }

    @Test
    @DisplayName("Path independence: skipped, irregular or out-of-order evaluation gives exactly the values of regular evaluation (section 6.2)")
    void pathIndependence() {
        Playback p = Playback.start(tick(1_000), 0.0, 1.0).pause(tick(1_300)).resume(tick(1_450)).withRate(tick(1_600), 0.75);
        Timing t = Timing.pingPong(37).withDelay(5).withCycles(40);
        int n = 3_000;
        double[] regular = new double[n];
        long[] regularBits = new long[n];
        for (int i = 0; i < n; i++) {
            TimingSample s = t.sample(p.localTicks(tick(1_000 + i)));
            regular[i] = s.progress();
            regularBits[i] = bits(s.progress()) ^ s.cycle() ^ s.stage().ordinal();
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i += 1 + (i % 7)) {
            order.add(i); // skipped and irregular frames
        }
        Collections.shuffle(order, new java.util.Random(10)); // out of order
        for (int i : order) {
            TimingSample s = t.sample(p.localTicks(tick(1_000 + i)));
            assertEquals(regularBits[i], bits(s.progress()) ^ s.cycle() ^ s.stage().ordinal(), "tick " + (1_000 + i));
        }
        assertTrue(regular[n - 1] >= 0.0);
    }

    @Test
    @DisplayName("Low TPS: GAME time follows ticks (half speed at 10 TPS), COSMETIC time follows supplied nanoseconds (real-time speed) (sections 3, 6.2)")
    void lowTps() {
        // two real seconds of server time at 10 TPS: 20 ticks happen, 2e9 ns pass
        Playback game = Playback.start(tick(0));
        Playback cosmetic = Playback.start(TimePoint.cosmeticNanos(0));
        long tickAt10Tps = 0;
        long nanos = 0;
        for (int i = 0; i < 20; i++) {
            tickAt10Tps += 1;
            nanos += 100_000_000L;
        }
        assertBits(20.0, game.localTicks(tick(tickAt10Tps)), "GAME: 20 local ticks, the animation is half done if it lasts 40");
        assertBits(40.0, cosmetic.localTicks(TimePoint.cosmeticNanos(nanos)), "COSMETIC: 40 local ticks, on schedule in real time");
        Timing t = Timing.once(40);
        assertBits(0.5, t.sample(game.localTicks(tick(tickAt10Tps))).progress(), "GAME progress");
        assertEquals(Stage.AFTER, t.sample(cosmetic.localTicks(TimePoint.cosmeticNanos(nanos))).stage(), "COSMETIC finished");
    }

    @Test
    @DisplayName("Long-running: 10 million consecutive ticks from 2^40 are exact integers with exact loop phases, no drift (section 6.4)")
    void longRunningNoDrift() {
        long base = 1L << 40;
        Playback p = Playback.start(tick(base), 0.0, 1.0);
        Timing t = Timing.loop(1_200); // a 60-second loop
        for (long i = 0; i < 10_000_000L; i++) {
            double local = p.localTicks(tick(base + i));
            if (local != (double) i) {
                throw new AssertionError("drift at tick " + i + ": " + local);
            }
            if (i % 997 == 0) {
                TimingSample s = t.sample(local);
                assertEquals(i / 1_200, s.cycle(), "cycle at " + i);
                assertBits((double) (i % 1_200) / 1_200.0, s.progress(), "phase at " + i);
            }
        }
    }

    @Test
    @DisplayName("Integer pause/resume chains are exact: local time equals the playing span, after 100,000 control events (section 6.3)")
    void integerControlChainsAreExact() {
        SplittableRandom r = new SplittableRandom(11);
        Playback p = Playback.start(tick(0));
        long t = 0;
        long playing = 0;
        boolean paused = false;
        for (int i = 0; i < 100_000; i++) {
            long step = r.nextLong(0, 500);
            if (!paused) {
                playing += step;
            }
            t += step;
            p = paused ? p.resume(tick(t)) : p.pause(tick(t));
            paused = !paused;
            assertBits((double) playing, p.anchorTicks(), "after event " + i);
        }
    }

    @Test
    @DisplayName("Fractional rate chains: the error after n re-anchorings is at most 2 n 2^-52 max|local| against exact arithmetic (section 6.4)")
    void fractionalRateChainErrorBound() {
        SplittableRandom r = new SplittableRandom(12);
        Playback p = Playback.start(tick(0), 0.0, 0.3);
        BigDecimal exact = BigDecimal.ZERO;
        long anchor = 0;
        double maxAbs = 0;
        double rate = 0.3;
        int events = 10_000;
        MathContext mc = MathContext.UNLIMITED;
        for (int i = 0; i < events; i++) {
            long next = anchor + r.nextLong(1, 400);
            exact = exact.add(new BigDecimal(rate).multiply(BigDecimal.valueOf(next - anchor), mc), mc);
            rate = r.nextBoolean() ? 0.7 : -0.45;
            p = p.withRate(tick(next), rate);
            anchor = next;
            maxAbs = Math.max(maxAbs, Math.abs(p.anchorTicks()));
            double err = new BigDecimal(p.anchorTicks()).subtract(exact).abs().doubleValue();
            // per re-anchoring: fl(rate * dt) and fl(anchor + product), at most 1.5 * 2^-52 * max|local| together
            assertTrue(err <= 2.0 * (i + 1) * 0x1p-52 * Math.max(1.0, maxAbs), "event " + i + " error " + err);
        }
    }

    @Test
    @DisplayName("Discontinuities: a cosmetic gap jumps forward by design; pause/resume or seek removes it; a reset tick counter is rejected for control (section 6.5)")
    void discontinuities() {
        Playback p = Playback.start(TimePoint.cosmeticNanos(0));
        assertBits(200.0, p.localTicks(TimePoint.cosmeticNanos(10_000_000_000L)), "a 10 s freeze shows as +200 ticks");
        // caller-side gap handling: pause when the gap is detected at its start, resume after it
        Playback bridged = p.pause(TimePoint.cosmeticNanos(1_000_000_000L)).resume(TimePoint.cosmeticNanos(11_000_000_000L));
        assertBits(21.0, bridged.localTicks(TimePoint.cosmeticNanos(11_050_000_000L)), "the gap does not count");
        // or detected afterwards: seek back to the value before the gap
        Playback sought = p.seek(TimePoint.cosmeticNanos(11_000_000_000L), 20.0);
        assertBits(21.0, sought.localTicks(TimePoint.cosmeticNanos(11_050_000_000L)), "same result through seek");
        // the server tick counter restarted from 0: control events cannot go back in time
        Playback g = Playback.start(tick(5_000_000));
        assertThrows(IllegalArgumentException.class, () -> g.pause(tick(3)));
        assertBits(-4_999_997.0, g.localTicks(tick(3)), "evaluation still answers (BEFORE for any timing)");
        assertEquals(Stage.BEFORE, Timing.once(40).sample(g.localTicks(tick(3))).stage());
        assertBits(3.0, Playback.start(tick(0)).localTicks(tick(3)), "the fix is a new playback on the new counter");
    }

    @Test
    @DisplayName("Reversal: a negative rate runs a timing backwards through its start into BEFORE (sections 4, 5)")
    void reversal() {
        Playback p = Playback.start(tick(0)).withRate(tick(30), -1.0);
        Timing t = Timing.once(40);
        assertBits(0.75, t.sample(p.localTicks(tick(30))).progress(), "at the turn");
        assertBits(0.5, t.sample(p.localTicks(tick(40))).progress(), "running back");
        assertBits(0.0, t.sample(p.localTicks(tick(60))).progress(), "back at the start");
        assertEquals(Stage.BEFORE, t.sample(p.localTicks(tick(61))).stage(), "then before it");
    }
}
