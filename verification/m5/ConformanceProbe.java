package workshop.vfx.scene;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.SplittableRandom;
import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Quat;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.curve.Easing;
import workshop.vfx.math.time.Playback;
import workshop.vfx.math.time.TimeDomain;
import workshop.vfx.math.time.TimePoint;
import workshop.vfx.math.time.Timing;

/**
 * M5 verification probe: hashes the raw bits of every frame (stage, local ticks, layer index, M2 sample, pose and
 * coordinates) for the four example scenes at 2,001 instants each and for 3,000 random scenes, so that two
 * implementations of the normative operation order can be compared bit for bit by running this against each.
 * Compile with src/test/java/workshop/vfx/scene/M5TestSupport.java against the implementation's main classes.
 */
public class ConformanceProbe {
    static MessageDigest md;
    static long frames, points;

    static void put(long v) {
        for (int i = 0; i < 8; i++) md.update((byte) (v >>> (8 * i)));
    }

    static void frame(EffectScene s, Pose anchor, double local) {
        EffectFrame f;
        try {
            f = s.evaluate(anchor, new Playback(new TimePoint(s.domain(), 0), local, 1, true), new TimePoint(s.domain(), 0));
        } catch (ArithmeticException e) {
            put(-1);
            return;
        }
        frames++;
        put(f.stage().ordinal());
        put(Double.doubleToRawLongBits(f.localTicks()));
        for (FrameBatch b : f.batches()) {
            put(b.layerIndex());
            put(b.timing().cycle());
            put(Double.doubleToRawLongBits(b.timing().progress()));
            Pose p = b.pose();
            for (double v : new double[] {p.translation().x(), p.translation().y(), p.translation().z(), p.rotation().w(), p.rotation().x(), p.rotation().y(), p.rotation().z(), p.scale()}) put(Double.doubleToRawLongBits(v));
            for (double c : b.coordinates()) put(Double.doubleToRawLongBits(c));
            points += b.size();
        }
    }

    public static void main(String[] a) throws Exception {
        md = MessageDigest.getInstance("SHA-256");
        Pose anchor = new Pose(new Vec3(-1234.5, 70.25, 98765.125), new Quat(0.3, -0.4, 0.5, 0.6), 1.75);
        EffectScene[] demos = {M5TestSupport.healing(), M5TestSupport.orbit(), M5TestSupport.trident(), M5TestSupport.constellation()};
        for (EffectScene s : demos) for (int i = -100; i <= 1900; i++) frame(s, anchor, i * 0.0625);
        SplittableRandom r = new SplittableRandom(20261017);
        Easing[] easings = Easing.values();
        for (int c = 0; c < 3000; c++) {
            List<Layer> layers = new ArrayList<>();
            int nl = r.nextInt(1, 6);
            for (int l = 0; l < nl; l++) {
                Timing t = switch (r.nextInt(3)) {
                    case 0 -> Timing.once(r.nextDouble(0.5, 40)).withDelay(r.nextDouble(-5, 20));
                    case 1 -> Timing.loop(r.nextDouble(0.5, 40));
                    default -> Timing.pingPong(r.nextDouble(0.5, 40)).withCycles(r.nextLong(1, 5));
                };
                Motion m = Motion.IDENTITY;
                for (int k = r.nextInt(0, 6); k > 0; k--) {
                    Easing e = easings[r.nextInt(easings.length)];
                    m = m.then(switch (r.nextInt(4)) {
                        case 0 -> Motion.fixed(new Pose(new Vec3(r.nextDouble(-3, 3), r.nextDouble(-3, 3), r.nextDouble(-3, 3)), new Quat(r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1), 0.2), r.nextDouble(0.5, 2)));
                        case 1 -> Motion.spin(new Vec3(r.nextDouble(-1, 1), r.nextDouble(-1, 1), 0.3), r.nextDouble(-20, 20), r.nextDouble(-2, 2), t, e);
                        case 2 -> Motion.scale(r.nextDouble(0.2, 3), r.nextDouble(0.2, 3), t, e);
                        default -> Motion.translate(new Vec3(r.nextDouble(-5, 5), 0, 1), new Vec3(r.nextDouble(-5, 5), 2, -1), t, e);
                    });
                }
                List<Vec3> pts = new ArrayList<>();
                for (int p = r.nextInt(0, 12); p > 0; p--) pts.add(new Vec3(r.nextDouble(-4, 4), r.nextDouble(-4, 4), r.nextDouble(-4, 4)));
                layers.add(new Layer("l" + l, t, r.nextBoolean() ? Visibility.ACTIVE_ONLY : Visibility.HOLD_AFTER, r.nextBoolean() ? Reveal.ALL : Reveal.PREFIX, m, Shape.points(pts), StyleKey.of("probe:s" + l)));
            }
            EffectScene s = EffectScene.of(r.nextBoolean() ? TimeDomain.GAME : TimeDomain.COSMETIC, r.nextBoolean() ? EffectScene.UNBOUNDED : r.nextDouble(1, 100), layers, new SceneLimits(8, 64));
            Pose an = new Pose(new Vec3(r.nextDouble(-3e4, 3e4), r.nextDouble(-64, 320), r.nextDouble(-3e4, 3e4)), new Quat(r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1) + 0.01), r.nextDouble(0.1, 10));
            for (int i = 0; i < 8; i++) frame(s, an, r.nextDouble(-10, 120));
        }
        System.out.println("frames " + frames + ", points " + points + ", SHA-256 of all frame bits " + HexFormat.of().formatHex(md.digest()));
    }
}
