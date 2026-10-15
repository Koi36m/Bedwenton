package workshop.vfx.math.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static workshop.vfx.math.time.M2Support.assertBits;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Contract section 3: time domains and instants. */
class TimePointTest {

    @Test
    @DisplayName("Factories set the domain and the raw value; a null domain is rejected (section 3)")
    void factoriesAndValidation() {
        TimePoint g = TimePoint.gameTick(-42);
        assertEquals(TimeDomain.GAME, g.domain());
        assertEquals(-42L, g.value());
        TimePoint c = TimePoint.cosmeticNanos(Long.MAX_VALUE);
        assertEquals(TimeDomain.COSMETIC, c.domain());
        assertEquals(Long.MAX_VALUE, c.value());
        assertEquals(new TimePoint(TimeDomain.GAME, 7), TimePoint.gameTick(7), "value semantics");
        assertNotEquals(TimePoint.gameTick(7), TimePoint.cosmeticNanos(7), "domains are part of the value");
        assertThrows(IllegalArgumentException.class, () -> new TimePoint(null, 0));
    }

    @Test
    @DisplayName("GAME ticksSince is the exact tick difference converted once to double (section 3)")
    void gameDifferences() {
        assertBits(0.0, TimePoint.gameTick(5).ticksSince(TimePoint.gameTick(5)), "same instant is +0.0");
        assertBits(3.0, TimePoint.gameTick(5).ticksSince(TimePoint.gameTick(2)), "forward");
        assertBits(-7.0, TimePoint.gameTick(-3).ticksSince(TimePoint.gameTick(4)), "backward");
        assertBits(9_007_199_254_740_992.0, TimePoint.gameTick((1L << 53) + 1).ticksSince(TimePoint.gameTick(0)),
                "2^53 + 1 rounds to 2^53 (nearest even), the only rounding");
        SplittableRandom r = new SplittableRandom(3);
        for (int i = 0; i < 100_000; i++) {
            long a = r.nextLong(-(1L << 52), 1L << 52);
            long b = r.nextLong(-(1L << 52), 1L << 52);
            assertBits((double) (a - b), TimePoint.gameTick(a).ticksSince(TimePoint.gameTick(b)), a + " - " + b);
        }
    }

    @Test
    @DisplayName("COSMETIC ticksSince is the exact nanosecond difference divided by 5e7 with one rounding below 2^53 ns (section 3)")
    void cosmeticDifferences() {
        assertBits(1.0, TimePoint.cosmeticNanos(50_000_000).ticksSince(TimePoint.cosmeticNanos(0)), "one tick");
        assertBits(20.0, TimePoint.cosmeticNanos(1_000_000_000).ticksSince(TimePoint.cosmeticNanos(0)), "one second");
        assertBits(2e-8, TimePoint.cosmeticNanos(1).ticksSince(TimePoint.cosmeticNanos(0)), "one nanosecond");
        assertBits(-1.00000002, TimePoint.cosmeticNanos(-50_000_001).ticksSince(TimePoint.cosmeticNanos(0)), "negative");
        SplittableRandom r = new SplittableRandom(4);
        MathContext mc = new MathContext(40);
        for (int i = 0; i < 50_000; i++) {
            long a = r.nextLong(-(1L << 52), 1L << 52);
            long b = r.nextLong(-(1L << 52), 1L << 52);
            double expected = new BigDecimal(a - b).divide(BigDecimal.valueOf(50_000_000L), mc).doubleValue();
            assertBits(expected, TimePoint.cosmeticNanos(a).ticksSince(TimePoint.cosmeticNanos(b)), a + " - " + b + " ns");
        }
    }

    @Test
    @DisplayName("Mixing domains or passing null is an IllegalArgumentException; an unrepresentable difference is an ArithmeticException (sections 3, 7)")
    void invalidUse() {
        assertThrows(IllegalArgumentException.class, () -> TimePoint.gameTick(1).ticksSince(TimePoint.cosmeticNanos(0)));
        assertThrows(IllegalArgumentException.class, () -> TimePoint.cosmeticNanos(1).ticksSince(TimePoint.gameTick(0)));
        assertThrows(IllegalArgumentException.class, () -> TimePoint.gameTick(1).ticksSince(null));
        assertThrows(ArithmeticException.class, () -> TimePoint.gameTick(Long.MAX_VALUE).ticksSince(TimePoint.gameTick(-1)));
        assertThrows(ArithmeticException.class, () -> TimePoint.gameTick(Long.MIN_VALUE).ticksSince(TimePoint.gameTick(1)));
        assertThrows(ArithmeticException.class, () -> TimePoint.cosmeticNanos(Long.MIN_VALUE).ticksSince(TimePoint.cosmeticNanos(Long.MAX_VALUE)));
        // the extreme representable differences do not throw
        assertBits(9.223372036854775807E18, TimePoint.gameTick(Long.MAX_VALUE).ticksSince(TimePoint.gameTick(0)), "Long.MAX_VALUE ticks");
        assertBits(-1.0, TimePoint.gameTick(Long.MIN_VALUE).ticksSince(TimePoint.gameTick(Long.MIN_VALUE + 1)), "adjacent at the bottom");
    }
}
