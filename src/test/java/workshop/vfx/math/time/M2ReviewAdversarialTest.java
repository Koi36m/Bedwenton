package workshop.vfx.math.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static workshop.vfx.math.time.M2Support.assertBits;
import static workshop.vfx.math.time.M2Support.hex;
import static workshop.vfx.math.time.M2Support.tick;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import workshop.vfx.math.time.TimingSample.Stage;

/**
 * Independent adversarial review of M2 (Claude Code), against contract revision 1.
 *
 * <p>Every assertion follows from a contract clause, named in the display name, or from a bound proven in
 * review/m2/M2-CLAUDE-REVIEW.md. The oracles are independent of the specified operation sequence: exact rationals
 * (BigDecimal, BigInteger and the Python boundary generator, which uses no fmod and no rint), pure long arithmetic,
 * and metamorphic relations. None of the 169 delivered tests or golden files is modified.
 */
@Tag("m2-review")
class M2ReviewAdversarialTest {
    private static final long SEED = 0x2E71_E3_2026L;

    private static long bits(double x) {
        return Double.doubleToRawLongBits(x);
    }

    @Test
    @DisplayName("Environment: tests run on Java 25")
    void runsOnJava25() {
        assertEquals(25, Runtime.version().feature(), "found " + Runtime.version());
    }

    // ------------------------------------------------------------ exact-rational boundary oracle

