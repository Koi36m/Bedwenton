package workshop.vfx.paper.core;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import workshop.vfx.delivery.DeliveryPolicy;
import workshop.vfx.delivery.DeliveryRequest;
import workshop.vfx.delivery.EffectPriority;
import workshop.vfx.delivery.EffectRecipe;
import workshop.vfx.delivery.LayerRole;
import workshop.vfx.delivery.QualityTier;
import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.curve.Spiral;
import workshop.vfx.math.sample.Endpoints;
import workshop.vfx.math.sample.SampleLimits;
import workshop.vfx.math.sample.Sampler;
import workshop.vfx.math.time.Playback;
import workshop.vfx.math.time.TimeDomain;
import workshop.vfx.math.time.TimePoint;
import workshop.vfx.math.time.Timing;
import workshop.vfx.scene.EffectScene;
import workshop.vfx.scene.Layer;
import workshop.vfx.scene.Motion;
import workshop.vfx.scene.Reveal;
import workshop.vfx.scene.SceneLimits;
import workshop.vfx.scene.Shape;
import workshop.vfx.scene.StyleKey;
import workshop.vfx.scene.Visibility;

/** The approved static, two-layer M5 demo; no geometry is improvised in Paper. */
public final class DemoRecipe {
    private DemoRecipe() { }
    private static Layer layer(String name, double radius, int count, StyleKey style) {
        Shape shape = Shape.of(Sampler.uniformParameter(Spiral.circle(radius), count, Endpoints.EXCLUDE_END,
                new SampleLimits(72, 256)));
        return new Layer(name, Timing.loop(40), Visibility.ACTIVE_ONLY, Reveal.ALL, Motion.IDENTITY, shape, style);
    }
    private static EffectScene tier(int ring, int accent, int lifetime) {
        return EffectScene.of(TimeDomain.GAME, lifetime, List.of(
                layer("ring", 2.0, ring, DemoStyleCatalog.END_ROD),
                layer("accent", 2.25, accent, DemoStyleCatalog.DUST)), new SceneLimits(2,72));
    }
    public static EffectRecipe create(int lifetime) {
        if (lifetime < 20 || lifetime > 6000) throw new IllegalArgumentException("Ring lifetime outside hard ceilings");
        return EffectRecipe.of("bedwenton-demo-ring", EffectPriority.ACTION,
            Map.of(QualityTier.SIMPLE,tier(16,8,lifetime), QualityTier.BALANCED,tier(32,16,lifetime),
                   QualityTier.HEAVY,tier(48,24,lifetime)),
            Map.of("ring",LayerRole.ESSENTIAL,"accent",LayerRole.DECORATIVE));
    }
}
