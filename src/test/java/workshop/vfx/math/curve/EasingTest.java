package workshop.vfx.math.curve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Contract section 6: easing. Oracles are exact closed forms, properties and the generator's exact values. */
class EasingTest {
    private static final double TOL = 0x1p-50; // contract 6: absolute error bound
    private static final Set<Easing> POWER = EnumSet.complementOf(EnumSet.of(Easing.LINEAR, Easing.SMOOTHSTEP, Easing.SMOOTHERSTEP));
    private static final Set<Easing> SYMMETRIC = EnumSet.of(Easing.LINEAR, Easing.SMOOTHSTEP, Easing.SMOOTHERSTEP,
            Easing.QUAD_IN_OUT, Easing.CUBIC_IN_OUT, Easing.QUART_IN_OUT, Easing.QUINT_IN_OUT);

    @Test
    @DisplayName("Exactly the 15 specified easings exist, in the specified order (section 6)")
    void catalogue() {
        assertEquals(List.of("LINEAR", "SMOOTHSTEP", "SMOOTHERSTEP", "QUAD_IN", "QUAD_OUT", "QUAD_IN_OUT", "CUBIC_IN",
                "CUBIC_OUT", "CUBIC_IN_OUT", "QUART_IN", "QUART_OUT", "QUART_IN_OUT", "QUINT_IN", "QUINT_OUT", "QUINT_IN_OUT"),
                EnumSet.allOf(Easing.class).stream().map(Enum::name).collect(Collectors.toList()));
    }

    @Test
    @DisplayName("Endpoints and clamping: f(t) = 0 for t <= 0 (including -0.0 and -infinity), 1 for t >= 1 (including +infinity); NaN is rejected (section 6)")
    void endpointsAndClamping() {
        for (Easing e : Easing.values()) {
            for (double t : new double[] {0.0, -0.0, -1e-300, -1.0, -1e300, Double.NEGATIVE_INFINITY}) {
                assertEquals(0L, Double.doubleToRawLongBits(e.apply(t)), e + " at " + t + " must be +0.0");
            }
            for (double t : new double[] {1.0, Math.nextUp(1.0), 2.0, 1e300, Double.POSITIVE_INFINITY}) {
                assertEquals(1.0, e.apply(t), 0.0, e + " at " + t);
            }
            assertThrows(IllegalArgumentException.class, () -> e.apply(Double.NaN), e + " NaN");
            if (SYMMETRIC.contains(e)) {
                assertEquals(0.5, e.apply(0.5), 0.0, e + " passes through (1/2, 1/2) exactly");
            }
        }
    }

    @Test
    @DisplayName("Closed-form values at hand-checked points (section 6)")
    void handValues() {
        assertEquals(0.15625, Easing.SMOOTHSTEP.apply(0.25), 0.0, "3/16 - 2/64");
        assertEquals(0.84375, Easing.SMOOTHSTEP.apply(0.75), 0.0);
        assertEquals(0.103515625, Easing.SMOOTHERSTEP.apply(0.25), 0.0, "6/1024 - 15/256 + 10/64");
        assertEquals(0.0625, Easing.QUAD_IN.apply(0.25), 0.0);
        assertEquals(0.4375, Easing.QUAD_OUT.apply(0.25), 0.0);
        assertEquals(0.125, Easing.QUAD_IN_OUT.apply(0.25), 0.0, "2 t^2");
        assertEquals(0.875, Easing.QUAD_IN_OUT.apply(0.75), 0.0, "1 - (2 - 2t)^2 / 2");
        assertEquals(0.0625, Easing.CUBIC_IN_OUT.apply(0.25), 0.0, "4 t^3");
        assertEquals(1.0 / 1024, Easing.QUINT_IN.apply(0.25), 0.0);
        assertEquals(0.375, Easing.LINEAR.apply(0.375), 0.0);
    }

    @Test
    @DisplayName("Range and accuracy fuzz: 0 <= f <= 1 and |f - exact| <= 2^-50 against an exact rational evaluation (section 6)")
    void rangeAndAccuracy() {
        SplittableRandom r = new SplittableRandom(31);
        java.math.MathContext mc = new java.math.MathContext(80);
        for (int i = 0; i < 40_000; i++) {
            double t = switch (i % 4) {
                case 0 -> r.nextDouble();
                case 1 -> r.nextDouble() * 1e-6;
                case 2 -> 1 - r.nextDouble() * 1e-6;
                default -> 0.5 + r.nextDouble(-1e-9, 1e-9);
            };
            for (Easing e : Easing.values()) {
                double v = e.apply(t);
                assertTrue(v >= 0 && v <= 1, e + " range at " + t);
                java.math.BigDecimal exact = exact(e, new java.math.BigDecimal(t), mc);
                double err = new java.math.BigDecimal(v).subtract(exact).abs().doubleValue();
                if (err > TOL) {
                    fail(e + " at " + t + ": error " + err);
                }
            }
        }
    }