    @Test
    @DisplayName("Boundary oracle: on and next to 11 cycle boundaries up to 2^51 - 1, for 14 durations including subnormals, stage and cycle are exact and forward progress is correctly rounded (sections 5, 6.4)")
    void boundaryOracle() throws Exception {
        byte[] csv = M2Support.resourceBytes("/m2/review/boundary_cases.csv");
        String manifest = new String(M2Support.resourceBytes("/m2/review/MANIFEST.sha256"), StandardCharsets.UTF_8).trim();
        assertEquals(manifest.split("\\s+")[0], HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(csv)), "manifest");
        List<String[]> rows = new java.util.ArrayList<>();
        for (String line : new String(csv, StandardCharsets.UTF_8).split("\n")) {
            if (!line.isBlank() && !line.startsWith("#") && !line.startsWith("duration_hex")) {
                rows.add(line.trim().split(","));
            }
        }
        assertTrue(rows.size() > 2000, "rows " + rows.size());
        MathContext mc = new MathContext(60);
        for (String[] r : rows) {
            double d = hex(r[0]);
            RepeatMode mode = RepeatMode.valueOf(r[1]);
            long cycles = Long.parseLong(r[2]);
            double local = hex(r[3]);
            TimingSample s = new Timing(0.0, d, mode, cycles).sample(local);
            String label = String.join(",", r);
            assertEquals(Stage.valueOf(r[4]), s.stage(), label);
            assertEquals(Long.parseLong(r[5]), s.cycle(), label);
            boolean backward = s.stage() == Stage.ACTIVE && mode == RepeatMode.PING_PONG && (s.cycle() & 1) == 1;
            if (backward) {
                // two roundings allowed (contract 6.4): |p - exact| <= 2^-52
                BigDecimal exact = new BigDecimal(new BigInteger(r[6])).divide(new BigDecimal(new BigInteger(r[7])), mc);
                double err = new BigDecimal(s.progress()).subtract(exact).abs().doubleValue();
                assertTrue(err <= 0x1p-52, label + " backward error " + err);
            } else {
                assertBits(hex(r[8]), s.progress(), label);
            }
        }
    }

    @Test
    @DisplayName("The 2^51 cycle-exact domain is sufficient: 200,000 random elapsed times with elapsed/duration in [2^50, 2^51) give the exact cycle (section 6.4)")
    void cycleDomainEdge() {
        SplittableRandom r = new SplittableRandom(SEED);
        for (int i = 0; i < 200_000; i++) {
            double d = r.nextDouble(0.01, 100);
            long n = r.nextLong(1L << 50, 1L << 51);
            double u = new BigDecimal(n).multiply(new BigDecimal(d)).doubleValue();
            BigInteger exactCycle = new BigDecimal(u).divide(new BigDecimal(d), 0, java.math.RoundingMode.FLOOR).toBigIntegerExact();
            if (exactCycle.compareTo(BigInteger.ONE.shiftLeft(51)) >= 0) {
                continue;
            }
            assertEquals(exactCycle.longValueExact(), Timing.loop(d).sample(u).cycle(), "u=" + u + " d=" + d);
        }
    }

    // ------------------------------------------------------------ integer gameplay pipeline (theorem 6.3)

    @Test
    @DisplayName("Exhaustive small integers: every delay in [-6, 6], duration in [1, 8], local in [-30, 90], all modes and cycle counts agree with integer arithmetic (section 6.3)")
    void exhaustiveSmallIntegers() {
        long[] counts = {1, 2, 3, 4, 5, Timing.FOREVER};
        int checked = 0;
        for (long delay = -6; delay <= 6; delay++) {
            for (long d = 1; d <= 8; d++) {
                for (RepeatMode mode : RepeatMode.values()) {
                    for (long cycles : counts) {
                        if (mode == RepeatMode.ONCE && cycles != 1) {
                            continue;
                        }
                        Timing t = new Timing(delay, d, mode, cycles);
                        for (long local = -30; local <= 90; local++) {
                            assertIntegerModel(t, delay, d, mode, cycles, local);
                            checked++;
                        }
                    }
                }
            }
        }
        assertTrue(checked > 100_000, "checked " + checked);
    }

    private static void assertIntegerModel(Timing t, long delay, long d, RepeatMode mode, long cycles, long local) {
        TimingSample s = t.sample(local);
        String label = "delay " + delay + " d " + d + " " + mode + " x" + cycles + " local " + local;
        long u = local - delay;
        if (u < 0) {
            assertEquals(new TimingSample(Stage.BEFORE, 0, 0.0), s, label);
            return;
        }
        long n = Math.floorDiv(u, d);
        long rem = Math.floorMod(u, d);
        if (n >= cycles) {
            assertEquals(Stage.AFTER, s.stage(), label);
            assertEquals(cycles - 1, s.cycle(), label);
            assertBits(mode == RepeatMode.PING_PONG && cycles % 2 == 0 ? 0.0 : 1.0, s.progress(), label);
            return;
        }
        assertEquals(Stage.ACTIVE, s.stage(), label);
        assertEquals(n, s.cycle(), label);
        long num = mode == RepeatMode.PING_PONG && (n & 1) == 1 ? d - rem : rem;
        assertBits((double) num / (double) d, s.progress(), label);
    }

    @Test
    @DisplayName("Integer control scripts: random pause/resume/restart/seek/withRate (integer rates in [-3, 3]) match a pure long-arithmetic model bit for bit, through Timing as well (section 6.3)")
    void integerControlScriptsMatchLongModel() {
        SplittableRandom r = new SplittableRandom(SEED + 1);
        for (int script = 0; script < 2_000; script++) {
            long t = r.nextLong(-(1L << 40), 1L << 40);
            long aTick = t;
            long aLocal = r.nextLong(-1000, 1000);
            long rate = r.nextLong(-3, 4);
            boolean paused = false;
            Playback p = Playback.start(tick(t), aLocal, rate);
            Timing tm = new Timing(r.nextLong(-50, 50), r.nextLong(1, 60), RepeatMode.values()[r.nextInt(3)], 1);
            if (tm.mode() != RepeatMode.ONCE) {
                tm = tm.withCycles(r.nextBoolean() ? Timing.FOREVER : r.nextLong(1, 9));
            }
            for (int step = 0; step < 60; step++) {
                t += r.nextLong(0, 400);
                long modelLocal = paused ? aLocal : aLocal + rate * (t - aTick);
                switch (r.nextInt(6)) {
                    case 0 -> { p = p.pause(tick(t)); if (!paused) { aLocal = modelLocal; aTick = t; paused = true; } }
                    case 1 -> { p = p.resume(tick(t)); if (paused) { aTick = t; paused = false; } }
                    case 2 -> { long nr = r.nextLong(-3, 4); p = p.withRate(tick(t), nr); aLocal = modelLocal; aTick = t; rate = nr; }
                    case 3 -> { long x = r.nextLong(-500, 500); p = p.seek(tick(t), x); aLocal = x; aTick = t; }
                    case 4 -> { p = p.restart(tick(t)); aLocal = 0; aTick = t; paused = false; }
                    default -> { }
                }
                long probe = t + r.nextLong(-100, 300);
                long expected = paused ? aLocal : aLocal + rate * (probe - aTick);
                double local = p.localTicks(tick(probe));
                assertBits((double) expected, local, "script " + script + " step " + step);
                assertIntegerModel(tm, (long) tm.delayTicks(), (long) tm.durationTicks(), tm.mode(), tm.cycles(), expected);
            }
        }
    }

    // ------------------------------------------------------------ metamorphic relations

    @Test
    @DisplayName("Time-translation invariance: shifting every instant by the same amount changes no bit, in both domains (sections 3, 4)")
    void timeTranslationInvariance() {
        SplittableRandom r = new SplittableRandom(SEED + 2);
        for (int i = 0; i < 20_000; i++) {
            TimeDomain dom = r.nextBoolean() ? TimeDomain.GAME : TimeDomain.COSMETIC;
            long base = r.nextLong(-(1L << 50), 1L << 50);
            long shift = r.nextLong(-(1L << 60), 1L << 60);
            long d1 = r.nextLong(0, 1_000_000_000L);
            long d2 = d1 + r.nextLong(0, 1_000_000_000L);
            long d3 = d2 + r.nextLong(-1_000_000, 1_000_000_000L);
            double off = r.nextDouble(-1e4, 1e4);
            double rate = r.nextDouble(-5, 5);
            double rate2 = r.nextDouble(-5, 5);
            Playback a = Playback.start(M2Support.at(dom, base), off, rate).pause(M2Support.at(dom, base + d1))
                    .withRate(M2Support.at(dom, base + d1), rate2).resume(M2Support.at(dom, base + d2));
            Playback b = Playback.start(M2Support.at(dom, base + shift), off, rate).pause(M2Support.at(dom, base + shift + d1))
                    .withRate(M2Support.at(dom, base + shift + d1), rate2).resume(M2Support.at(dom, base + shift + d2));
            assertEquals(bits(a.localTicks(M2Support.at(dom, base + d3))), bits(b.localTicks(M2Support.at(dom, base + shift + d3))), "shift " + shift);
        }
    }

    @Test
    @DisplayName("PING_PONG mirror symmetry: for integer d and u in [0, 2d], progress(u) == progress(2d - u) bit for bit (section 5)")
    void pingPongMirror() {
        for (long d = 1; d <= 300; d++) {
            Timing t = Timing.pingPong(d);
            for (long u = 0; u <= 2 * d; u++) {
                assertBits(t.sample(u).progress(), t.sample(2 * d - u).progress(), "d " + d + " u " + u);
            }
        }
    }

    @Test
    @DisplayName("Stage and cycle are monotone in local time for finite runs: never AFTER -> ACTIVE or a lower cycle later (section 5)")
    void stageMonotone() {
        SplittableRandom r = new SplittableRandom(SEED + 3);
        for (int k = 0; k < 300; k++) {
            double d = r.nextDouble(1e-3, 1e3);
            Timing t = new Timing(r.nextDouble(-1e3, 1e3), d, r.nextBoolean() ? RepeatMode.LOOP : RepeatMode.PING_PONG, r.nextLong(1, 20));
            double x = t.delayTicks() - 2 * d;
            TimingSample prev = t.sample(x);
            for (int i = 0; i < 3_000; i++) {
                x += r.nextDouble(0, d / 50);
                TimingSample s = t.sample(x);
                assertTrue(s.stage().compareTo(prev.stage()) >= 0, "stage went back at " + x);
                assertTrue(s.cycle() >= prev.cycle() || s.stage() != prev.stage(), "cycle went back at " + x);
                prev = s;
            }
            assertEquals(Stage.AFTER, prev.stage(), "every finite run ends");
        }
    }

    // ------------------------------------------------------------ validation order and edge inputs

    @Test
    @DisplayName("Validation order: control-instant checks, then argument checks, then evaluation; so an invalid rate is an IAE even where evaluation would overflow (section 4)")
    void validationOrder() {
        Playback fast = Playback.start(tick(0), 0.0, 1e300);
        assertThrows(IllegalArgumentException.class, () -> fast.withRate(tick(10_000_000_000L), Double.NaN), "rate before L(at)");
        assertThrows(IllegalArgumentException.class, () -> fast.withRate(tick(-1), Double.NaN), "instant first");
        assertThrows(IllegalArgumentException.class, () -> fast.seek(tick(-1), Double.NaN));
        Playback paused = Playback.start(tick(10)).pause(tick(20));
        assertThrows(IllegalArgumentException.class, () -> paused.pause(TimePoint.cosmeticNanos(30)), "domain checked on the idempotent path");
        assertThrows(IllegalArgumentException.class, () -> paused.localTicks(null));
        Playback overflowing = Playback.start(tick(Long.MIN_VALUE));
        assertThrows(ArithmeticException.class, () -> overflowing.pause(tick(Long.MAX_VALUE)), "pause evaluates L(at)");
        assertBits(0.0, overflowing.restart(tick(Long.MAX_VALUE)).anchorTicks(), "restart does not evaluate");
        assertBits(5.0, overflowing.seek(tick(Long.MAX_VALUE), 5.0).anchorTicks(), "seek does not evaluate");
    }

    @Test
    @DisplayName("Subnormal and extreme durations: exact cycles for small multiples of Double.MIN_VALUE; huge durations stay in cycle 0 (section 5)")
    void subnormalAndHugeDurations() {
        double m = Double.MIN_VALUE;
        assertEquals(new TimingSample(Stage.ACTIVE, 0, 0.0), Timing.once(m).sample(0));
        assertEquals(new TimingSample(Stage.AFTER, 0, 1.0), Timing.once(m).sample(m));
        assertEquals(new TimingSample(Stage.ACTIVE, 3, 0.0), Timing.loop(m).sample(3 * m));
        assertEquals(new TimingSample(Stage.ACTIVE, 7, 1.0), Timing.pingPong(m).sample(7 * m), "backward pass starts at 1");
        assertEquals(new TimingSample(Stage.ACTIVE, 0, 0.5), Timing.loop(Double.MAX_VALUE).sample(Double.MAX_VALUE / 2));
        assertEquals(Stage.AFTER, Timing.once(Double.MAX_VALUE).sample(Double.MAX_VALUE).stage());
    }

    @Test
    @DisplayName("COSMETIC differences beyond 2^53 ns round twice, as specified, and stay within 2 ulp of the exact quotient (section 3)")
    void cosmeticBeyond2p53() {
        SplittableRandom r = new SplittableRandom(SEED + 4);
        MathContext mc = new MathContext(60);
        for (int i = 0; i < 50_000; i++) {
            long delta = r.nextLong(1L << 53, Long.MAX_VALUE);
            double got = TimePoint.cosmeticNanos(delta).ticksSince(TimePoint.cosmeticNanos(0));
            assertBits((double) delta / 50_000_000.0, got, "specified operation order");
            double exact = new BigDecimal(delta).divide(BigDecimal.valueOf(50_000_000L), mc).doubleValue();
            assertTrue(Math.abs(got - exact) <= 2 * Math.ulp(exact), "delta " + delta);
        }
    }

    // ------------------------------------------------------------ error bounds (finding A1)

    @Test
    @DisplayName("Re-anchoring error: GAME stays within the contract's 1.5 * 2^-52 * M; COSMETIC needs 2.5 * 2^-52 * M (finding A1: the contract's 1.5 is exceeded)")
    void reanchoringErrorBounds() {
        // counterexample to the contract's COSMETIC bound (found by random search, review section 4)
        double a = -0x1.f77c448775a2dp18;
        double rate = 0x1.16f13d3b52584p-1;
        long delta = 113_443_124_582_639L;
        Playback p = Playback.start(TimePoint.cosmeticNanos(0), a, rate).withRate(TimePoint.cosmeticNanos(delta), 1.0);
        BigDecimal exact = new BigDecimal(a).add(new BigDecimal(rate).multiply(new BigDecimal(delta)).divide(BigDecimal.valueOf(50_000_000L)));
        double m = Math.max(Math.abs(a), Math.abs(p.anchorTicks()));
        double err = new BigDecimal(p.anchorTicks()).subtract(exact).abs().doubleValue();
        assertTrue(err > 1.5 * 0x1p-52 * m, "the contract's 1.5 bound is exceeded: " + err / (0x1p-52 * m));
        assertTrue(err <= 2.5 * 0x1p-52 * m, "the corrected 2.5 bound holds");
        SplittableRandom r = new SplittableRandom(SEED + 5);
        // GAME: exact delta, then fl(rate * delta) and fl(anchor + product): 3 * 2^-53 * M = 1.5 * 2^-52 * M.
        // COSMETIC, delta <= 2^53 ns: one more rounding in delta / 5e7: 2.5 * 2^-52 * M.
        // COSMETIC, delta > 2^53 ns: (double) delta rounds too: 3.5 * 2^-52 * M.
        for (int i = 0; i < 150_000; i++) {
            int kind = r.nextInt(3);
            double a0 = r.nextDouble(-1, 1) * Math.pow(10, r.nextInt(0, 7));
            double k = r.nextDouble(-3, 3);
            long dt = switch (kind) {
                case 0 -> r.nextLong(1, 10_000_000L);
                case 1 -> r.nextLong(1, 1L << 53);
                default -> r.nextLong(1L << 53, 1L << 62);
            };
            TimePoint s = kind == 0 ? tick(0) : TimePoint.cosmeticNanos(0);
            TimePoint e = kind == 0 ? tick(dt) : TimePoint.cosmeticNanos(dt);
            double got = Playback.start(s, a0, k).withRate(e, 1.0).anchorTicks();
            BigDecimal dticks = kind == 0 ? new BigDecimal(dt) : new BigDecimal(dt).divide(BigDecimal.valueOf(50_000_000L));
            BigDecimal ex = new BigDecimal(a0).add(new BigDecimal(k).multiply(dticks));
            double mm = Math.max(Math.abs(a0), Math.abs(got));
            double er = new BigDecimal(got).subtract(ex).abs().doubleValue();
            double factor = kind == 0 ? 1.5 : kind == 1 ? 2.5 : 3.5;
            if (er > factor * 0x1p-52 * mm) {
                fail("kind " + kind + " re-anchoring error " + er / (0x1p-52 * mm) + " * 2^-52 M > " + factor);
            }
        }
    }

    // ------------------------------------------------------------ determinism under JIT compilation

    @Test
    @DisplayName("Determinism under JIT: results computed before and after 3 million hot iterations are bit-identical (section 6.1)")
    void jitStability() {
        SplittableRandom r = new SplittableRandom(SEED + 6);
        int n = 2_000;
        Playback[] ps = new Playback[n];
        TimePoint[] ts = new TimePoint[n];
        Timing[] tms = new Timing[n];
        long[] before = new long[n];
        for (int i = 0; i < n; i++) {
            ps[i] = Playback.start(TimePoint.cosmeticNanos(r.nextLong(-(1L << 40), 1L << 40)), r.nextDouble(-1e3, 1e3), r.nextDouble(-3, 3));
            ts[i] = TimePoint.cosmeticNanos(ps[i].anchor().value() + r.nextLong(0, 1L << 40));
            tms[i] = new Timing(r.nextDouble(-100, 100), r.nextDouble(0.1, 100), RepeatMode.PING_PONG, Timing.FOREVER);
        }
        for (int i = 0; i < n; i++) {
            before[i] = bits(tms[i].sample(ps[i].localTicks(ts[i])).progress()) ^ bits(ps[i].localTicks(ts[i]));
        }
        long sink = 0;
        for (int rep = 0; rep < 1_500; rep++) {
            for (int i = 0; i < n; i++) {
                sink += bits(tms[i].sample(ps[i].localTicks(ts[i])).progress());
            }
        }
        for (int i = 0; i < n; i++) {
            assertEquals(before[i], bits(tms[i].sample(ps[i].localTicks(ts[i])).progress()) ^ bits(ps[i].localTicks(ts[i])), "case " + i);
        }
        assertTrue(sink != 1, "keep the loop alive");
    }
}
