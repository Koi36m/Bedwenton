package workshop.vfx.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Quat;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.curve.Curve;
import workshop.vfx.math.curve.Easing;
import workshop.vfx.math.curve.Spiral;
import workshop.vfx.math.sample.Endpoints;
import workshop.vfx.math.sample.Sampler;
import workshop.vfx.math.time.Playback;
import workshop.vfx.math.time.TimeDomain;
import workshop.vfx.math.time.TimePoint;
import workshop.vfx.math.time.Timing;

/** Contract section 9 (limits and work): preflight at construction, exact counts, no curve work in evaluate. */
class BudgetTest {
    private static Layer layer(String name, Shape s) {
        return new Layer(name, Timing.loop(10), Visibility.ACTIVE_ONLY, Reveal.ALL, Motion.IDENTITY, s, M5TestSupport.STYLE);
    }

    private static EffectFrame at(EffectScene s, double local) {
        return s.evaluate(Pose.IDENTITY, new Playback(TimePoint.gameTick(0), local, 1, true), TimePoint.gameTick(0));
    }

    @Test
    @DisplayName("Preflight: the scene is rejected at construction when the sum of shape sizes exceeds maxPointsPerFrame (sum in long, no overflow); accepted at exactly the limit; 4096 layers of 256 points fill 2^20")
    void preflight() {
        Shape s256 = Shape.points(Collections.nCopies(256, new Vec3(1, 2, 3)));
        Shape s257 = Shape.points(Collections.nCopies(257, new Vec3(1, 2, 3)));
        List<Layer> full = new ArrayList<>(), over = new ArrayList<>();
        for (int i = 0; i < 4096; i++) {
            full.add(layer("l" + i, s256));
            over.add(layer("l" + i, s257));
        }
        SceneLimits max = new SceneLimits(SceneLimits.MAX_LAYERS_CEILING, SceneLimits.MAX_POINTS_CEILING);
        EffectScene scene = EffectScene.of(TimeDomain.GAME, 100, full, max);
        assertEquals(1 << 20, scene.maxPointsPerFrame());
        assertEquals(1 << 20, at(scene, 1).pointCount());
        assertThrows(IllegalArgumentException.class, () -> EffectScene.of(TimeDomain.GAME, 100, over, max));
        List<Layer> threeBig = List.of(layer("a", Shape.points(Collections.nCopies(Shape.MAX_POINTS, Vec3.ZERO))), layer("b", s256), layer("c", s256));
        assertThrows(IllegalArgumentException.class, () -> EffectScene.of(TimeDomain.GAME, 100, threeBig, max));
        assertEquals(10, EffectScene.of(TimeDomain.GAME, 1, List.of(layer("a", Shape.points(Collections.nCopies(10, Vec3.ZERO)))), new SceneLimits(1, 10)).maxPointsPerFrame());
        assertThrows(IllegalArgumentException.class, () -> EffectScene.of(TimeDomain.GAME, 1, List.of(layer("a", Shape.points(Collections.nCopies(11, Vec3.ZERO)))), new SceneLimits(1, 10)));
    }

    @Test
    @DisplayName("Largest frame: one layer of 2^20 points on a moving, rotated, scaled anchor evaluates with every coordinate finite and in shape order")
    void largestFrame() {
        Shape ring = Shape.of(Sampler.uniformParameter(Spiral.circle(3), Shape.MAX_POINTS, Endpoints.EXCLUDE_END, M5TestSupport.SAMPLE_LIMITS));
        EffectScene s = EffectScene.of(TimeDomain.GAME, 100, List.of(new Layer("ring", Timing.once(100), Visibility.ACTIVE_ONLY, Reveal.ALL,
            Motion.spin(Vec3.Y, 3, 0, Timing.once(100), Easing.LINEAR), ring, M5TestSupport.STYLE)), new SceneLimits(1, SceneLimits.MAX_POINTS_CEILING));
        EffectFrame f = s.evaluate(new Pose(new Vec3(1e4, 70, -1e4), new Quat(0.6, 0.0, 0.8, 0.0), 2), new Playback(TimePoint.gameTick(0), 33.3, 1, true), TimePoint.gameTick(0));
        FrameBatch b = f.batches().get(0);
        assertEquals(Shape.MAX_POINTS, b.size());
        double[] c = b.coordinates();
        for (double v : c) {
            assertTrue(Double.isFinite(v));
        }
        assertEquals(b.pose().toWorld(ring.point(123_457)), b.point(123_457));
    }

    @Test
    @DisplayName("No curve work: evaluate never calls a Curve (shapes are copied when built); 1,000 frames of a scene built from a counting curve add 0 calls")
    void noCurveWork() {
        int[] calls = new int[1];
        Curve counting = new Curve() {
            private final Curve inner = Spiral.helix(1, 2, 3);
            @Override public Vec3 point(double t) { calls[0]++; return inner.point(t); }
            @Override public Vec3 derivative(double t) { calls[0]++; return inner.derivative(t); }
            @Override public Vec3 secondDerivative(double t) { calls[0]++; return inner.secondDerivative(t); }
        };
        Shape helix = Shape.of(Sampler.uniformParameter(counting, 64, Endpoints.INCLUDE_END, M5TestSupport.SAMPLE_LIMITS));
        int afterBuild = calls[0];
        assertEquals(64, afterBuild);
        EffectScene s = EffectScene.of(TimeDomain.GAME, EffectScene.UNBOUNDED, List.of(layer("h", helix)), M5TestSupport.LIMITS);
        for (int i = 0; i < 1_000; i++) {
            at(s, i * 0.37);
        }
        assertEquals(afterBuild, calls[0]);
    }

    @Test
    @DisplayName("Motion depth: at most 16 steps per motion (Motion.MAX_STEPS), so a layer costs at most 16 drive samples and 17 pose compositions")
    void depth() {
        Motion m = Motion.IDENTITY;
        for (int i = 0; i < 8; i++) {
            m = m.then(Motion.spin(Vec3.Y, 0.125, 0, Timing.loop(8), Easing.LINEAR)).then(Motion.fixed(Pose.ofTranslation(new Vec3(0.5, 0, 0))));
        }
        assertEquals(Motion.MAX_STEPS, m.steps());
        Motion deepest = m;
        assertThrows(IllegalArgumentException.class, () -> deepest.then(Motion.fixed(Pose.IDENTITY)));
        EffectScene s = EffectScene.of(TimeDomain.GAME, 100, List.of(new Layer("deep", Timing.once(100), Visibility.ACTIVE_ONLY, Reveal.ALL, deepest, Shape.point(Vec3.ZERO), M5TestSupport.STYLE)), M5TestSupport.LIMITS);
        assertTrue(at(s, 3).batches().get(0).point(0).isFinite());
    }
}
