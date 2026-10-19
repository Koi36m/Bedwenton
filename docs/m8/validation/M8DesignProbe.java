import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import workshop.vfx.delivery.*;
import workshop.vfx.math.core.*;
import workshop.vfx.math.curve.Easing;
import workshop.vfx.math.curve.Spiral;
import workshop.vfx.math.sample.*;
import workshop.vfx.math.time.*;
import workshop.vfx.scene.*;

/**
 * M8 R1 DESIGN PROBE (scratch evidence; not production). Uses only the accepted M1-M6 engine API to check the
 * M8-ANCHOR-AND-MOTION-SPEC conventions and the showcase scenes:
 *  P1 yaw -> quaternion mapping against Paper's documented yaw convention;
 *  P2 a spinning aura evaluated with a moving, turning anchor at known ticks, against an independent formula;
 *  P3 activation phase point counts at phase boundaries (M2 timing + M5 composition);
 *  P4 synthetic benchmark: rebuilding the two-layer (fresh/old) trail scene of the R2 design and planning it every tick
 *     (indicative only; one container; not a server measurement).
 * R2: the probe exits with status 1 when P1 > 1e-12, P2 > 1e-9 or any P3 phase boundary differs from the expected table.
 */
public class M8DesignProbe {
    static int failures;
    static void check(boolean ok, String what) { if (!ok) { failures++; System.out.println("  FAIL: " + what); } }
    static final SampleLimits SL = new SampleLimits(256, 256);
    static final StyleKey AURA = StyleKey.of("bedwenton:m8_aura_violet"), HALO = StyleKey.of("bedwenton:m8_aura_gold"),
            TRAIL = StyleKey.of("bedwenton:m8_trail_cyan"), FLASH = StyleKey.of("bedwenton:m8_flash_white"),
            ROD = StyleKey.of("bedwenton:demo_end_rod"), RED = StyleKey.of("bedwenton:demo_dust_red");

    /** Spec section 4: rotation about +Y by -yaw, so local +Z (forward) maps to Paper's facing (-sin yaw, 0, cos yaw). */
    static Quat yawQuat(double yawDegrees) {
        double half = -StrictMath.toRadians(yawDegrees) / 2;
        return new Quat(StrictMath.cos(half), 0, StrictMath.sin(half), 0);
    }

    static Shape ring(double r, int n) {
        return Shape.of(Sampler.uniformParameter(Spiral.circle(r), n, Endpoints.EXCLUDE_END, SL));
    }

    static EffectScene auraTier(int orbit, int halo, int lifetime) {
        List<Layer> layers = new ArrayList<>();
        Motion orbitMotion = Motion.fixed(Pose.ofTranslation(new Vec3(0, 1.0, 0)))
                .then(Motion.spin(Vec3.Y, 1, 0, Timing.loop(60), Easing.LINEAR));
        layers.add(new Layer("orbit", Timing.loop(60), Visibility.ACTIVE_ONLY, Reveal.ALL, orbitMotion, ring(0.9, orbit), AURA));
        if (halo > 0) {
            Motion haloMotion = Motion.fixed(Pose.ofTranslation(new Vec3(0, 2.15, 0)))
                    .then(Motion.spin(Vec3.Y, -1, 0, Timing.loop(80), Easing.LINEAR));
            layers.add(new Layer("halo", Timing.loop(80), Visibility.ACTIVE_ONLY, Reveal.ALL, haloMotion, ring(0.45, halo), HALO));
        }
        return EffectScene.of(TimeDomain.GAME, lifetime, layers, new SceneLimits(4, 72));
    }

    static EffectScene activationTier(int form, int column, int burst, int embers) {
        Layer formL = new Layer("form", Timing.once(20), Visibility.ACTIVE_ONLY, Reveal.PREFIX,
                Motion.fixed(Pose.ofTranslation(new Vec3(0, 0.1, 0))), ring(1.5, form), ROD);
        Timing build = Timing.once(30).withDelay(20);
        Layer columnL = new Layer("column", build, Visibility.ACTIVE_ONLY, Reveal.ALL,
                Motion.spin(Vec3.Y, 1, 0, build, Easing.LINEAR),
                Shape.of(Sampler.uniformParameter(Spiral.helix(0.6, 2.0, 2), column, Endpoints.INCLUDE_END, SL)), RED);
        Timing release = Timing.once(10).withDelay(50);
        Layer burstL = new Layer("burst", release, Visibility.ACTIVE_ONLY, Reveal.ALL,
                Motion.fixed(Pose.ofTranslation(new Vec3(0, 1.0, 0))).then(Motion.scale(1.0, 3.5, release, Easing.QUAD_OUT)),
                ring(1.0, burst), FLASH);
        Timing dissolve = Timing.once(20).withDelay(60);
        Layer embersL = new Layer("embers", dissolve, Visibility.ACTIVE_ONLY, Reveal.ALL,
                Motion.translate(new Vec3(0, 0.2, 0), new Vec3(0, 1.6, 0), dissolve, Easing.QUAD_IN), ring(2.5, embers), AURA);
        return EffectScene.of(TimeDomain.GAME, 80, List.of(formL, columnL, burstL, embersL), new SceneLimits(4, 128));
    }

