import java.util.*;
import workshop.vfx.delivery.*;
import workshop.vfx.math.core.*;
import workshop.vfx.math.curve.Spiral;
import workshop.vfx.math.sample.*;
import workshop.vfx.math.time.*;
import workshop.vfx.scene.*;

/** M7 R1 probe: builds the demo recipe of contract section 5 with the accepted engine and plans it under the default policy. */
public class DemoRecipeProbe {
    static final SampleLimits SL = new SampleLimits(1 << 20, 1 << 20);
    static Layer layer(String name, double r, int n, String style) {
        Shape s = Shape.of(Sampler.uniformParameter(Spiral.circle(r), n, Endpoints.EXCLUDE_END, SL));
        return new Layer(name, Timing.loop(40), Visibility.ACTIVE_ONLY, Reveal.ALL, Motion.IDENTITY, s, StyleKey.of(style));
    }
    static EffectScene tier(int ring, int accent) {
        return EffectScene.of(TimeDomain.GAME, 600, List.of(layer("ring", 2.0, ring, "bedwenton:demo_end_rod"),
            layer("accent", 2.25, accent, "bedwenton:demo_dust_red")), new SceneLimits(2, 72));
    }
    public static void main(String[] a) {
        EffectRecipe recipe = EffectRecipe.of("bedwenton-demo-ring", EffectPriority.ACTION,
            Map.of(QualityTier.SIMPLE, tier(16, 8), QualityTier.BALANCED, tier(32, 16), QualityTier.HEAVY, tier(48, 24)),
            Map.of("ring", LayerRole.ESSENTIAL, "accent", LayerRole.DECORATIVE));
        DeliveryPolicy pol = new DeliveryPolicy(4, 4, 16, 1152, 0, 2048, 0, 2048, 256, 24, 48, 72);
        Set<StyleKey> styles = Set.of(StyleKey.of("bedwenton:demo_end_rod"), StyleKey.of("bedwenton:demo_dust_red"));
        long start = 1000, now = 1017;
        Pose anchor = Pose.ofTranslation(new Vec3(100.5, 64.1, -20.5));
        for (QualityTier t : QualityTier.values()) {
            DeliveryRequest q = DeliveryRequest.of(1, recipe, t, 0, anchor, Playback.start(TimePoint.gameTick(start)),
                TimePoint.gameTick(now), now - start, new long[] {0, 1});
            DeliveryPlan p = DeliveryPlanner.plan(now, List.of(q), styles, pol);
            EffectDelivery d = p.deliveries().get(0);
            System.out.printf("%-8s outcome=%s tier=%s reserved=%d source=%d kept=%d placements=%d recipients=%d batches=%d%n",
                t, d.outcome(), d.tier(), d.reservedGeneratedPoints(), d.sourcePoints(), d.keptPoints(), d.placements(), d.recipientCount(), d.orderedBatchCount());
            FrameBatch b = d.frame().batches().get(d.orderedBatch(0));
            System.out.printf("         first ring point (%.4f, %.4f, %.4f), style %s%n", b.x(d.selectedIndex(0, 0)), b.y(d.selectedIndex(0, 0)), b.z(d.selectedIndex(0, 0)), b.style());
        }
        // ceiling: 16 rings x 16 viewers at HEAVY under the hard ceilings
        DeliveryPolicy ceil = new DeliveryPolicy(16, 16, 256, 1152, 0, 4096, 0, 4096, 1024, 72, 72, 72);
        List<DeliveryRequest> reqs = new ArrayList<>();
        long[] v = new long[16]; for (int i = 0; i < 16; i++) v[i] = i;
        for (int i = 0; i < 16; i++) reqs.add(DeliveryRequest.of(i, recipe, QualityTier.HEAVY, 0, anchor, Playback.start(TimePoint.gameTick(start)), TimePoint.gameTick(now), now - start, v));
        DeliveryPlan p = DeliveryPlanner.plan(now, reqs, styles, ceil);
        System.out.printf("ceiling: delivered=%d thinned=%d skippedBudget=%d generated=%d placements=%d (<= 4096)%n",
            p.count(Outcome.DELIVERED), p.count(Outcome.THINNED), p.count(Outcome.SKIPPED_DELIVERY_BUDGET), p.sourcePoints(), p.placements());
    }
}
