package workshop.vfx.scene;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static workshop.vfx.scene.M5TestSupport.LIMITS;
import static workshop.vfx.scene.M5TestSupport.STYLE;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Quat;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.curve.Easing;
import workshop.vfx.math.curve.Spiral;
import workshop.vfx.math.sample.Endpoints;
import workshop.vfx.math.sample.Sampler;
import workshop.vfx.math.time.Playback;
import workshop.vfx.math.time.TimeDomain;
import workshop.vfx.math.time.TimePoint;
import workshop.vfx.math.time.Timing;
import workshop.vfx.math.time.TimingSample.Stage;

/** Adversarial cases required by the kickoff (section "Required acceptance evidence", item 4). */
class M5AdversarialTest {
    private static EffectFrame at(EffectScene s, Pose anchor, double local) {
        return s.evaluate(anchor, new Playback(new TimePoint(s.domain(), 0), local, 1, true), new TimePoint(s.domain(), 0));
    }

    @Test
    @DisplayName("Overlapping animations: 200 layers with the same timing, geometry and style but different motions are all emitted, in insertion order, each with its own pose")
    void overlapping() {
        List<Layer> layers = new ArrayList<>();
        Timing t = Timing.once(20);
        Shape ring = M5TestSupport.circle(1, 8);
        for (int i = 0; i < 200; i++) {
            layers.add(new Layer("o" + i, t, Visibility.ACTIVE_ONLY, Reveal.ALL, Motion.spin(Vec3.Y, 1, i / 200.0, t, Easing.LINEAR), ring, STYLE));
        }
        EffectScene s = EffectScene.of(TimeDomain.GAME, 20, layers, LIMITS);
        EffectFrame f = at(s, Pose.IDENTITY, 10);
        assertEquals(200, f.batches().size());
        for (int i = 0; i < 200; i++) {
            FrameBatch b = f.batches().get(i);
            assertEquals(i, b.layerIndex());
            double[] expected = M5TestSupport.oracle(Pose.IDENTITY, List.of(new M5TestSupport.SpinSpec(Vec3.Y, 1, i / 200.0, t, Easing.LINEAR)), 10, ring.point(3));
            assertEquals(expected[0], b.x(3), 1e-14);
            assertEquals(expected[2], b.z(3), 1e-14);
        }
    }

    @Test
    @DisplayName("Stateless frames (cancellation is the caller's): evaluation order does not matter; a stopped effect is one the caller no longer evaluates; BEFORE, ACTIVE-but-empty and AFTER (ended) are distinct")
    void statelessFrames() {
        EffectScene s = M5TestSupport.healing();
        Pose anchor = new Pose(new Vec3(3, 4, 5), new Quat(1, 0.2, 0, 0), 1);
        long[] at30 = bits(at(s, anchor, 30));
        at(s, anchor, 59);
        at(s, anchor, 5);
        at(s, anchor, 1e9);
        assertArrayEquals(at30, bits(at(s, anchor, 30)), "no hidden state between evaluations");
        assertEquals(Stage.BEFORE, at(s, anchor, -0.5).stage());
        EffectFrame gap = at(s, anchor, 17);
        assertEquals(Stage.ACTIVE, gap.stage(), "between pulses the scene is running but emits nothing");
        assertTrue(gap.isEmpty());
        EffectFrame ended = at(s, anchor, 60);
        assertEquals(Stage.AFTER, ended.stage(), "natural completion");
        assertTrue(ended.isEmpty());
        // pause then resume continues where it stopped (M2 Playback), with no M5 state involved
        Playback pb = Playback.start(TimePoint.gameTick(100));
        Playback paused = pb.pause(TimePoint.gameTick(110));
        Playback resumed = paused.resume(TimePoint.gameTick(500));
        assertArrayEquals(bits(s.evaluate(anchor, pb, TimePoint.gameTick(115))), bits(s.evaluate(anchor, resumed, TimePoint.gameTick(505))));
    }

    private static long[] bits(EffectFrame f) {
        List<Long> out = new ArrayList<>();
        out.add((long) f.stage().ordinal());
        for (FrameBatch b : f.batches()) {
            out.add((long) b.layerIndex());
            for (double c : b.coordinates()) {
                out.add(Double.doubleToRawLongBits(c));
            }
        }
        return out.stream().mapToLong(Long::longValue).toArray();
    }

    @Test
    @DisplayName("0 and 1 samples: shapes from M4 sample sets of 0 and 1 points; a 0-point layer is never emitted; a 1-point layer is a single exact placement")
    void zeroAndOneSamples() {
        Shape none = Shape.of(Sampler.uniformParameter(Spiral.circle(1), 0, Endpoints.INCLUDE_END, M5TestSupport.SAMPLE_LIMITS));
        Shape one = Shape.of(Sampler.uniformParameter(Spiral.circle(1), 1, Endpoints.INCLUDE_END, M5TestSupport.SAMPLE_LIMITS));
        assertEquals(0, none.size());
        assertEquals(1, one.size());
        assertEquals(new Vec3(1, 0, -0.0), one.point(0));
        EffectScene s = EffectScene.of(TimeDomain.GAME, 10, List.of(
            new Layer("none", Timing.once(10), Visibility.HOLD_AFTER, Reveal.ALL, Motion.IDENTITY, none, STYLE),
            new Layer("one", Timing.once(10), Visibility.ACTIVE_ONLY, Reveal.ALL, Motion.IDENTITY, one, STYLE)), LIMITS);
        EffectFrame f = at(s, Pose.ofTranslation(new Vec3(10, 20, 30)), 1);
        assertEquals(1, f.batches().size());
        assertEquals(new Vec3(11, 20, 30), f.batches().get(0).point(0));
        assertEquals(1, s.maxPointsPerFrame());
    }

