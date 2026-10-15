package workshop.vfx.math.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static workshop.vfx.math.time.M2Support.assertBits;
import static workshop.vfx.math.time.M2Support.tick;

import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Contract section 4: the playback map from an instant to local animation ticks. */
class PlaybackTest {

    @Test
    @DisplayName("Construction: anchor required, anchorTicks and rate finite; start() is local 0, rate 1, playing (sections 4, 7)")
    void construction() {
        Playback p = Playback.start(tick(100));
        assertEquals(tick(100), p.anchor());
        assertBits(0.0, p.anchorTicks(), "anchorTicks");
        assertBits(1.0, p.rate(), "rate");
        assertFalse(p.paused());
        Playback q = Playback.start(tick(5), -20.0, 0.5);
        assertBits(-20.0, q.anchorTicks(), "initial offset");
        assertBits(0.5, q.rate(), "rate");
        assertThrows(IllegalArgumentException.class, () -> new Playback(null, 0, 1, false));
        assertThrows(IllegalArgumentException.class, () -> Playback.start(null));
        for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new Playback(tick(0), bad, 1, false), "anchorTicks " + bad);
            assertThrows(IllegalArgumentException.class, () -> new Playback(tick(0), 0, bad, false), "rate " + bad);
            assertThrows(IllegalArgumentException.class, () -> Playback.start(tick(0), bad, 1), "start offset " + bad);
        }
    }

    @Test
    @DisplayName("localTicks = anchorTicks + rate * ticksSince(anchor); integer data give exact integers (sections 4, 6.3)")
    void localTicksFormula() {
        Playback p = Playback.start(tick(100));
        assertBits(0.0, p.localTicks(tick(100)), "at the anchor");
        assertBits(1.0, p.localTicks(tick(101)), "one tick later");
        assertBits(40.0, p.localTicks(tick(140)), "forty ticks later");
        assertBits(-1.0, p.localTicks(tick(99)), "before the anchor: affine extrapolation, no history");
        SplittableRandom r = new SplittableRandom(5);
        for (int i = 0; i < 100_000; i++) {
            long a = r.nextLong(-(1L << 40), 1L << 40);
            long t = a + r.nextLong(-(1L << 40), 1L << 40);
            double off = r.nextInt(-1_000_000, 1_000_000);
            double rate = r.nextInt(-8, 9);
            Playback q = Playback.start(tick(a), off, rate);
            assertBits(off + rate * (double) (t - a), q.localTicks(tick(t)), "formula");
            assertEquals(0.0, q.localTicks(tick(t)) - Math.rint(q.localTicks(tick(t))), "integer result " + q);
        }
    }

    @Test
    @DisplayName("pause holds the value exactly, even far in the future; resume continues from it; a paused span adds exactly nothing (section 4)")
    void pauseAndResume() {
        Playback p = Playback.start(tick(0), 0.0, 0.37);
        Playback paused = p.pause(tick(1000));
        double held = p.localTicks(tick(1000));
        assertTrue(paused.paused());
        assertBits(held, paused.anchorTicks(), "pause re-anchors at the current value");
        assertBits(held, paused.localTicks(tick(1000)), "at the pause");
        assertBits(held, paused.localTicks(tick(Long.MAX_VALUE)), "far future, no overflow while paused");
        assertBits(held, paused.localTicks(tick(Long.MIN_VALUE)), "far past while paused");
        Playback resumed = paused.resume(tick(5000));
        assertFalse(resumed.paused());
        assertBits(held, resumed.localTicks(tick(5000)), "resume continues from the held value");
        assertBits(held + 0.37 * 10.0, resumed.localTicks(tick(5010)), "then advances at the kept rate");
        // integer case: pausing for any span equals starting later by that span
        Playback a = Playback.start(tick(10)).pause(tick(30)).resume(tick(1_000_030));
        Playback b = Playback.start(tick(1_000_010));
        for (long t = 1_000_030; t < 1_000_200; t++) {
            assertBits(b.localTicks(tick(t)), a.localTicks(tick(t)), "shifted by the paused span at " + t);
        }
    }

    @Test
    @DisplayName("pause on a paused playback and resume on a playing one return the same object (idempotent), after validating the instant (section 4)")
    void idempotentControls() {
        Playback paused = Playback.start(tick(0)).pause(tick(10));
        assertSame(paused, paused.pause(tick(20)));
        Playback playing = Playback.start(tick(0));
        assertSame(playing, playing.resume(tick(5)));
        assertThrows(IllegalArgumentException.class, () -> paused.pause(tick(9)), "still monotonic");
        assertThrows(IllegalArgumentException.class, () -> playing.resume(tick(-1)), "still monotonic");
    }

    @Test
    @DisplayName("withRate keeps the value continuous at the switch instant; negative rates reverse; rate 0 freezes without pausing (section 4)")
    void rateChanges() {
        Playback p = Playback.start(tick(0), 0.0, 0.3);
        double before = p.localTicks(tick(17));
        Playback q = p.withRate(tick(17), -1.75);
        assertBits(before, q.localTicks(tick(17)), "continuity at the switch");
        assertBits(before, q.anchorTicks(), "re-anchored at the switch value");
        assertBits(before + -1.75 * 4.0, q.localTicks(tick(21)), "reversed afterwards");
        Playback frozen = q.withRate(tick(30), 0.0);
        assertFalse(frozen.paused(), "rate 0 is not paused");
        assertBits(frozen.anchorTicks(), frozen.localTicks(tick(1_000_000)), "rate 0 holds the value");
        Playback pausedThenRate = Playback.start(tick(0)).pause(tick(10)).withRate(tick(20), 2.0);
        assertTrue(pausedThenRate.paused(), "withRate keeps the paused state");
        assertBits(10.0, pausedThenRate.localTicks(tick(50)), "and the held value");
        assertBits(10.0 + 2.0 * 5.0, pausedThenRate.resume(tick(60)).localTicks(tick(65)), "the new rate applies on resume");
        assertThrows(IllegalArgumentException.class, () -> p.withRate(tick(20), Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> p.withRate(tick(20), Double.NEGATIVE_INFINITY));
    }

    @Test
    @DisplayName("seek sets the value at the instant and keeps rate and pause state; restart is local 0, playing, same rate (section 4)")
    void seekAndRestart() {
        Playback p = Playback.start(tick(0), 0.0, -2.0);
        Playback s = p.seek(tick(50), 12.5);
        assertBits(12.5, s.localTicks(tick(50)), "seek value");
        assertBits(12.5 - 2.0 * 3.0, s.localTicks(tick(53)), "rate kept");
        Playback ps = p.pause(tick(10)).seek(tick(20), 4.0);
        assertTrue(ps.paused(), "seek keeps paused");
        assertBits(4.0, ps.localTicks(tick(99)), "paused at the sought value");
        Playback rs = p.pause(tick(10)).restart(tick(30));
        assertFalse(rs.paused(), "restart plays");
        assertBits(0.0, rs.localTicks(tick(30)), "from zero");
        assertBits(-2.0 * 5.0, rs.localTicks(tick(35)), "with the kept rate");
        assertThrows(IllegalArgumentException.class, () -> p.seek(tick(5), Double.NaN));
    }

    @Test
    @DisplayName("Control instants: same domain, not before the anchor (equal allowed); localTicks also validates its domain (sections 4, 7)")
    void controlInstantRules() {
        Playback p = Playback.start(tick(100));
        assertBits(0.0, p.pause(tick(100)).anchorTicks(), "control at the anchor instant is allowed");
        assertThrows(IllegalArgumentException.class, () -> p.pause(tick(99)));
        assertThrows(IllegalArgumentException.class, () -> p.withRate(tick(99), 1));
        assertThrows(IllegalArgumentException.class, () -> p.seek(tick(99), 0));
        assertThrows(IllegalArgumentException.class, () -> p.restart(tick(99)));
        assertThrows(IllegalArgumentException.class, () -> p.pause(TimePoint.cosmeticNanos(200)));
        assertThrows(IllegalArgumentException.class, () -> p.pause(null));
        assertThrows(IllegalArgumentException.class, () -> p.localTicks(TimePoint.cosmeticNanos(200)));
        assertThrows(IllegalArgumentException.class, () -> p.localTicks(null));
        Playback paused = p.pause(tick(150));
        assertThrows(IllegalArgumentException.class, () -> paused.localTicks(TimePoint.cosmeticNanos(0)), "even when paused");
    }

    @Test
    @DisplayName("Unrepresentable results: instant overflow while playing and a non-finite re-anchored value are ArithmeticExceptions (sections 4, 7)")
    void arithmeticFailures() {
        Playback p = Playback.start(tick(Long.MIN_VALUE));
        assertThrows(ArithmeticException.class, () -> p.localTicks(tick(Long.MAX_VALUE)), "instant difference overflows");
        Playback fast = Playback.start(tick(0), 0.0, 1e300);
        assertEquals(Double.POSITIVE_INFINITY, fast.localTicks(tick(10_000_000_000L)), "evaluation itself is raw IEEE");
        assertThrows(ArithmeticException.class, () -> fast.pause(tick(10_000_000_000L)), "pause would anchor at infinity");
        assertThrows(ArithmeticException.class, () -> fast.withRate(tick(10_000_000_000L), 1.0));
        assertBits(0.0, fast.restart(tick(10_000_000_000L)).anchorTicks(), "restart and seek do not evaluate, so they succeed");
    }

    @Test
    @DisplayName("COSMETIC playback runs at one local tick per 50 ms of supplied nanoseconds (sections 3, 4)")
    void cosmeticPlayback() {
        Playback p = Playback.start(TimePoint.cosmeticNanos(1_000_000_000L));
        assertBits(1.0, p.localTicks(TimePoint.cosmeticNanos(1_050_000_000L)), "50 ms");
        assertBits(20.0, p.localTicks(TimePoint.cosmeticNanos(2_000_000_000L)), "1 s");
        assertBits(2e-8, p.localTicks(TimePoint.cosmeticNanos(1_000_000_001L)), "1 ns");
        assertThrows(IllegalArgumentException.class, () -> p.localTicks(tick(0)), "game ticks are a different clock");
    }
}