    /** Exact polynomial definitions (not the contract's recommended split evaluation). */
    static java.math.BigDecimal exact(Easing e, java.math.BigDecimal t, java.math.MathContext mc) {
        java.math.BigDecimal one = java.math.BigDecimal.ONE;
        java.math.BigDecimal half = new java.math.BigDecimal("0.5");
        switch (e) {
            case LINEAR:
                return t;
            case SMOOTHSTEP:
                return t.pow(2).multiply(java.math.BigDecimal.valueOf(3)).subtract(t.pow(3).multiply(java.math.BigDecimal.valueOf(2)));
            case SMOOTHERSTEP:
                return t.pow(5).multiply(java.math.BigDecimal.valueOf(6)).subtract(t.pow(4).multiply(java.math.BigDecimal.valueOf(15)))
                        .add(t.pow(3).multiply(java.math.BigDecimal.TEN));
            default:
                break;
        }
        String n = e.name();
        int deg = n.startsWith("QUAD") ? 2 : n.startsWith("CUBIC") ? 3 : n.startsWith("QUART") ? 4 : 5;
        if (n.endsWith("_IN_OUT")) {
            return t.compareTo(half) < 0 ? t.pow(deg).multiply(java.math.BigDecimal.valueOf(1L << (deg - 1)))
                    : one.subtract(java.math.BigDecimal.valueOf(2).subtract(t.multiply(java.math.BigDecimal.valueOf(2))).pow(deg).multiply(half));
        }
        if (n.endsWith("_OUT")) {
            return one.subtract(one.subtract(t).pow(deg));
        }
        return t.pow(deg);
    }

    @Test
    @DisplayName("Power easings are exactly monotone on doubles; smoothstep and smootherstep are monotone up to 2^-49 (section 6, DM3-11)")
    void monotonicity() {
        SplittableRandom r = new SplittableRandom(32);
        double[] starts = {0.0, Double.MIN_VALUE, 1e-200, 1e-8, 0.25, Math.nextDown(0.5) - 2e-13, 0.5, 0.75, 1 - 1e-9, Math.nextDown(1.0) - 1e-12};
        for (Easing e : Easing.values()) {
            double slack = POWER.contains(e) || e == Easing.LINEAR ? 0.0 : 2 * TOL;
            for (int k = 0; k < starts.length + 200; k++) {
                double x = k < starts.length ? starts[k] : r.nextDouble();
                double prev = e.apply(x);
                for (int i = 0; i < 3_000 && x < 1; i++) {
                    x = Math.nextUp(x);
                    double v = e.apply(x);
                    if (v < prev - slack) {
                        fail(e + " decreases at " + x + ": " + prev + " -> " + v);
                    }
                    prev = v;
                }
            }
            for (int i = 0; i < 100_000; i++) {
                double a = r.nextDouble(), b = r.nextDouble();
                double lo = Math.min(a, b), hi = Math.max(a, b);
                assertTrue(e.apply(hi) >= e.apply(lo) - slack, e + " at " + lo + ", " + hi);
            }
        }
    }

    @Test
    @DisplayName("Symmetry f(1 - t) = 1 - f(t) for symmetric easings and duality OUT(t) = 1 - IN(1 - t), to 2^-49 (section 6)")
    void symmetryAndDuality() {
        SplittableRandom r = new SplittableRandom(33);
        Easing[][] pairs = {{Easing.QUAD_IN, Easing.QUAD_OUT}, {Easing.CUBIC_IN, Easing.CUBIC_OUT},
                {Easing.QUART_IN, Easing.QUART_OUT}, {Easing.QUINT_IN, Easing.QUINT_OUT}};
        for (int i = 0; i < 50_000; i++) {
            double t = r.nextDouble();
            double s = 1 - t; // exact for t >= 1/2; within an ulp otherwise, which the tolerance covers
            for (Easing e : SYMMETRIC) {
                assertEquals(1 - e.apply(t), e.apply(s), 4 * TOL, e + " symmetry at " + t);
            }
            for (Easing[] p : pairs) {
                assertEquals(1 - p[0].apply(s), p[1].apply(t), 4 * TOL, p[1] + " duality at " + t);
            }
        }
    }

    @Test
    @DisplayName("Continuity: IN_OUT easings join at 1/2; smoothstep/smootherstep have zero slope at both ends (C1/C2 ends) (section 6)")
    void continuityAndEndSlopes() {
        for (Easing e : List.of(Easing.QUAD_IN_OUT, Easing.CUBIC_IN_OUT, Easing.QUART_IN_OUT, Easing.QUINT_IN_OUT, Easing.SMOOTHSTEP, Easing.SMOOTHERSTEP)) {
            assertEquals(0.5, e.apply(Math.nextDown(0.5)), 1e-15, e + " left of 1/2");
            assertEquals(0.5, e.apply(Math.nextUp(0.5)), 1e-15, e + " right of 1/2");
        }
        for (double h : new double[] {1e-4, 1e-6, 1e-8}) {
            assertEquals(3 * h * h, Easing.SMOOTHSTEP.apply(h), 3 * h * h * 1e-3, "smoothstep ~ 3h^2 near 0");
            assertEquals(10 * h * h * h, Easing.SMOOTHERSTEP.apply(h), 10 * h * h * h * 1e-3, "smootherstep ~ 10h^3 near 0");
            assertEquals(3 * h * h, 1 - Easing.SMOOTHSTEP.apply(1 - h), 3 * h * h * 1e-3 + 2e-16, "flat at 1");
        }
    }

    @Test
    @DisplayName("Composition with M2: easing a TimingSample progress keeps [0, 1] and the ends (section 6, DM3-13)")
    void composesWithM2Progress() {
        workshop.vfx.math.time.Timing timing = workshop.vfx.math.time.Timing.pingPong(40).withCycles(3);
        for (int tick = -5; tick <= 130; tick++) {
            double p = timing.sample(tick).progress();
            for (Easing e : Easing.values()) {
                double v = e.apply(p);
                assertTrue(v >= 0 && v <= 1, e + " at tick " + tick);
                if (p == 0 || p == 1) {
                    assertEquals(p, v, 0.0, e + " keeps the end at tick " + tick);
                }
            }
        }
    }
}
