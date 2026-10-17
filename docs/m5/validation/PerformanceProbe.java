package workshop.vfx.scene;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Quat;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.curve.Easing;
import workshop.vfx.math.time.Playback;
import workshop.vfx.math.time.TimeDomain;
import workshop.vfx.math.time.TimePoint;
import workshop.vfx.math.time.Timing;

/**
 * M5 R1 performance probe (review scratch; run against the throwaway reference implementation, which is not delivered).
 * Compile together with src/test/java/workshop/vfx/scene/M5TestSupport.java. Reports indicative evaluate() cost per
 * frame and per point, and bytes allocated per frame, for the four example scenes and a dense synthetic scene.
 */
public class PerformanceProbe {
    static final com.sun.management.ThreadMXBean MX = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    static long sink;

    static void measure(String name, EffectScene s, double period) {
        Pose anchor = new Pose(new Vec3(1000.5, 70, -2000.5), new Quat(0.9, 0, 0.43, 0), 1);
        int frames = 20_000;
        long points = 0;
        for (int warm = 0; warm < 3; warm++) {
            for (int i = 0; i < frames; i++) sink += s.evaluate(anchor, new Playback(new TimePoint(s.domain(), 0), (i * 0.37) % period, 1, true), new TimePoint(s.domain(), 0)).pointCount();
        }
        long b0 = MX.getCurrentThreadAllocatedBytes(), t0 = System.nanoTime();
        for (int i = 0; i < frames; i++) {
            EffectFrame f = s.evaluate(anchor, new Playback(new TimePoint(s.domain(), 0), (i * 0.37) % period, 1, true), new TimePoint(s.domain(), 0));
            points += f.pointCount();
        }
        long ns = System.nanoTime() - t0, bytes = MX.getCurrentThreadAllocatedBytes() - b0;
        System.out.printf("%-34s layers %4d, max points %6d: mean points/frame %8.1f, %9.0f ns/frame, %6.1f ns/point, %9.0f bytes/frame%n",
            name, s.layers().size(), s.maxPointsPerFrame(), points / (double) frames, ns / (double) frames, points == 0 ? 0 : ns / (double) points, bytes / (double) frames);
    }

    public static void main(String[] a) {
        measure("A. healing ritual", M5TestSupport.healing(), 60);
        measure("B. orbiting sparkles", M5TestSupport.orbit(), 400);
        measure("C. moving trident helix", M5TestSupport.trident(), 30);
        measure("D. Astral Prism constellation", M5TestSupport.constellation(), 120);
        List<Layer> layers = new ArrayList<>();
        Shape ring = M5TestSupport.circle(2, 1024);
        Timing loop = Timing.loop(80);
        for (int i = 0; i < 64; i++) {
            Motion m = Motion.spin(Vec3.Y, 1, i / 64.0, loop, Easing.LINEAR).then(Motion.translate(Vec3.ZERO, new Vec3(0, 3, 0), Timing.pingPong(40), Easing.SMOOTHSTEP))
                .then(Motion.scale(0.5, 1.5, Timing.pingPong(20), Easing.QUAD_IN_OUT)).then(Motion.fixed(new Pose(Vec3.ZERO, new Quat(0.98, 0.2, 0, 0), 1)));
            layers.add(new Layer("dense-" + i, loop, Visibility.ACTIVE_ONLY, Reveal.ALL, m, ring, M5TestSupport.STYLE));
        }
        measure("dense: 64 layers x 1,024 points, 4 steps", EffectScene.of(TimeDomain.GAME, EffectScene.UNBOUNDED, layers, new SceneLimits(64, 1 << 16)), 400);
        System.out.println("(indicative only: one container, warm JIT, Java " + System.getProperty("java.version") + "; not acceptance criteria)");
    }
}
