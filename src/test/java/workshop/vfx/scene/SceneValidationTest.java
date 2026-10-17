package workshop.vfx.scene;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static workshop.vfx.scene.M5TestSupport.LIMITS;
import static workshop.vfx.scene.M5TestSupport.STYLE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

/** Contract sections 3 to 6: construction-time validation; every invalid input gives IllegalArgumentException. */
class SceneValidationTest {
    private static Layer layer(String name, Shape shape) {
        return new Layer(name, Timing.once(10), Visibility.ACTIVE_ONLY, Reveal.ALL, Motion.IDENTITY, shape, STYLE);
    }

    @Test
    @DisplayName("StyleKey: namespace [a-z0-9_.-]{1,32}, one colon, path [a-z0-9_./-]{1,64}; anything else (null, empty parts, upper case, spaces, two colons, slash in the namespace, overlong parts) is rejected")
    void styleKeys() {
        for (String ok : new String[] {"a:b", "bedwenton:heal_ring", "astral:link/thin", "x.y-z_0:a.b-c_d/e", "a".repeat(32) + ":" + "b".repeat(64)}) {
            assertEquals(ok, StyleKey.of(ok).value());
            assertEquals(ok, new StyleKey(ok).toString());
        }
        for (String bad : new String[] {null, "", ":", "a:", ":b", "ab", "A:b", "a:B", "a :b", "a:b:c", "a/b:c", "a".repeat(33) + ":b", "a:" + "b".repeat(65), "a:b\n", "é:b"}) {
            assertThrows(IllegalArgumentException.class, () -> StyleKey.of(bad), String.valueOf(bad));
        }
    }

    @Test
    @DisplayName("Layer: name [a-z0-9_.-]{1,64}; every component nonnull")
    void layers() {
        Shape s = Shape.point(Vec3.ZERO);
        assertDoesNotThrow(() -> layer("a", s));
        assertDoesNotThrow(() -> layer("x".repeat(64), s));
        for (String bad : new String[] {null, "", "A", "a b", "a:b", "a/b", "x".repeat(65)}) {
            assertThrows(IllegalArgumentException.class, () -> layer(bad, s), String.valueOf(bad));
        }
        Timing t = Timing.once(1);
        assertThrows(IllegalArgumentException.class, () -> new Layer("a", null, Visibility.ACTIVE_ONLY, Reveal.ALL, Motion.IDENTITY, s, STYLE));
        assertThrows(IllegalArgumentException.class, () -> new Layer("a", t, null, Reveal.ALL, Motion.IDENTITY, s, STYLE));
        assertThrows(IllegalArgumentException.class, () -> new Layer("a", t, Visibility.ACTIVE_ONLY, null, Motion.IDENTITY, s, STYLE));
        assertThrows(IllegalArgumentException.class, () -> new Layer("a", t, Visibility.ACTIVE_ONLY, Reveal.ALL, null, s, STYLE));
        assertThrows(IllegalArgumentException.class, () -> new Layer("a", t, Visibility.ACTIVE_ONLY, Reveal.ALL, Motion.IDENTITY, null, STYLE));
        assertThrows(IllegalArgumentException.class, () -> new Layer("a", t, Visibility.ACTIVE_ONLY, Reveal.ALL, Motion.IDENTITY, s, null));
    }

