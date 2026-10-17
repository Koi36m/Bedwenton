package workshop.vfx.scene;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Quat;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.time.Playback;
import workshop.vfx.math.time.TimePoint;

/** Contract section 10: bit-identical results under repetition, JIT warm-up and concurrent use of shared scenes. */
class M5DeterminismTest {
    private static long[] fingerprint(EffectScene s, Pose anchor, double local) {
        EffectFrame f = s.evaluate(anchor, new Playback(new TimePoint(s.domain(), 0), local, 1, true), new TimePoint(s.domain(), 0));
        List<Long> bits = new ArrayList<>();
        bits.add((long) f.stage().ordinal());
        bits.add(Double.doubleToRawLongBits(f.localTicks()));
        for (FrameBatch b : f.batches()) {
            bits.add((long) b.layerIndex());
            bits.add(b.timing().cycle());
            bits.add(Double.doubleToRawLongBits(b.timing().progress()));
            for (double c : b.coordinates()) {
                bits.add(Double.doubleToRawLongBits(c));
            }
        }
        return bits.stream().mapToLong(Long::longValue).toArray();
    }

    private static final Pose ANCHOR = new Pose(new Vec3(-1234.5, 70.25, 98765.125), new Quat(0.3, -0.4, 0.5, 0.6), 1.75);
    private static final double[] TIMES = {-1, 0, 0.5, 7, 13.25, 19.999, 20, 33, 47.5, 58, 64, 90.125, 119, 120, 400};

    @Test
    @DisplayName("Repetition: 25 rounds over the four example scenes at 15 instants give bit-identical frames (including through JIT compilation)")
    void repetition() {
        EffectScene[] scenes = {M5TestSupport.healing(), M5TestSupport.orbit(), M5TestSupport.trident(), M5TestSupport.constellation()};
        List<long[]> first = new ArrayList<>();
        for (EffectScene s : scenes) {
            for (double t : TIMES) {
                first.add(fingerprint(s, ANCHOR, t));
            }
        }
        for (int round = 0; round < 25; round++) {
            int i = 0;
            for (EffectScene s : scenes) {
                for (double t : TIMES) {
                    assertArrayEquals(first.get(i++), fingerprint(s, ANCHOR, t), "round " + round);
                }
            }
        }
    }

    @Test
    @DisplayName("Threads: 8 threads evaluating the same scene objects concurrently produce the single-threaded frames")
    void threads() throws Exception {
        EffectScene[] scenes = {M5TestSupport.healing(), M5TestSupport.orbit(), M5TestSupport.trident(), M5TestSupport.constellation()};
        List<long[]> expected = new ArrayList<>();
        for (EffectScene s : scenes) {
            for (double t : TIMES) {
                expected.add(fingerprint(s, ANCHOR, t));
            }
        }
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int w = 0; w < 8; w++) {
                results.add(pool.submit(() -> {
                    int checked = 0;
                    for (int round = 0; round < 40; round++) {
                        int i = 0;
                        for (EffectScene s : scenes) {
                            for (double t : TIMES) {
                                assertArrayEquals(expected.get(i++), fingerprint(s, ANCHOR, t));
                                checked++;
                            }
                        }
                    }
                    return checked;
                }));
            }
            for (Future<Integer> r : results) {
                assertEquals(40 * scenes.length * TIMES.length, r.get());
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
