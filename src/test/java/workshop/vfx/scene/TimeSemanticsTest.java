package workshop.vfx.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static workshop.vfx.scene.M5TestSupport.LIMITS;
import static workshop.vfx.scene.M5TestSupport.STYLE;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.curve.Easing;
import workshop.vfx.math.time.Playback;
import workshop.vfx.math.time.TimeDomain;
import workshop.vfx.math.time.TimePoint;
import workshop.vfx.math.time.Timing;
import workshop.vfx.math.time.TimingSample;
import workshop.vfx.math.time.TimingSample.Stage;

/** Contract section 7 (time): M2 supplies every instant-to-progress decision; M5 adds the scene window only. */
class TimeSemanticsTest {
    private static final Shape DOT = Shape.point(new Vec3(1, 0, 0));

    private static EffectScene scene(TimeDomain domain, double lifetime, Layer... layers) {
        return EffectScene.of(domain, lifetime, List.of(layers), LIMITS);
    }

    private static Layer layer(String name, Timing t, Visibility v) {
        return new Layer(name, t, v, Reveal.ALL, Motion.translate(Vec3.ZERO, new Vec3(0, 10, 0), t, Easing.LINEAR), DOT, STYLE);
    }

    private static EffectFrame at(EffectScene s, double local) {
        return s.evaluate(Pose.IDENTITY, new Playback(TimePoint.gameTick(0), local, 1, true), TimePoint.gameTick(0)); // paused: local ticks exactly
    }

    @Test
    @DisplayName("Domains: the playback anchor and the instant must both have the scene's domain (IllegalArgumentException), GAME and COSMETIC alike")
    void domains() {
        EffectScene game = scene(TimeDomain.GAME, 10, layer("a", Timing.once(10), Visibility.ACTIVE_ONLY));
        EffectScene cosmetic = scene(TimeDomain.COSMETIC, 10, layer("a", Timing.once(10), Visibility.ACTIVE_ONLY));
        Playback gamePb = Playback.start(TimePoint.gameTick(100));
        Playback cosPb = Playback.start(TimePoint.cosmeticNanos(100));
        assertEquals(Stage.ACTIVE, game.evaluate(Pose.IDENTITY, gamePb, TimePoint.gameTick(101)).stage());
        assertEquals(Stage.ACTIVE, cosmetic.evaluate(Pose.IDENTITY, cosPb, TimePoint.cosmeticNanos(100 + 50_000_000)).stage());
        assertThrows(IllegalArgumentException.class, () -> game.evaluate(Pose.IDENTITY, cosPb, TimePoint.cosmeticNanos(101)));
        assertThrows(IllegalArgumentException.class, () -> game.evaluate(Pose.IDENTITY, gamePb, TimePoint.cosmeticNanos(101)));
        assertThrows(IllegalArgumentException.class, () -> cosmetic.evaluate(Pose.IDENTITY, gamePb, TimePoint.gameTick(101)));
        assertThrows(IllegalArgumentException.class, () -> cosmetic.evaluate(Pose.IDENTITY, cosPb, TimePoint.gameTick(101)));
    }

    @Test
    @DisplayName("Scene window: local < 0 is BEFORE (empty), [0, lifetime) is ACTIVE, local >= lifetime is AFTER (empty, even with HOLD_AFTER layers); UNBOUNDED never ends; -0.0 counts as 0")
    void sceneWindow() {
        EffectScene s = scene(TimeDomain.GAME, 20, layer("a", Timing.once(5), Visibility.HOLD_AFTER));
        assertEquals(Stage.BEFORE, at(s, -1).stage());
        assertEquals(Stage.BEFORE, at(s, -Double.MIN_VALUE).stage());
        assertTrue(at(s, -1).isEmpty());
        assertEquals(Stage.ACTIVE, at(s, -0.0).stage());
        assertEquals(1, at(s, -0.0).pointCount());
        assertEquals(Stage.ACTIVE, at(s, Math.nextDown(20.0)).stage());
        assertEquals(1, at(s, Math.nextDown(20.0)).pointCount(), "held after its pass, inside the scene window");
        assertEquals(Stage.AFTER, at(s, 20).stage());
        assertTrue(at(s, 20).isEmpty());
        assertEquals(0, at(s, 1e300).pointCount());
        EffectScene forever = scene(TimeDomain.GAME, EffectScene.UNBOUNDED, layer("a", Timing.loop(5), Visibility.ACTIVE_ONLY));
        assertEquals(Stage.ACTIVE, at(forever, 1e15).stage());
        assertEquals(1, at(forever, 1e15).pointCount());
    }