    @Test
    @DisplayName("Extreme magnitudes: anchors at |x| = 3e7 (world border) and 1e15, scales 1e-9 and 1e9, subnormal and huge local offsets stay finite and match the oracle within the tolerance")
    void extremeMagnitudes() {
        Timing t = Timing.loop(40);
        List<M5TestSupport.Spec> specs = List.of(new M5TestSupport.SpinSpec(new Vec3(0.3, 1, -0.2), 3, 0.1, t, Easing.LINEAR), new M5TestSupport.ScaleSpec(1e-9, 1e9, t, Easing.QUAD_IN));
        Motion m = M5TestSupport.motionOf(specs);
        for (Vec3 tr : new Vec3[] {new Vec3(3e7, 320, -3e7), new Vec3(1e15, -1e15, 1e15), Vec3.ZERO}) {
            for (Vec3 local : new Vec3[] {new Vec3(Double.MIN_VALUE, 0, 0), new Vec3(1e-300, 1e-300, 0), new Vec3(1, 2, 3), new Vec3(1e100, 0, 0)}) {
                for (double time : new double[] {0, 1, 13.7, 39.99}) {
                    Pose anchor = new Pose(tr, new Quat(0.2, 0.3, 0.4, 0.5), 0.75);
                    EffectScene s = EffectScene.of(TimeDomain.GAME, EffectScene.UNBOUNDED, List.of(new Layer("x", t, Visibility.ACTIVE_ONLY, Reveal.ALL, m, Shape.point(local), STYLE)), LIMITS);
                    FrameBatch b = at(s, anchor, time).batches().get(0);
                    double[] e = M5TestSupport.oracle(anchor, specs, time, local);
                    double tol = M5TestSupport.tolerance(M5TestSupport.magnitude(e[0], e[1], e[2], tr.x(), tr.y(), tr.z()), 3);
                    assertEquals(e[0], b.x(0), tol);
                    assertEquals(e[1], b.y(0), tol);
                    assertEquals(e[2], b.z(0), tol);
                }
            }
        }
    }

    @Test
    @DisplayName("Inactive layers never leak: across 2,000 instants of the four example scenes, a batch exists only for ACTIVE layers or AFTER layers with HOLD_AFTER, and only inside the scene window")
    void inactiveLayers() {
        EffectScene[] scenes = {M5TestSupport.healing(), M5TestSupport.orbit(), M5TestSupport.trident(), M5TestSupport.constellation()};
        for (EffectScene s : scenes) {
            for (int i = -50; i < 450; i++) {
                double local = i * 0.3;
                EffectFrame f = at(s, Pose.IDENTITY, local);
                boolean window = local >= 0 && local < s.lifetimeTicks();
                assertEquals(window ? Stage.ACTIVE : (local < 0 ? Stage.BEFORE : Stage.AFTER), f.stage());
                for (FrameBatch b : f.batches()) {
                    assertTrue(window);
                    Layer l = s.layers().get(b.layerIndex());
                    Stage st = l.timing().sample(local == 0 ? 0.0 : local).stage();
                    assertTrue(st == Stage.ACTIVE || (st == Stage.AFTER && l.visibility() == Visibility.HOLD_AFTER), l.name() + " at " + local);
                }
            }
        }
    }

    @Test
    @DisplayName("Signed zeros in inputs do not change positions: -0.0 anchor and point coordinates, -0.0 phase, give the same values (compared with ==) as +0.0")
    void signedZeroInputs() {
        Timing t = Timing.once(4);
        EffectScene pos = EffectScene.of(TimeDomain.GAME, 4, List.of(new Layer("p", t, Visibility.ACTIVE_ONLY, Reveal.ALL, Motion.spin(Vec3.Y, 1, 0.0, t, Easing.LINEAR), Shape.point(new Vec3(1, 0.0, 0.0)), STYLE)), LIMITS);
        EffectScene neg = EffectScene.of(TimeDomain.GAME, 4, List.of(new Layer("p", t, Visibility.ACTIVE_ONLY, Reveal.ALL, Motion.spin(Vec3.Y, 1, -0.0, t, Easing.LINEAR), Shape.point(new Vec3(1, -0.0, -0.0)), STYLE)), LIMITS);
        for (double time : new double[] {0, 1, 2, 3}) {
            FrameBatch a = at(pos, Pose.ofTranslation(new Vec3(0.0, 0.0, 0.0)), time).batches().get(0);
            FrameBatch b = at(neg, Pose.ofTranslation(new Vec3(-0.0, -0.0, -0.0)), time).batches().get(0);
            assertTrue(a.x(0) == b.x(0) && a.y(0) == b.y(0) && a.z(0) == b.z(0), "at " + time);
        }
    }
}
