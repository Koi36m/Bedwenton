package workshop.vfx.paper.m8;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import workshop.vfx.delivery.*;
import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.curve.Easing;
import workshop.vfx.math.curve.Spiral;
import workshop.vfx.math.sample.Endpoints;
import workshop.vfx.math.sample.SampleLimits;
import workshop.vfx.math.sample.Sampler;
import workshop.vfx.math.time.TimeDomain;
import workshop.vfx.math.time.Timing;
import workshop.vfx.paper.core.DemoStyleCatalog;
import workshop.vfx.scene.*;

/** Immutable M1–M6 geometry, built once per enabled session and shared by every aura. */
public final class AuraRecipe {
    private AuraRecipe() { }

    private static Layer ring(String name, double radius, double height, int points, int period,
                              double turns, StyleKey style) {
        Shape shape = Shape.of(Sampler.uniformParameter(Spiral.circle(radius), points,
            Endpoints.EXCLUDE_END, new SampleLimits(36, 256)));
        Motion motion = Motion.fixed(Pose.ofTranslation(new Vec3(0, height, 0)))
            .then(Motion.spin(Vec3.Y, turns, 0, Timing.loop(period), Easing.LINEAR));
        return new Layer(name, Timing.loop(period), Visibility.ACTIVE_ONLY, Reveal.ALL, motion, shape, style);
    }

    private static EffectScene scene(int orbit, int halo, int lifetime) {
        List<Layer> layers = new ArrayList<>();
        layers.add(ring("orbit", 0.9, 1.0, orbit, 60, 1, DemoStyleCatalog.END_ROD));
        if (halo > 0) layers.add(ring("halo", 0.45, 2.15, halo, 80, -1, M8StyleCatalog.CYAN_HALO));
        return EffectScene.of(TimeDomain.GAME, lifetime, layers, new SceneLimits(2, 36));
    }

    public static EffectRecipe create(int lifetime) {
        if (lifetime < 20 || lifetime > 12000) throw new IllegalArgumentException("Aura lifetime outside approved bounds");
        return EffectRecipe.of("bedwenton-m8-aura", EffectPriority.NORMAL,
            Map.of(QualityTier.SIMPLE, scene(12, 0, lifetime),
                QualityTier.BALANCED, scene(18, 9, lifetime), QualityTier.HEAVY, scene(24, 12, lifetime)),
            Map.of("orbit", LayerRole.ESSENTIAL, "halo", LayerRole.STANDARD));
    }
}
