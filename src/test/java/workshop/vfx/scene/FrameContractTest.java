package workshop.vfx.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static workshop.vfx.scene.M5TestSupport.LIMITS;

import java.util.ArrayList;
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
import workshop.vfx.math.time.TimingSample.Stage;

/** Contract section 8 (frames): order, omission of empty batches, reveal counts, immutability and exact placement. */
class FrameContractTest {
    private static EffectFrame at(EffectScene s, Pose anchor, double local) {
        return s.evaluate(anchor, new Playback(TimePoint.gameTick(0), local, 1, true), TimePoint.gameTick(0));
    }

    private static Shape line(int n) {
        List<Vec3> pts = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            pts.add(new Vec3(i, 0, 0));
        }
        return Shape.points(pts);
    }

    @Test
    @DisplayName("Order: batches follow layer insertion order (layerIndex increasing), points follow shape order; overlapping layers with identical timing and geometry are all emitted")
    void order() {
        List<Layer> layers = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            Timing t = i % 3 == 0 ? Timing.once(100) : Timing.loop(7).withDelay(i % 5);
            layers.add(new Layer(String.format("l%02d", 39 - i), t, Visibility.ACTIVE_ONLY, Reveal.ALL, Motion.IDENTITY, line(i % 4 + 1), StyleKey.of(i % 2 == 0 ? "a:even" : "a:odd")));
        }
        EffectScene s = EffectScene.of(TimeDomain.GAME, 1000, layers, LIMITS);
        EffectFrame f = at(s, Pose.IDENTITY, 50);
        int previous = -1, total = 0;
        for (FrameBatch b : f.batches()) {
            assertTrue(b.layerIndex() > previous, "increasing layer index");
            previous = b.layerIndex();
            Layer l = layers.get(b.layerIndex());
            assertEquals(l.name(), b.layer());
            assertEquals(l.style(), b.style());
            for (int k = 0; k < b.size(); k++) {
                assertEquals(new Vec3(k, 0, 0), b.point(k));
            }
            total += b.size();
        }
        assertEquals(total, f.pointCount());
        assertEquals(40 - (int) layers.stream().filter(l -> l.timing().sample(50).stage() != Stage.ACTIVE).count(), f.batches().size());
    }

    @Test
    @DisplayName("Omission: hidden layers (BEFORE, AFTER with ACTIVE_ONLY), empty shapes and empty reveals produce no batch; an empty scene gives an empty ACTIVE frame")
    void omission() {
        EffectScene s = EffectScene.of(TimeDomain.GAME, 100, List.of(
            new Layer("before", Timing.once(5).withDelay(50), Visibility.HOLD_AFTER, Reveal.ALL, Motion.IDENTITY, line(3), M5TestSupport.STYLE),
            new Layer("after", Timing.once(5), Visibility.ACTIVE_ONLY, Reveal.ALL, Motion.IDENTITY, line(3), M5TestSupport.STYLE),
            new Layer("empty", Timing.once(100), Visibility.ACTIVE_ONLY, Reveal.ALL, Motion.IDENTITY, Shape.points(List.of()), M5TestSupport.STYLE),
            new Layer("unrevealed", Timing.once(100).withDelay(9), Visibility.ACTIVE_ONLY, Reveal.PREFIX, Motion.IDENTITY, line(3), M5TestSupport.STYLE),
            new Layer("shown", Timing.once(100), Visibility.ACTIVE_ONLY, Reveal.ALL, Motion.IDENTITY, line(2), M5TestSupport.STYLE)), LIMITS);
        EffectFrame f = at(s, Pose.IDENTITY, 10);
        assertEquals(1, f.batches().size());
        assertEquals("shown", f.batches().get(0).layer());
        assertEquals(4, f.batches().get(0).layerIndex());
        EffectFrame empty = at(EffectScene.of(TimeDomain.GAME, 10, List.of(), LIMITS), Pose.IDENTITY, 1);
        assertEquals(Stage.ACTIVE, empty.stage());
        assertTrue(empty.isEmpty());
        assertEquals(0, empty.pointCount());
    }

    @Test
    @DisplayName("Reveal PREFIX: k = n at progress >= 1, else min(n - 1, floor(progress n)); 0 at the start, n - 1 just before the end, n when held after; a 1-point shape appears only at progress 1")
    void reveal() {
        for (int n : new int[] {1, 2, 9, 10, 37, 1000}) {
            Timing t = Timing.once(n);
            EffectScene s = EffectScene.of(TimeDomain.GAME, 10 * n + 10, List.of(new Layer("r", t, Visibility.HOLD_AFTER, Reveal.PREFIX, Motion.IDENTITY, line(n), M5TestSupport.STYLE)), LIMITS);
            for (int k = 0; k < n; k++) {
                EffectFrame f = at(s, Pose.IDENTITY, k);
                assertEquals(k, f.pointCount(), "n " + n + " at progress " + k + "/" + n);
                if (k > 0) {
                    assertEquals(k, f.batches().get(0).size());
                    assertEquals(new Vec3(k - 1, 0, 0), f.batches().get(0).point(k - 1), "a prefix in shape order");
                }
            }
            assertEquals(n - 1, at(s, Pose.IDENTITY, Math.nextDown((double) n)).pointCount());
            assertEquals(n, at(s, Pose.IDENTITY, n).pointCount());
            assertEquals(n, at(s, Pose.IDENTITY, 10 * n).pointCount());
        }
        EffectScene pp = EffectScene.of(TimeDomain.GAME, 100, List.of(new Layer("r", Timing.pingPong(10).withCycles(2), Visibility.HOLD_AFTER, Reveal.PREFIX, Motion.IDENTITY, line(10), M5TestSupport.STYLE)), LIMITS);
        assertEquals(5, at(pp, Pose.IDENTITY, 15).pointCount(), "ping-pong reverse pass at progress 0.5");
        assertEquals(0, at(pp, Pose.IDENTITY, 50).pointCount(), "held at the ping-pong end progress 0");
    }

    @Test
    @DisplayName("Immutability: batches and layers are unmodifiable; coordinates() is a copy; accessors outside [0, size) throw IndexOutOfBoundsException")
    void immutability() {
        EffectScene s = M5TestSupport.healing();
        EffectFrame f = at(s, Pose.IDENTITY, 5);
        FrameBatch b = f.batches().get(0);
        assertThrows(UnsupportedOperationException.class, () -> f.batches().clear());
        assertThrows(UnsupportedOperationException.class, () -> f.batches().add(b));
        double[] c = b.coordinates();
        assertEquals(3 * b.size(), c.length);
        double x0 = b.x(0);
        c[0] = 12345;
        assertEquals(x0, b.x(0));
        assertEquals(x0, b.coordinates()[0]);
        for (int bad : new int[] {-1, b.size(), Integer.MAX_VALUE}) {
            assertThrows(IndexOutOfBoundsException.class, () -> b.x(bad));
            assertThrows(IndexOutOfBoundsException.class, () -> b.y(bad));
            assertThrows(IndexOutOfBoundsException.class, () -> b.z(bad));
            assertThrows(IndexOutOfBoundsException.class, () -> b.point(bad));
        }
        for (int k = 0; k < b.size(); k++) {
            assertEquals(new Vec3(c[3 * k] == 12345 ? x0 : c[3 * k], c[3 * k + 1], c[3 * k + 2]), b.point(k));
        }
    }

    @Test
    @DisplayName("Exact single-point placement: with the identity anchor and no motion, every coordinate is returned exactly; with a translation-only anchor, exactly anchor + point (one rounding per coordinate)")
    void exactPlacement() {
        Vec3 p = new Vec3(0.25, 64.5, -3.75);
        EffectScene s = EffectScene.of(TimeDomain.GAME, 1, List.of(new Layer("p", Timing.once(1), Visibility.ACTIVE_ONLY, Reveal.ALL, Motion.IDENTITY, Shape.point(p), M5TestSupport.STYLE)), LIMITS);
        assertEquals(p, at(s, Pose.IDENTITY, 0).batches().get(0).point(0));
        Vec3 t = new Vec3(1e7 + 0.1, -0.3, 123.456);
        FrameBatch b = at(s, Pose.ofTranslation(t), 0).batches().get(0);
        assertEquals(t.x() + p.x(), b.x(0));
        assertEquals(t.y() + p.y(), b.y(0));
        assertEquals(t.z() + p.z(), b.z(0));
    }

    @Test
    @DisplayName("Point counts: pointCount is the sum of batch sizes and never exceeds scene.maxPointsPerFrame(), at every instant of the example scenes")
    void counts() {
        for (EffectScene s : new EffectScene[] {M5TestSupport.healing(), M5TestSupport.trident(), M5TestSupport.constellation()}) {
            for (double local = -2; local <= 125; local += 0.5) {
                EffectFrame f = at(s, Pose.IDENTITY, local);
                int sum = 0;
                for (FrameBatch b : f.batches()) {
                    sum += b.size();
                    assertTrue(b.size() > 0);
                }
                assertEquals(sum, f.pointCount());
                assertTrue(sum <= s.maxPointsPerFrame());
            }
        }
    }

    @Test
    @DisplayName("Easing applies only to motions; batch timing and reveal use the layer's raw M2 progress")
    void rawProgressForReveal() {
        Timing t = Timing.once(10);
        EffectScene s = EffectScene.of(TimeDomain.GAME, 20, List.of(new Layer("r", t, Visibility.ACTIVE_ONLY, Reveal.PREFIX,
            Motion.translate(Vec3.ZERO, new Vec3(0, 1, 0), t, Easing.QUINT_IN), line(10), M5TestSupport.STYLE)), LIMITS);
        FrameBatch b = at(s, Pose.IDENTITY, 5).batches().get(0);
        assertEquals(0.5, b.timing().progress());
        assertEquals(5, b.size());
        assertEquals(Easing.QUINT_IN.apply(0.5), b.y(0), 1e-16);
    }
}