    @Test
    @DisplayName("Layer passes are half-open (M2): visible at the start, still at nextDown(end), hidden at the end for ACTIVE_ONLY, held at progress 1 for HOLD_AFTER; hidden before a delay")
    void halfOpenPasses() {
        Timing t = Timing.once(16).withDelay(4);
        EffectScene s = scene(TimeDomain.GAME, 100, layer("only", t, Visibility.ACTIVE_ONLY), layer("hold", t, Visibility.HOLD_AFTER));
        assertEquals(0, at(s, 3).pointCount());
        assertEquals(0, at(s, Math.nextDown(4.0)).pointCount());
        EffectFrame start = at(s, 4);
        assertEquals(2, start.batches().size());
        assertEquals(0.0, start.batches().get(0).timing().progress());
        assertEquals(0.0, start.batches().get(0).y(0), "translate at progress 0 is exactly 'from'");
        EffectFrame last = at(s, Math.nextDown(20.0));
        assertEquals(2, last.batches().size());
        assertTrue(last.batches().get(0).timing().progress() < 1);
        EffectFrame end = at(s, 20);
        assertEquals(1, end.batches().size());
        FrameBatch held = end.batches().get(0);
        assertEquals("hold", held.layer());
        assertEquals(1, held.layerIndex());
        assertEquals(Stage.AFTER, held.timing().stage());
        assertEquals(1.0, held.timing().progress());
        assertEquals(10.0, held.y(0), "translate at progress 1 is exactly 'to'");
    }

    @Test
    @DisplayName("Ping-pong and loops: cycle and progress are M2's; a held ping-pong with an even cycle count ends at progress 0")
    void repeats() {
        Timing loop = Timing.loop(10);
        Timing pp = Timing.pingPong(10).withCycles(2);
        EffectScene s = scene(TimeDomain.GAME, 1000, layer("loop", loop, Visibility.ACTIVE_ONLY), layer("pp", pp, Visibility.HOLD_AFTER));
        EffectFrame f = at(s, 35);
        assertEquals(new TimingSample(Stage.ACTIVE, 3, 0.5), f.batches().get(0).timing());
        assertEquals(Stage.AFTER, f.batches().get(1).timing().stage());
        assertEquals(0.0, f.batches().get(1).timing().progress());
        assertEquals(0.0, f.batches().get(1).y(0), "the drive's AFTER progress 0 puts the translation at 'from'");
        EffectFrame g = at(s, 15);
        assertEquals(new TimingSample(Stage.ACTIVE, 1, 0.5), g.batches().get(1).timing());
        assertEquals(5.0, g.batches().get(1).y(0), 1e-15);
        for (double local : new double[] {0, 2.5, 9.75, 10, 17.5, 35, 999}) {
            EffectFrame h = at(s, local);
            for (FrameBatch b : h.batches()) {
                Timing timing = s.layers().get(b.layerIndex()).timing();
                assertEquals(timing.sample(local), b.timing(), "batch timing is the M2 sample at " + local);
            }
        }
    }

    @Test
    @DisplayName("Playback controls are M2's: paused frames do not change; seek moves; negative rate plays backwards; zero rate freezes; restart and resume")
    void playbackControls() {
        EffectScene s = scene(TimeDomain.GAME, 100, layer("a", Timing.once(50), Visibility.ACTIVE_ONLY));
        TimePoint t0 = TimePoint.gameTick(1000);
        Playback pb = Playback.start(t0);
        Playback paused = pb.pause(TimePoint.gameTick(1010));
        double y = s.evaluate(Pose.IDENTITY, paused, TimePoint.gameTick(1010)).batches().get(0).y(0);
        assertEquals(y, s.evaluate(Pose.IDENTITY, paused, TimePoint.gameTick(5000)).batches().get(0).y(0));
        assertEquals(10.0 / 50 * 10, y, 1e-15);
        Playback resumed = paused.resume(TimePoint.gameTick(2000));
        assertEquals(30.0, s.evaluate(Pose.IDENTITY, resumed, TimePoint.gameTick(2020)).localTicks());
        Playback seeked = pb.seek(TimePoint.gameTick(1001), 40);
        assertEquals(41.0, s.evaluate(Pose.IDENTITY, seeked, TimePoint.gameTick(1002)).localTicks());
        Playback back = Playback.start(t0, 30, -1);
        assertEquals(20.0, s.evaluate(Pose.IDENTITY, back, TimePoint.gameTick(1010)).localTicks());
        assertEquals(Stage.BEFORE, s.evaluate(Pose.IDENTITY, back, TimePoint.gameTick(1031)).stage());
        Playback frozen = Playback.start(t0, 7, 0);
        assertEquals(7.0, s.evaluate(Pose.IDENTITY, frozen, TimePoint.gameTick(1_000_000)).localTicks());
        Playback restarted = seeked.restart(TimePoint.gameTick(3000));
        assertEquals(0.0, s.evaluate(Pose.IDENTITY, restarted, TimePoint.gameTick(3000)).localTicks());
        assertThrows(IllegalArgumentException.class, () -> pb.seek(TimePoint.gameTick(999), 0), "M2 rejects control before the anchor");
    }

