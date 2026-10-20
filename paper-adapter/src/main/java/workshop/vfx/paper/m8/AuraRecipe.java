package workshop.vfx.paper.m8;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import workshop.vfx.delivery.*;
import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Quat;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.curve.CubicBezier;
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

    private static Layer outline(int points) {
        Shape shape = Shape.of(Sampler.uniformParameter(Spiral.circle(1.05), points,
            Endpoints.EXCLUDE_END, new SampleLimits(44, 256)));
        // The existing circle is XZ. Rotate +90 degrees about X into XY, then place behind the torso.
        double half = Math.PI / 4;
        Pose placement = new Pose(new Vec3(0, 1.25, -0.85),
            new Quat(StrictMath.cos(half), StrictMath.sin(half), 0, 0), 1);
        return new Layer("orbit", Timing.loop(80), Visibility.ACTIVE_ONLY, Reveal.ALL,
            Motion.fixed(placement), shape, DemoStyleCatalog.END_ROD);
    }

    private static Layer stroke(String name, CubicBezier curve, int points, boolean skipStart) {
        // The second stroke starts after the shared junction: never emit it twice in one frame.
        Shape shape = Shape.of(Sampler.uniformParameter(curve, skipStart ? 1.0 / points : 0, 1, points,
            Endpoints.INCLUDE_END, new SampleLimits(44, 256)));
        // Scale only the white internal geometry by the same radius ratio; keep its center fixed.
        Motion motion = Motion.fixed(new Pose(new Vec3(0, 1.25, -0.85), Quat.IDENTITY, 1.05 / 0.78))
            .then(Motion.spin(Vec3.Z, 1, 0, Timing.loop(80), Easing.LINEAR));
        return new Layer(name, Timing.loop(80), Visibility.ACTIVE_ONLY, Reveal.ALL, motion, shape, DemoStyleCatalog.END_ROD);
    }

    private static Layer halo(int points) {
        // K1.1 cyan geometry, payload and clock are deliberately unchanged.
        Shape shape = Shape.of(Sampler.uniformParameter(Spiral.circle(0.45), points,
            Endpoints.EXCLUDE_END, new SampleLimits(44, 256)));
        Motion motion = Motion.fixed(Pose.ofTranslation(new Vec3(0, 2.15, 0)))
            .then(Motion.spin(Vec3.Y, -1, 0, Timing.loop(80), Easing.LINEAR));
        return new Layer("halo", Timing.loop(80), Visibility.ACTIVE_ONLY, Reveal.ALL, motion, shape, M8StyleCatalog.CYAN_HALO);
    }

    private static EffectScene scene(int outer, int innerPerStroke, int halo, int lifetime) {
        List<Layer> layers = new ArrayList<>();
        layers.add(outline(outer));
        // Two joined, tangent-aligned cubic sweeps form an asymmetric flowing S inside the outline.
        // Controls fit radius 0.665 before scaling; the proportional scale keeps them inside radius 1.05.
        layers.add(stroke("stroke-a", new CubicBezier(new Vec3(-0.62, -0.24, 0), new Vec3(-0.18, -0.38, 0),
            new Vec3(-0.06, -0.16, 0), new Vec3(-0.08, 0.02, 0)), innerPerStroke, false));
        layers.add(stroke("stroke-b", new CubicBezier(new Vec3(-0.08, 0.02, 0), new Vec3(-0.12, 0.38, 0),
            new Vec3(0.28, 0.32, 0), new Vec3(0.56, 0.30, 0)), innerPerStroke, true));
        if (halo > 0) layers.add(halo(halo));
        return EffectScene.of(TimeDomain.GAME, lifetime, layers, new SceneLimits(4, 44));
    }

    public static EffectRecipe create(int lifetime) {
        if (lifetime < 20 || lifetime > 12000) throw new IllegalArgumentException("Aura lifetime outside approved bounds");
        return EffectRecipe.of("bedwenton-m8-aura", EffectPriority.NORMAL,
            Map.of(QualityTier.SIMPLE, scene(12, 4, 0, lifetime),
                QualityTier.BALANCED, scene(16, 6, 6, lifetime), QualityTier.HEAVY, scene(20, 8, 8, lifetime)),
            Map.of("orbit", LayerRole.ESSENTIAL, "stroke-a", LayerRole.STANDARD,
                "stroke-b", LayerRole.STANDARD, "halo", LayerRole.STANDARD));
    }
}
