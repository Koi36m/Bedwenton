package workshop.vfx.math.time;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static workshop.vfx.math.time.M2Support.assertBits;
import static workshop.vfx.math.time.M2Support.tick;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Executable forms of the review findings that would change behaviour (review/m2/M2-CLAUDE-REVIEW.md). Each test
 * asserts the PROPOSED amended behaviour and fails against 1e85682 by design. They are disabled until the owner
 * decides; delete a test if its proposal is rejected. Findings that are documentation-only (A1, A2 option a) have
 * no test here; A1's corrected bound is checked in M2ReviewAdversarialTest.
 */
@Tag("m2-review-finding")
@Disabled("Review proposals awaiting the owner's decision (review/m2/M2-CLAUDE-REVIEW.md)")
class M2ReviewFindingsTest {

    @Test
    @DisplayName("R1 (proposal P-R1): COSMETIC differences use wrapping subtraction, as System.nanoTime's Javadoc prescribes")
    void r1CosmeticWrapAround() {
        // 20 ns elapse while the arbitrary nanoTime origin makes the value wrap from Long.MAX_VALUE to Long.MIN_VALUE
        TimePoint before = TimePoint.cosmeticNanos(Long.MAX_VALUE - 9);
        TimePoint after = TimePoint.cosmeticNanos(Long.MIN_VALUE + 10);
        assertBits(20.0 / 50_000_000.0, after.ticksSince(before), "20 ns, not an overflow");
        Playback p = Playback.start(before);
        assertDoesNotThrow(() -> p.pause(after), "a later instant is not 'before the anchor'");
        assertBits(4e-7, p.localTicks(after), "local time after 20 ns");
    }

    @Test
    @DisplayName("A2 (option b only): a no-op control still advances the last control instant, so a later control may not precede it")
    void a2NoOpControlAdvancesTheControlClock() {
        Playback p = Playback.start(tick(0)).pause(tick(100)).pause(tick(200)); // the second pause is a no-op
        assertThrows(IllegalArgumentException.class, () -> p.resume(tick(150)), "150 precedes the last control at 200");
    }

    @Test
    @DisplayName("A3 (option c only): a FOREVER timing never reports AFTER, even when the cycle index exceeds 2^63")
    void a3ForeverNeverAfter() {
        TimingSample s = Timing.loop(Double.MIN_VALUE).sample(1.0);
        assertNotEquals(TimingSample.Stage.AFTER, s.stage(), "FOREVER reported AFTER: " + s);
    }
}
