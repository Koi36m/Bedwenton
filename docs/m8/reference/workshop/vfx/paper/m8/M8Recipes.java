package workshop.vfx.paper.m8;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import workshop.vfx.delivery.EffectPriority;
import workshop.vfx.delivery.EffectRecipe;
import workshop.vfx.delivery.LayerRole;
import workshop.vfx.delivery.QualityTier;
import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.curve.Easing;
import workshop.vfx.math.curve.Spiral;
import workshop.vfx.math.sample.Endpoints;
import workshop.vfx.math.sample.SampleLimits;
import workshop.vfx.math.sample.Sampler;
import workshop.vfx.math.time.TimeDomain;
import workshop.vfx.math.time.Timing;
import workshop.vfx.scene.*;

/** M8 R2 THROWAWAY REFERENCE (design verification only; never production). */
public final class M8Recipes {
    public static final StyleKey AURA = StyleKey.of("bedwenton:m8_aura_violet"), HALO = StyleKey.of("bedwenton:m8_aura_gold"),
            TRAIL = StyleKey.of("bedwenton:m8_trail_cyan"), TRAIL_DIM = StyleKey.of("bedwenton:m8_trail_dim"),
            FLASH = StyleKey.of("bedwenton:m8_flash_white"), ROD = StyleKey.of("bedwenton:demo_end_rod"),
            RED = StyleKey.of("bedwenton:demo_dust_red");
    private static final SampleLimits SL = new SampleLimits(256, 256);
    private M8Recipes() { }
    static Shape ring(double r, int n) { return Shape.of(Sampler.uniformParameter(Spiral.circle(r), n, Endpoints.EXCLUDE_END, SL)); }

    public static EffectScene auraScene(int orbit, int halo, int lifetime) {
        List<Layer> layers = new ArrayList<>();
        layers.add(new Layer("orbit", Timing.loop(60), Visibility.ACTIVE_ONLY, Reveal.ALL,
                Motion.fixed(Pose.ofTranslation(new Vec3(0, 1.0, 0))).then(Motion.spin(Vec3.Y, 1, 0, Timing.loop(60), Easing.LINEAR)),
                ring(0.9, orbit), AURA));
        if (halo > 0) layers.add(new Layer("halo", Timing.loop(80), Visibility.ACTIVE_ONLY, Reveal.ALL,
                Motion.fixed(Pose.ofTranslation(new Vec3(0, 2.15, 0))).then(Motion.spin(Vec3.Y, -1, 0, Timing.loop(80), Easing.LINEAR)),
                ring(0.45, halo), HALO));
        return EffectScene.of(TimeDomain.GAME, lifetime, layers, new SceneLimits(4, 72));
    }
    public static EffectRecipe aura(int lifetime) {
        Map<QualityTier, EffectScene> v = new EnumMap<>(QualityTier.class);
        v.put(QualityTier.SIMPLE, auraScene(12, 0, lifetime)); v.put(QualityTier.BALANCED, auraScene(18, 9, lifetime));
        v.put(QualityTier.HEAVY, auraScene(24, 12, lifetime));
        return EffectRecipe.of("bedwenton-m8-aura", EffectPriority.NORMAL, v, Map.of("orbit", LayerRole.ESSENTIAL, "halo", LayerRole.STANDARD));
    }
    public static EffectScene activationScene(int form, int column, int burst, int embers) {
        Timing build = Timing.once(30).withDelay(20), release = Timing.once(10).withDelay(50), dissolve = Timing.once(20).withDelay(60);
        return EffectScene.of(TimeDomain.GAME, 80, List.of(
                new Layer("form", Timing.once(20), Visibility.ACTIVE_ONLY, Reveal.PREFIX, Motion.fixed(Pose.ofTranslation(new Vec3(0, 0.1, 0))), ring(1.5, form), ROD),
                new Layer("column", build, Visibility.ACTIVE_ONLY, Reveal.ALL, Motion.spin(Vec3.Y, 1, 0, build, Easing.LINEAR),
                        Shape.of(Sampler.uniformParameter(Spiral.helix(0.6, 2.0, 2), column, Endpoints.INCLUDE_END, SL)), RED),
                new Layer("burst", release, Visibility.ACTIVE_ONLY, Reveal.ALL,
                        Motion.fixed(Pose.ofTranslation(new Vec3(0, 1.0, 0))).then(Motion.scale(1.0, 3.5, release, Easing.QUAD_OUT)), ring(1.0, burst), FLASH),
                new Layer("embers", dissolve, Visibility.ACTIVE_ONLY, Reveal.ALL,
                        Motion.translate(new Vec3(0, 0.2, 0), new Vec3(0, 1.6, 0), dissolve, Easing.QUAD_IN), ring(2.5, embers), AURA)),
                new SceneLimits(4, 128));
    }
    public static EffectRecipe activation() {
        Map<QualityTier, EffectScene> v = new EnumMap<>(QualityTier.class);
        v.put(QualityTier.SIMPLE, activationScene(16, 12, 16, 8)); v.put(QualityTier.BALANCED, activationScene(24, 18, 24, 12));
        v.put(QualityTier.HEAVY, activationScene(32, 24, 32, 16));
        return EffectRecipe.of("bedwenton-m8-activation", EffectPriority.ACTION, v, Map.of());
    }
    /**
     * The rebuilt per-tick trail scene (showcase B, R2): two layers split by age; scene lifetime 2 ticks; evaluated with
     * a playback restarted at the current tick (scene clock), never the effect's start tick.
     */
    public static EffectRecipe trail(List<TrailSample> liveNewestFirst, long tick, int maxAge, QualityTier tier) {
        TrailSample newest = liveNewestFirst.getFirst();
        List<Vec3> fresh = new ArrayList<>(), old = new ArrayList<>();
        for (TrailSample s : liveNewestFirst) {
            Vec3 local = new Vec3(s.x() - newest.x(), s.y() - newest.y() + 0.1, s.z() - newest.z());
            (tick - s.tick() <= maxAge / 2 ? fresh : old).add(local);
        }
        List<Layer> layers = new ArrayList<>();
        if (!fresh.isEmpty()) layers.add(new Layer("fresh", Timing.once(2), Visibility.ACTIVE_ONLY, Reveal.ALL, Motion.IDENTITY, Shape.points(fresh), TRAIL));
        if (!old.isEmpty()) layers.add(new Layer("old", Timing.once(2), Visibility.ACTIVE_ONLY, Reveal.ALL, Motion.IDENTITY, Shape.points(old), TRAIL_DIM));
        EffectScene scene = EffectScene.of(TimeDomain.GAME, 2, layers, new SceneLimits(2, 72));
        Map<String, LayerRole> roles = new java.util.HashMap<>();
        if (!fresh.isEmpty()) roles.put("fresh", LayerRole.ESSENTIAL);
        if (!old.isEmpty()) roles.put("old", LayerRole.DECORATIVE);   // M6 accepts roles only for existing layers
        return EffectRecipe.of("bedwenton-m8-trail", EffectPriority.AMBIENT, Map.of(tier, scene), roles);
    }
}
