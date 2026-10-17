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

/**
 * M7 R2 proposed test support: the demo recipe of contract section 5 built with the accepted engine, ring requests,
 * a scripted viewer-facts table behind the owner-aware rule, and a recording, optionally failing particle sink.
 */
final class DemoFixture {
    static final StyleKey END_ROD = StyleKey.of("bedwenton:demo_end_rod");
    static final StyleKey DUST = StyleKey.of("bedwenton:demo_dust_red");
    static final Set<StyleKey> CATALOGUE = Set.of(END_ROD, DUST);
    static final EffectRecipe RECIPE = recipe();
    static final double RADIUS = 32.0;

    private DemoFixture() { }

    private static Layer layer(String name, double r, int n, StyleKey style) {
        Shape s = Shape.of(Sampler.uniformParameter(Spiral.circle(r), n, Endpoints.EXCLUDE_END, new SampleLimits(1 << 20, 1 << 20)));
        return new Layer(name, Timing.loop(40), Visibility.ACTIVE_ONLY, Reveal.ALL, Motion.IDENTITY, s, style);
    }

    private static EffectScene tier(int ring, int accent) {
        return EffectScene.of(TimeDomain.GAME, 600, List.of(layer("ring", 2.0, ring, END_ROD), layer("accent", 2.25, accent, DUST)),
                new SceneLimits(2, 72));
    }

    private static EffectRecipe recipe() {
        return EffectRecipe.of("bedwenton-demo-ring", EffectPriority.ACTION,
                Map.of(QualityTier.SIMPLE, tier(16, 8), QualityTier.BALANCED, tier(32, 16), QualityTier.HEAVY, tier(48, 24)),
                Map.of("ring", LayerRole.ESSENTIAL, "accent", LayerRole.DECORATIVE));
    }

    /** A ring request started at tick 0 and planned at {@code tick}; viewers must already be strictly increasing. */
    static DeliveryRequest ring(long id, QualityTier tier, int world, double ax, long tick, long... viewers) {
        return DeliveryRequest.of(id, RECIPE, tier, world, Pose.ofTranslation(new Vec3(ax, 64.1, 0.5)),
                Playback.start(TimePoint.gameTick(0)), TimePoint.gameTick(tick), tick, viewers);
    }

    static RequestContext context(long id, long owner, int world, double ax) {
        return new RequestContext(id, owner, world, ax, 64.1, 0.5);
    }

    static DeliveryPolicy roomy() {
        return new DeliveryPolicy(16, 16, 256, 1152, 0, 4096, 0, 4096, 1024, 72, 72, 72);
    }

    static final PointValidator ANY_POINT = (w, x, y, z) -> Optional.empty();

    /** Viewer facts by viewer id; all viewers stand at the origin of world 0 unless changed. */
    static final class Viewers {
        final Map<Long, ViewerFacts> facts = new HashMap<>();
        final RecipientRule rule = new OwnerAwareRecipientRule(RADIUS);

        Viewers put(long viewer, boolean use, boolean view) {
            facts.put(viewer, new ViewerFacts(true, true, 0, 0.5, 64.0, 0.5, use, view));
            return this;
        }

        RecipientGate gate() {
            return (context, viewer) -> rule.decide(context, viewer, facts.get(viewer));
        }
    }

    /** One recorded send. */
    record Send(long viewer, int world, StyleKey style, double x, double y, double z) { }

    /** Records sends; throws for a style according to a script of booleans per call of that style (true = fail). */
    static final class Sink implements ParticleSink {
        final List<Send> sends = new ArrayList<>();
        final Map<StyleKey, List<Boolean>> script = new HashMap<>();
        final Map<StyleKey, Integer> callsByStyle = new HashMap<>();
        StyleKey alwaysFail;

        @Override
        public void send(long viewer, int world, StyleKey style, double x, double y, double z) {
            int n = callsByStyle.merge(style, 1, Integer::sum) - 1;
            List<Boolean> s = script.get(style);
            if (style.equals(alwaysFail) || (s != null && n < s.size() && s.get(n))) {
                throw new IllegalArgumentException("scripted failure");
            }
            sends.add(new Send(viewer, world, style, x, y, z));
        }

        long count(long viewer) {
            return sends.stream().filter(s -> s.viewer() == viewer).count();
        }

        long count(StyleKey style) {
            return sends.stream().filter(s -> s.style().equals(style)).count();
        }
    }
}