    @Test
    @DisplayName("Shape: finite nonnull points, at most 2^20; 0 points allowed; the input list is copied")
    void shapes() {
        assertEquals(0, Shape.points(List.of()).size());
        assertEquals(1, Shape.point(new Vec3(1, 2, 3)).size());
        assertThrows(IllegalArgumentException.class, () -> Shape.point(null));
        assertThrows(IllegalArgumentException.class, () -> Shape.points(null));
        assertThrows(IllegalArgumentException.class, () -> Shape.of(null));
        assertThrows(IllegalArgumentException.class, () -> Shape.points(Arrays.asList(Vec3.ZERO, null)));
        for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> Shape.point(new Vec3(0, bad, 0)));
        }
        assertThrows(IllegalArgumentException.class, () -> Shape.points(Collections.nCopies(Shape.MAX_POINTS + 1, Vec3.ZERO)));
        assertEquals(Shape.MAX_POINTS, Shape.points(Collections.nCopies(Shape.MAX_POINTS, Vec3.ZERO)).size());
        List<Vec3> source = new ArrayList<>(List.of(new Vec3(1, 2, 3)));
        Shape shape = Shape.points(source);
        source.set(0, new Vec3(9, 9, 9));
        assertEquals(new Vec3(1, 2, 3), shape.point(0));
        assertThrows(IndexOutOfBoundsException.class, () -> shape.point(1));
        assertThrows(IndexOutOfBoundsException.class, () -> shape.point(-1));
    }

    @Test
    @DisplayName("Motion: spin axis finite with largest component >= 1e-12, |turns| <= 2^20, finite phase; scales in [1e-9, 1e9]; finite translations; nonnull drives and easings; at most 16 steps")
    void motions() {
        Timing t = Timing.once(10);
        assertEquals(0, Motion.IDENTITY.steps());
        assertEquals(Pose.IDENTITY, Motion.IDENTITY.poseAt(5));
        for (Vec3 bad : new Vec3[] {null, Vec3.ZERO, new Vec3(1e-13, 0, 0), new Vec3(Double.NaN, 1, 0), new Vec3(0, Double.POSITIVE_INFINITY, 0)}) {
            assertThrows(IllegalArgumentException.class, () -> Motion.spin(bad, 1, 0, t, Easing.LINEAR), String.valueOf(bad));
        }
        assertDoesNotThrow(() -> Motion.spin(new Vec3(1e-12, 0, 0), Motion.MAX_TURNS, -1e300, t, Easing.LINEAR));
        for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Math.nextUp(Motion.MAX_TURNS), -Math.nextUp(Motion.MAX_TURNS)}) {
            assertThrows(IllegalArgumentException.class, () -> Motion.spin(Vec3.Y, bad, 0, t, Easing.LINEAR));
        }
        assertThrows(IllegalArgumentException.class, () -> Motion.spin(Vec3.Y, 1, Double.NaN, t, Easing.LINEAR));
        assertThrows(IllegalArgumentException.class, () -> Motion.spin(Vec3.Y, 1, 0, null, Easing.LINEAR));
        assertThrows(IllegalArgumentException.class, () -> Motion.spin(Vec3.Y, 1, 0, t, null));
        assertDoesNotThrow(() -> Motion.scale(Motion.MIN_SCALE, Motion.MAX_SCALE, t, Easing.LINEAR));
        for (double bad : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY, Math.nextDown(Motion.MIN_SCALE), Math.nextUp(Motion.MAX_SCALE)}) {
            assertThrows(IllegalArgumentException.class, () -> Motion.scale(bad, 1, t, Easing.LINEAR));
            assertThrows(IllegalArgumentException.class, () -> Motion.scale(1, bad, t, Easing.LINEAR));
        }
        assertThrows(IllegalArgumentException.class, () -> Motion.translate(null, Vec3.ZERO, t, Easing.LINEAR));
        assertThrows(IllegalArgumentException.class, () -> Motion.translate(Vec3.ZERO, new Vec3(Double.NaN, 0, 0), t, Easing.LINEAR));
        assertThrows(IllegalArgumentException.class, () -> Motion.fixed(null));
        assertThrows(IllegalArgumentException.class, () -> Motion.IDENTITY.then(null));
        Motion m = Motion.IDENTITY;
        for (int i = 0; i < Motion.MAX_STEPS; i++) {
            m = m.then(Motion.fixed(Pose.IDENTITY));
        }
        assertEquals(16, m.steps());
        Motion full = m;
        assertThrows(IllegalArgumentException.class, () -> full.then(Motion.fixed(Pose.IDENTITY)));
        assertEquals(16, full.then(Motion.IDENTITY).steps());
        assertThrows(IllegalArgumentException.class, () -> Motion.IDENTITY.poseAt(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Motion.IDENTITY.poseAt(Double.NEGATIVE_INFINITY));
    }

    @Test
    @DisplayName("SceneLimits: 1 <= maxLayers <= 4096 and 1 <= maxPointsPerFrame <= 2^20")
    void limits() {
        assertDoesNotThrow(() -> new SceneLimits(1, 1));
        assertDoesNotThrow(() -> new SceneLimits(SceneLimits.MAX_LAYERS_CEILING, SceneLimits.MAX_POINTS_CEILING));
        assertThrows(IllegalArgumentException.class, () -> new SceneLimits(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new SceneLimits(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new SceneLimits(4097, 1));
        assertThrows(IllegalArgumentException.class, () -> new SceneLimits(1, (1 << 20) + 1));
        assertThrows(IllegalArgumentException.class, () -> new SceneLimits(-1, -1));
    }

    @Test
    @DisplayName("EffectScene.of: nonnull domain, limits and layers; lifetime > 0 (finite or UNBOUNDED); unique layer names; layer and point counts within the limits; empty scenes allowed; the list is copied and unmodifiable")
    void scenes() {
        Shape one = Shape.point(Vec3.ZERO);
        List<Layer> two = List.of(layer("a", one), layer("b", one));
        assertDoesNotThrow(() -> EffectScene.of(TimeDomain.GAME, 1, List.of(), LIMITS));
        assertDoesNotThrow(() -> EffectScene.of(TimeDomain.GAME, EffectScene.UNBOUNDED, two, LIMITS));
        assertDoesNotThrow(() -> EffectScene.of(TimeDomain.COSMETIC, Double.MIN_VALUE, two, LIMITS));
        for (double bad : new double[] {0, -0.0, -1, Double.NaN, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> EffectScene.of(TimeDomain.GAME, bad, two, LIMITS), "lifetime " + bad);
        }
        assertThrows(IllegalArgumentException.class, () -> EffectScene.of(null, 1, two, LIMITS));
        assertThrows(IllegalArgumentException.class, () -> EffectScene.of(TimeDomain.GAME, 1, null, LIMITS));
        assertThrows(IllegalArgumentException.class, () -> EffectScene.of(TimeDomain.GAME, 1, two, null));
        assertThrows(IllegalArgumentException.class, () -> EffectScene.of(TimeDomain.GAME, 1, Arrays.asList(layer("a", one), null), LIMITS));
        assertThrows(IllegalArgumentException.class, () -> EffectScene.of(TimeDomain.GAME, 1, List.of(layer("a", one), layer("a", one)), LIMITS), "duplicate names");
        assertThrows(IllegalArgumentException.class, () -> EffectScene.of(TimeDomain.GAME, 1, two, new SceneLimits(1, 64)), "too many layers");
        assertThrows(IllegalArgumentException.class, () -> EffectScene.of(TimeDomain.GAME, 1, two, new SceneLimits(8, 1)), "too many points");
        assertEquals(2, EffectScene.of(TimeDomain.GAME, 1, two, new SceneLimits(2, 2)).maxPointsPerFrame());
        List<Layer> source = new ArrayList<>(two);
        EffectScene scene = EffectScene.of(TimeDomain.GAME, 1, source, LIMITS);
        source.clear();
        assertEquals(2, scene.layers().size());
        assertThrows(UnsupportedOperationException.class, () -> scene.layers().clear());
        assertEquals(TimeDomain.GAME, scene.domain());
        assertEquals(1.0, scene.lifetimeTicks());
        assertEquals(LIMITS, scene.limits());
    }

    @Test
    @DisplayName("EffectScene.evaluate: null anchor, playback or instant gives IllegalArgumentException")
    void evaluateArguments() {
        EffectScene scene = EffectScene.of(TimeDomain.GAME, 10, List.of(layer("a", Shape.point(Vec3.ZERO))), LIMITS);
        Playback pb = Playback.start(TimePoint.gameTick(0));
        assertThrows(IllegalArgumentException.class, () -> scene.evaluate(null, pb, TimePoint.gameTick(1)));
        assertThrows(IllegalArgumentException.class, () -> scene.evaluate(Pose.IDENTITY, null, TimePoint.gameTick(1)));
        assertThrows(IllegalArgumentException.class, () -> scene.evaluate(Pose.IDENTITY, pb, null));
        assertTrue(scene.evaluate(Pose.IDENTITY, pb, TimePoint.gameTick(1)).pointCount() == 1);
    }
}