    public static void main(String[] args) {
        run();
        System.out.println(failures == 0 ? "PROBE RESULT: PASS (all P1-P3 assertions)" : "PROBE RESULT: FAIL (" + failures + " assertion(s))");
        if (failures != 0) System.exit(1);
    }

    static void run() {
        System.out.println("P1 yaw mapping: local +Z (forward) and +X (left) in world space; Paper: yaw 0 = +Z, 90 = -X, 180 = -Z, 270 = +X");
        double worst = 0;
        for (double yaw : new double[] {0, 45, 90, 180, 270, -90, 359.5, 720}) {
            Quat q = yawQuat(yaw);
            Vec3 f = q.rotate(Vec3.Z), l = q.rotate(Vec3.X), u = q.rotate(Vec3.Y);
            double r = StrictMath.toRadians(yaw);
            Vec3 expect = new Vec3(-StrictMath.sin(r), 0, StrictMath.cos(r));
            worst = Math.max(worst, f.sub(expect).length());
            System.out.printf("  yaw %6.1f forward (%7.4f, %7.4f, %7.4f) left (%7.4f, %7.4f, %7.4f) up (%4.1f, %4.1f, %4.1f)%n",
                    yaw, f.x(), f.y(), f.z(), l.x(), l.y(), l.z(), u.x(), u.y(), u.z());
        }
        System.out.printf("  max |forward - (-sin yaw, 0, cos yaw)| = %.3e%n", worst);
        check(worst <= 1e-12, "P1 yaw mapping tolerance 1e-12");

        System.out.println("P2 aura (BALANCED: orbit 18 + halo 9) with a moving, turning anchor; start tick 1000");
        Map<QualityTier, EffectScene> av = new EnumMap<>(QualityTier.class);
        av.put(QualityTier.SIMPLE, auraTier(12, 0, 1200)); av.put(QualityTier.BALANCED, auraTier(18, 9, 1200)); av.put(QualityTier.HEAVY, auraTier(24, 12, 1200));
        EffectRecipe aura = EffectRecipe.of("bedwenton-m8-aura", EffectPriority.NORMAL, av, Map.of("orbit", LayerRole.ESSENTIAL, "halo", LayerRole.STANDARD));
        double worstAura = 0; int checked = 0;
        for (long t : new long[] {1000, 1001, 1015, 1030, 1059, 1060, 1123}) {
            double px = 100.5 + 0.28 * (t - 1000), py = 64.0, pz = -20.5 + 0.1 * (t - 1000), yaw = 3.0 * (t - 1000);
            Pose anchor = new Pose(new Vec3(px, py, pz), yawQuat(yaw), 1);
            EffectFrame frame = av.get(QualityTier.BALANCED).evaluate(anchor, Playback.start(TimePoint.gameTick(1000)), TimePoint.gameTick(t));
            FrameBatch orbit = frame.batches().get(0);
            for (int i = 0; i < orbit.size(); i++) {
                // independent expectation: base angle of sample i on Spiral.circle (+X toward -Z), plus spin, plus yaw
                double base = 2 * Math.PI * i / orbit.size();
                double spin = 2 * Math.PI * ((t - 1000) % 60) / 60.0;
                double a = base + spin;                         // rotation about +Y, right-handed
                double lx = 0.9 * Math.cos(a), lz = -0.9 * Math.sin(a);
                double yr = -Math.toRadians(yaw);               // anchor rotation about +Y
                double wx = lx * Math.cos(yr) + lz * Math.sin(yr), wz = -lx * Math.sin(yr) + lz * Math.cos(yr);
                Vec3 expect = new Vec3(px + wx, py + 1.0, pz + wz);
                worstAura = Math.max(worstAura, orbit.point(i).sub(expect).length()); checked++;
            }
            System.out.printf("  tick %d: %d points (%d batches), first orbit point (%.4f, %.4f, %.4f)%n", t, frame.pointCount(),
                    frame.batches().size(), orbit.x(0), orbit.y(0), orbit.z(0));
        }
        System.out.printf("  %d orbit points checked; max deviation from the independent formula = %.3e blocks%n", checked, worstAura);
        check(checked == 126 && worstAura <= 1e-9, "P2 aura tolerance 1e-9 over 126 points");

        System.out.println("P3 activation (HEAVY: form 32, column 24, burst 32, embers 16; lifetime 80) visible points per layer");
        EffectScene act = activationTier(32, 24, 32, 16);
        System.out.println("  maxPointsPerFrame (generation reservation) = " + act.maxPointsPerFrame());
        check(act.maxPointsPerFrame() == 104, "P3 HEAVY reservation 104");
        java.util.Map<Long, String> expected = new java.util.HashMap<>(java.util.Map.ofEntries(
                java.util.Map.entry(0L, ""), java.util.Map.entry(1L, "form=1"), java.util.Map.entry(10L, "form=16"), java.util.Map.entry(19L, "form=30"),
                java.util.Map.entry(20L, "column=24"), java.util.Map.entry(35L, "column=24"), java.util.Map.entry(49L, "column=24"),
                java.util.Map.entry(50L, "burst=32"), java.util.Map.entry(55L, "burst=32"), java.util.Map.entry(59L, "burst=32"),
                java.util.Map.entry(60L, "embers=16"), java.util.Map.entry(70L, "embers=16"), java.util.Map.entry(79L, "embers=16"), java.util.Map.entry(80L, "")));
        for (long t : new long[] {0, 1, 10, 19, 20, 35, 49, 50, 55, 59, 60, 70, 79, 80}) {
            EffectFrame f = act.evaluate(Pose.ofTranslation(new Vec3(0.5, 64, 0.5)), Playback.start(TimePoint.gameTick(0)), TimePoint.gameTick(t));
            StringBuilder sb = new StringBuilder();
            for (FrameBatch b : f.batches()) sb.append(b.layer()).append('=').append(b.size()).append(' ');
            System.out.printf("  local %3d: stage %-6s total %3d  %s%n", t, f.stage(), f.pointCount(), sb.toString().trim());
            check(expected.get(t).equals(sb.toString().trim()), "P3 phase table at local " + t);
        }

        System.out.println("P4 SYNTHETIC benchmark: two-layer trail rebuild (fresh/old split by age, R2 design) + one global plan (indicative)");
        Set<StyleKey> styles = Set.of(AURA, HALO, TRAIL, StyleKey.of("bedwenton:m8_trail_dim"), FLASH, ROD, RED);
        DeliveryPolicy pol = new DeliveryPolicy(16, 16, 256, 1152, 0, 2048, 0, 2048, 256, 24, 48, 72);
        for (int samples : new int[] {12, 24, 36}) {
            long sink = 0, rounds = 20000, warm = 20000, t0 = 0;
            for (long k = 0; k < warm + rounds; k++) {
                if (k == warm) t0 = System.nanoTime();
                List<Vec3> fresh = new ArrayList<>(samples), old = new ArrayList<>(samples);
                for (int i = 0; i < samples; i++) (i < samples / 2 ? fresh : old).add(new Vec3(-0.35 * i, 0.1, 0.02 * (k % 7)));
                EffectScene s = EffectScene.of(TimeDomain.GAME, 2, List.of(
                        new Layer("fresh", Timing.once(2), Visibility.ACTIVE_ONLY, Reveal.ALL, Motion.IDENTITY, Shape.points(fresh), TRAIL),
                        new Layer("old", Timing.once(2), Visibility.ACTIVE_ONLY, Reveal.ALL, Motion.IDENTITY, Shape.points(old), StyleKey.of("bedwenton:m8_trail_dim"))),
                        new SceneLimits(2, 72));
                EffectRecipe r = EffectRecipe.of("bedwenton-m8-trail", EffectPriority.AMBIENT, Map.of(QualityTier.HEAVY, s),
                        Map.of("fresh", LayerRole.ESSENTIAL, "old", LayerRole.DECORATIVE));
                DeliveryRequest q = DeliveryRequest.of(1, r, QualityTier.HEAVY, 0, Pose.ofTranslation(new Vec3(10.5, 64, 10.5)),
                        Playback.start(TimePoint.gameTick(k)), TimePoint.gameTick(k), k, new long[] {0, 1});   // scene clock restarted per tick
                sink += DeliveryPlanner.plan(k, List.of(q), styles, pol).placements();
            }
            System.out.printf("  %2d trail samples, 2 viewers: %.2f us per tick (build scene + recipe + plan), placements/tick %d%n",
                    samples, (System.nanoTime() - t0) / 1000.0 / rounds, sink / (warm + rounds));
        }
    }
}
