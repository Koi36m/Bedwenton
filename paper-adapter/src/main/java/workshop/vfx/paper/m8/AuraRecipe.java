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

    private static Layer rearArc(int points, double degrees) {
        // Local -Z is behind the player. Include both ends of a constant-radius planar arc.
        double sweep = degrees / 360;
        Shape shape = Shape.of(Sampler.uniformParameter(Spiral.arc(0.9, 0.25 - sweep / 2, sweep), points,
            Endpoints.INCLUDE_END, new SampleLimits(36, 256)));
        // A full revolution would carry the crescent into the front. Sway +/-8 degrees instead,
        // with zero velocity at each turnaround and the original deterministic 60-tick period.
        Motion motion = Motion.fixed(Pose.ofTranslation(new Vec3(0, 1.0, 0)))
            .then(Motion.spin(Vec3.Y, 16.0 / 360, -8.0 / 360, Timing.pingPong(30), Easing.SMOOTHSTEP));
        return new Layer("orbit", Timing.loop(60), Visibility.ACTIVE_ONLY, Reveal.ALL, motion, shape, DemoStyleCatalog.END_ROD);
    }

    private static Layer ring(String name, double radius, double height, int points, int period,
                              double turns, StyleKey style) {
        Shape shape = Shape.of(Sampler.uniformParameter(Spiral.circle(radius), points,
            Endpoints.EXCLUDE_END, new SampleLimits(36, 256)));
        Motion motion = Motion.fixed(Pose.ofTranslation(new Vec3(0, height, 0)))
            .then(Motion.spin(Vec3.Y, turns, 0, Timing.loop(period), Easing.LINEAR));
        return new Layer(name, Timing.loop(period), Visibility.ACTIVE_ONLY, Reveal.ALL, motion, shape, style);
    }

    private static EffectScene scene(int orbit, double arcDegrees, int halo, int lifetime) {
        List<Layer> layers = new ArrayList<>();
        layers.add(rearArc(orbit, arcDegrees));
        if (halo > 0) layers.add(ring("halo", 0.45, 2.15, halo, 80, -1, M8StyleCatalog.CYAN_HALO));
        return EffectScene.of(TimeDomain.GAME, lifetime, layers, new SceneLimits(2, 24));
    }

    public static EffectRecipe create(int lifetime) {
        if (lifetime < 20 || lifetime > 12000) throw new IllegalArgumentException("Aura lifetime outside approved bounds");
        return EffectRecipe.of("bedwenton-m8-aura", EffectPriority.NORMAL,
            Map.of(QualityTier.SIMPLE, scene(8, 120, 0, lifetime),
                QualityTier.BALANCED, scene(12, 160, 6, lifetime), QualityTier.HEAVY, scene(16, 200, 8, lifetime)),
            Map.of("orbit", LayerRole.ESSENTIAL, "halo", LayerRole.STANDARD));
    }
}
