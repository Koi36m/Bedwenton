package workshop.vfx.math.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MathUtilTest {
    @Test
    @DisplayName("LENGTH_EPS is the documented 1e-12")
    void lengthEps() {
        assertEquals(1e-12, MathUtil.LENGTH_EPS, 0.0);
    }

    @Test
    @DisplayName("clamp: inside, outside, boundaries, infinities, NaN maps to the lower bound")
    void clamp() {
        assertEquals(0.5, MathUtil.clamp(0.5, 0, 1), 0.0);
        assertEquals(0.0, MathUtil.clamp(-3, 0, 1), 0.0);
        assertEquals(1.0, MathUtil.clamp(7, 0, 1), 0.0);
        assertEquals(0.0, MathUtil.clamp(0, 0, 1), 0.0);
        assertEquals(1.0, MathUtil.clamp(1, 0, 1), 0.0);
        assertEquals(1.0, MathUtil.clamp(Double.POSITIVE_INFINITY, 0, 1), 0.0);
        assertEquals(0.0, MathUtil.clamp(Double.NEGATIVE_INFINITY, 0, 1), 0.0);
        assertEquals(0.25, MathUtil.clamp(Double.NaN, 0.25, 1), 0.0);
        assertEquals(2.0, MathUtil.clamp(5, 2, 2), 0.0);
    }

    @Test
    @DisplayName("clamp rejects an inverted or NaN range")
    void clampInvalidRange() {
        assertThrows(IllegalArgumentException.class, () -> MathUtil.clamp(0.5, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> MathUtil.clamp(0.5, Double.NaN, 1));
        assertThrows(IllegalArgumentException.class, () -> MathUtil.clamp(0.5, 0, Double.NaN));
    }

    @Test
    @DisplayName("approxEquals is inclusive at the tolerance and false for NaN")
    void approxEquals() {
        assertTrue(MathUtil.approxEquals(1.0, 1.0, 0.0));
        assertTrue(MathUtil.approxEquals(1.0, 1.5, 0.5));
        assertFalse(MathUtil.approxEquals(1.0, 1.6, 0.5));
        assertFalse(MathUtil.approxEquals(Double.NaN, 1.0, 1e9));
        assertFalse(MathUtil.approxEquals(1.0, Double.NaN, 1e9));
    }
}