    @Test
    @DisplayName("Extreme instants: GAME tick differences that overflow long give ArithmeticException (M2); COSMETIC nanoseconds wrap (M2); non-finite local ticks give ArithmeticException; huge finite local ticks are evaluated")
    void extremeInstants() {
        EffectScene game = scene(TimeDomain.GAME, EffectScene.UNBOUNDED, layer("a", Timing.loop(40), Visibility.ACTIVE_ONLY));
        assertThrows(ArithmeticException.class, () -> game.evaluate(Pose.IDENTITY, Playback.start(TimePoint.gameTick(Long.MIN_VALUE)), TimePoint.gameTick(Long.MAX_VALUE)));
        assertEquals((double) Long.MAX_VALUE, game.evaluate(Pose.IDENTITY, Playback.start(TimePoint.gameTick(0)), TimePoint.gameTick(Long.MAX_VALUE)).localTicks());
        assertThrows(ArithmeticException.class, () -> game.evaluate(Pose.IDENTITY, Playback.start(TimePoint.gameTick(0), 0, Double.MAX_VALUE), TimePoint.gameTick(Long.MAX_VALUE)));
        EffectScene cos = scene(TimeDomain.COSMETIC, EffectScene.UNBOUNDED, layer("a", Timing.loop(40), Visibility.ACTIVE_ONLY));
        long nearMax = Long.MAX_VALUE - 10;
        EffectFrame wrapped = cos.evaluate(Pose.IDENTITY, Playback.start(TimePoint.cosmeticNanos(nearMax)), TimePoint.cosmeticNanos(nearMax + 250_000_000L));
        EffectFrame plain = cos.evaluate(Pose.IDENTITY, Playback.start(TimePoint.cosmeticNanos(0)), TimePoint.cosmeticNanos(250_000_000L));
        assertEquals(plain.localTicks(), wrapped.localTicks());
        assertEquals(5.0, wrapped.localTicks());
        assertEquals(plain.batches().get(0).y(0), wrapped.batches().get(0).y(0));
        EffectFrame huge = at(game, 1e300);
        assertEquals(Stage.ACTIVE, huge.stage());
        assertEquals(0, huge.pointCount(), "M2: a FOREVER loop reports AFTER once the pass index reaches Long.MAX_VALUE, so ACTIVE_ONLY hides it");
        assertEquals(1, at(game, 1e18).pointCount());
    }

    @Test
    @DisplayName("Signed zero: local ticks of -0.0 are reported as +0.0 and evaluated as the scene start")
    void signedZero() {
        EffectScene s = scene(TimeDomain.GAME, 10, layer("a", Timing.once(10), Visibility.ACTIVE_ONLY));
        EffectFrame f = at(s, -0.0);
        assertEquals(Double.doubleToRawLongBits(0.0), Double.doubleToRawLongBits(f.localTicks()));
        assertEquals(Stage.ACTIVE, f.stage());
        assertEquals(new TimingSample(Stage.ACTIVE, 0, 0.0), f.batches().get(0).timing());
        assertEquals(Double.doubleToRawLongBits(0.0), Double.doubleToRawLongBits(f.batches().get(0).timing().progress()));
    }

    @Test
    @DisplayName("The frame reports the playback's local ticks exactly (M2 Playback.localTicks)")
    void localTicksReported() {
        EffectScene s = scene(TimeDomain.COSMETIC, EffectScene.UNBOUNDED, layer("a", Timing.loop(7), Visibility.ACTIVE_ONLY));
        Playback pb = Playback.start(TimePoint.cosmeticNanos(123), 3.25, -0.75);
        for (long dn : new long[] {0, 1, 49_999_999, 50_000_000, 987_654_321, -5_000_000_000L}) {
            TimePoint now = TimePoint.cosmeticNanos(123 + dn);
            EffectFrame f = s.evaluate(Pose.IDENTITY, pb, now);
            double expected = pb.localTicks(now);
            assertEquals(expected == 0 ? 0.0 : expected, f.localTicks());
            assertEquals(expected < 0 ? Stage.BEFORE : Stage.ACTIVE, f.stage());
        }
    }
}
