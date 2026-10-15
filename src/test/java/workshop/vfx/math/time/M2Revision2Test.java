package workshop.vfx.math.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static workshop.vfx.math.time.M2Support.assertBits;
import static workshop.vfx.math.time.M2Support.tick;

import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import workshop.vfx.math.time.TimingSample.Stage;

/**
 * Regression tests for M2 contract revision 2 (docs/m2/M2-AMENDMENT-R2.md): P-R1 (COSMETIC wrap-around), P-A2
 * (control ordering against the stored anchor) and P-A3 (the representational limit of FOREVER). P-A1 is covered
 * by M2ReviewAdversarialTest.reanchoringErrorBounds.
 *
 * <p>Against the revision-1 implementation 1e85682 the P-R1 wrap tests fail. That is expected: they are the
 * acceptance criteria for the authorized correction. The GAME, domain, P-A2 and P-A3 tests pass before and after.
 */
@Tag("m2-r2")
class M2Revision2Test {
    private static final long MAX = Long.MAX_VALUE;
    private static final long MIN = Long.MIN_VALUE;

    private static TimePoint ns(long v) {
        return TimePoint.cosmeticNanos(v);
    }

    // ------------------------------------------------------------ P-R1: COSMETIC differences wrap

    @Test
    @DisplayName("P-R1: COSMETIC ticksSince across the Long.MAX_VALUE -> Long.MIN_VALUE wrap is the elapsed time, forwards and backwards (section 3 [R2])")
    void cosmeticDifferenceAcrossTheWrap() {
        assertBits(20.0 / 50_000_000.0, ns(MIN + 10).ticksSince(ns(MAX - 9)), "20 ns forward across the wrap");
        assertBits(-20.0 / 50_000_000.0, ns(MAX - 9).ticksSince(ns(MIN + 10)), "20 ns backward across the wrap");
        assertBits(2e-8, ns(MIN).ticksSince(ns(MAX)), "MIN is 1 ns after MAX");
        assertBits(-2e-8, ns(MAX).ticksSince(ns(MIN)), "MAX is 1 ns before MIN");
        assertBits(1.0, ns(MIN + 49_999_999).ticksSince(ns(MAX)), "exactly one tick across the wrap");
    }

    @Test
    @DisplayName("P-R1: zero elapsed time is +0.0 everywhere, including at both ends of the long range (section 3 [R2])")
    void zeroElapsed() {
        for (long v : new long[] {MIN, MIN + 1, -1, 0, 1, MAX - 1, MAX}) {
            assertBits(0.0, ns(v).ticksSince(ns(v)), "value " + v);
        }
    }

    @Test
    @DisplayName("P-R1: the wrapping difference d is valid for |true elapsed| < 2^63; d = -2^63 is read as 2^63 ns backwards (section 3 [R2])")
    void intervalRestriction() {
        assertBits((double) MAX / 50_000_000.0, ns(MAX).ticksSince(ns(0)), "the largest forward interval, 2^63 - 1 ns");
        assertBits((double) MAX / 50_000_000.0, ns(MIN).ticksSince(ns(1)), "the same interval across the wrap (the changed golden row)");
        assertBits(-0x1p63 / 50_000_000.0, ns(MIN).ticksSince(ns(0)), "exactly 2^63 ns apart: interpreted as backwards");
        assertThrows(IllegalArgumentException.class, () -> Playback.start(ns(0)).pause(ns(MIN)), "so a control there precedes the anchor");
    }

    @Test
    @DisplayName("P-R1: COSMETIC playback evaluates and accepts control events across the wrap; earlier instants are rejected by wrapped order (section 4 [R2])")
    void cosmeticPlaybackAcrossTheWrap() {
        Playback p = Playback.start(ns(MAX - 9));
        assertBits(4e-7, p.localTicks(ns(MIN + 10)), "20 ns after the anchor");
        assertBits(-4e-7, Playback.start(ns(MIN + 10)).localTicks(ns(MAX - 9)), "evaluation before the anchor extrapolates backwards");
        Playback paused = p.pause(ns(MIN + 10));
        assertBits(4e-7, paused.anchorTicks(), "pause across the wrap re-anchors at the elapsed value");
        assertEquals(ns(MIN + 10), paused.anchor());
        assertBits(4e-7, paused.pause(ns(MIN + 10)).anchorTicks(), "control at the anchor instant is allowed");
        assertThrows(IllegalArgumentException.class, () -> paused.resume(ns(MAX)), "MAX is 11 ns before the anchor");
        assertThrows(IllegalArgumentException.class, () -> paused.seek(ns(MIN + 9), 0.0), "1 ns before the anchor");
        assertThrows(IllegalArgumentException.class, () -> p.withRate(ns(MAX - 10), 2.0), "1 ns before the original anchor");
        assertThrows(IllegalArgumentException.class, () -> p.restart(ns(MAX - 10)));
        Playback resumed = paused.resume(ns(MIN + 50_000_010));
        assertBits(4e-7 + 1.0, resumed.localTicks(ns(MIN + 100_000_010)), "one tick after resuming, 50 ms after the pause");
        Playback restarted = resumed.restart(ns(MIN + 100_000_010));
        assertBits(0.0, restarted.localTicks(ns(MIN + 100_000_010)), "restart across the wrap");
    }

    @Test
    @DisplayName("P-R1: translating a COSMETIC history so that it crosses the wrap changes no bit (section 3 [R2])")
    void translationAcrossTheWrap() {
        SplittableRandom r = new SplittableRandom(0x52_2026_1015L);
        for (int i = 0; i < 20_000; i++) {
            long d1 = r.nextLong(0, 1_000_000_000_000L);
            long d2 = d1 + r.nextLong(0, 1_000_000_000_000L);
            long d3 = d2 + r.nextLong(-1_000_000_000L, 1_000_000_000_000L);
            double off = r.nextDouble(-1e3, 1e3);
            double rate = r.nextDouble(-4, 4);
            long base = r.nextLong(-(1L << 40), 1L << 40);
            long wrapped = MAX - r.nextLong(0, d2 + 1); // the wrap falls inside the history
            double a = Playback.start(ns(base), off, rate).pause(ns(base + d1)).withRate(ns(base + d1), -rate)
                    .resume(ns(base + d2)).localTicks(ns(base + d3));
            double b = Playback.start(ns(wrapped), off, rate).pause(ns(wrapped + d1)).withRate(ns(wrapped + d1), -rate)
                    .resume(ns(wrapped + d2)).localTicks(ns(wrapped + d3)); // Java + wraps, like nanoTime
            assertEquals(Double.doubleToRawLongBits(a), Double.doubleToRawLongBits(b), "case " + i);
        }
    }

    @Test
    @DisplayName("P-R1: domain checks are unchanged: mixing COSMETIC and GAME is an IllegalArgumentException, also near the wrap (sections 3, 4)")
    void domainMismatchUnchanged() {
        assertThrows(IllegalArgumentException.class, () -> ns(MIN).ticksSince(tick(MAX)));
        assertThrows(IllegalArgumentException.class, () -> tick(MIN).ticksSince(ns(MAX)));
        assertThrows(IllegalArgumentException.class, () -> ns(MIN).ticksSince(null));
        Playback c = Playback.start(ns(MAX));
        assertThrows(IllegalArgumentException.class, () -> c.pause(tick(MIN)));
        assertThrows(IllegalArgumentException.class, () -> c.localTicks(tick(0)));
        Playback g = Playback.start(tick(MAX));
        assertThrows(IllegalArgumentException.class, () -> g.pause(ns(MIN)));
    }

    @Test
    @DisplayName("P-R1: GAME keeps checked subtraction and raw ordering: no wrap-around for tick counters (sections 3, 4)")
    void gameUnchanged() {
        assertThrows(ArithmeticException.class, () -> tick(MIN).ticksSince(tick(MAX)));
        assertThrows(ArithmeticException.class, () -> tick(MAX).ticksSince(tick(MIN)));
        assertThrows(ArithmeticException.class, () -> Playback.start(tick(MAX - 9)).localTicks(tick(MIN + 10)), "no wrapped evaluation");
        assertThrows(IllegalArgumentException.class, () -> Playback.start(tick(MAX - 9)).pause(tick(MIN + 10)), "MIN + 10 < MAX - 9 for ticks");
        assertBits(-1.0, tick(MIN).ticksSince(tick(MIN + 1)), "ordinary differences unchanged");
    }

    // ------------------------------------------------------------ P-A2: ordering against the stored anchor

    @Test
    @DisplayName("P-A2: no-op controls return the same object and keep the stored anchor; the next control is checked against that anchor only (section 4 [R2])")
    void controlOrderingAgainstStoredAnchor() {
        Playback paused = Playback.start(tick(0)).pause(tick(100));
        Playback noOp = paused.pause(tick(200));
        assertSame(paused, noOp, "pause while paused is a no-op");
        assertEquals(tick(100), noOp.anchor(), "the anchor stays at the last effective control");
        Playback resumed = noOp.resume(tick(150)); // allowed: 150 is not before the stored anchor 100
        assertEquals(paused.resume(tick(150)), resumed, "identical to the history without the no-op");
        assertBits(100.0 + 10.0, resumed.localTicks(tick(160)), "plays from the held value");
        assertThrows(IllegalArgumentException.class, () -> noOp.resume(tick(99)), "before the stored anchor");
        Playback playing = Playback.start(tick(10));
        assertSame(playing, playing.resume(tick(500)), "resume while playing is a no-op");
        assertEquals(tick(10), playing.resume(tick(500)).anchor());
    }

    // ------------------------------------------------------------ P-A3: representational limit of FOREVER

    @Test
    @DisplayName("P-A3: FOREVER reports AFTER only when the cycle index reaches 2^63 (documented limitation, section 5 [R2])")
    void foreverRepresentationalLimit() {
        assertEquals(new TimingSample(Stage.AFTER, Long.MAX_VALUE - 1, 1.0), Timing.loop(Double.MIN_VALUE).sample(1.0),
                "cycle index 2^1074: beyond 2^63");
        assertEquals(Stage.AFTER, Timing.loop(1.0).sample(0x1p63).stage(), "exactly 2^63 cycles");
        TimingSample below = Timing.loop(1.0).sample(Math.nextDown(0x1p63));
        assertEquals(Stage.ACTIVE, below.stage(), "the largest double below 2^63 is still ACTIVE");
        assertEquals(Long.MAX_VALUE - 1023, below.cycle(), "cycle 2^63 - 1024 (the double itself)");
        assertEquals(Stage.ACTIVE, Timing.pingPong(1.0).sample(1e15).stage(), "within the guaranteed domain FOREVER never ends");
        assertTrue(Timing.loop(1.0).sample(0x1p51 - 1).cycle() == (1L << 51) - 1, "exact at the domain edge");
    }
}
