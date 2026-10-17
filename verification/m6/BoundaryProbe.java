package workshop.vfx.delivery;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Quat;
import workshop.vfx.math.core.Vec3;
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

/** Additional public-API boundary checks; independent of the supplied JUnit helpers and fixture oracle. */
public final class BoundaryProbe {
    private static final StyleKey DOT = StyleKey.of("probe:dot");
    private static final TimePoint NOW = TimePoint.gameTick(0);
    private static final Pose FAIL = new Pose(new Vec3(Double.MAX_VALUE, 0, 0), Quat.IDENTITY, Double.MAX_VALUE);

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static EffectRecipe recipe(String name, int n, EffectPriority priority) {
        Shape shape = Shape.points(java.util.Collections.nCopies(n, Vec3.X));
        Layer layer = new Layer("main", Timing.once(10), Visibility.ACTIVE_ONLY, Reveal.ALL, Motion.IDENTITY, shape, DOT);
        EffectScene scene = EffectScene.of(TimeDomain.GAME, 10, List.of(layer), new SceneLimits(1, 1 << 20));
        return EffectRecipe.of(name, priority, Map.of(QualityTier.HEAVY, scene), Map.of("main", LayerRole.ESSENTIAL));
    }

    private static DeliveryRequest request(long id, EffectRecipe recipe, QualityTier tier, Pose pose, long local,
            long ordinal, long... viewers) {
        return DeliveryRequest.of(id, recipe, tier, Integer.MAX_VALUE, pose,
                new Playback(NOW, local, 1, true), NOW, ordinal, viewers);
    }

    private static DeliveryPolicy policy(long generation, long reserveG, long placements, long reserveP, int cap) {
        return new DeliveryPolicy(65536, 65536, 1L << 24, generation, reserveG, placements, reserveP,
                1L << 50, 1L << 50, cap, cap, cap);
    }

    private static EffectDelivery run(DeliveryRequest request, Set<StyleKey> styles, DeliveryPolicy policy) {
        return DeliveryPlanner.plan(Long.MIN_VALUE, List.of(request), styles, policy).deliveries().getFirst();
    }

    private static String signature(DeliveryPlan plan) {
        StringBuilder s = new StringBuilder();
        for (EffectDelivery d : plan.deliveries()) {
            s.append(d.requestId()).append(':').append(d.allocationRank()).append(':').append(d.outcome());
            for (int b = 0; b < d.orderedBatchCount(); b++) {
                for (int j = 0; j < d.kept(b); j++) s.append('/').append(d.selectedIndex(b, j));
            }
            for (int r = 0; r < d.recipientCount(); r++) s.append('@').append(d.recipient(r)).append('=').append(d.recipientPoints(r));
        }
        return s.toString();
    }

    public static void main(String[] args) throws Exception {
        EffectRecipe normal = recipe("normal", 4, EffectPriority.NORMAL);
        DeliveryPolicy roomy = policy(1L << 50, 0, 1L << 50, 0, 1 << 20);
        DeliveryRequest q = request(Long.MIN_VALUE, normal, QualityTier.HEAVY, Pose.IDENTITY, 1, Long.MAX_VALUE, 0, Long.MAX_VALUE);
        EnumSet<Outcome> observed = EnumSet.noneOf(Outcome.class);
        EffectDelivery full = run(q, Set.of(DOT), roomy);
        check(full.outcome() == Outcome.DELIVERED && full.placements() == 8, "huge ids/budgets and exact full cost");
        observed.add(full.outcome());
        check(run(q, Set.of(DOT), policy(4, 0, 8, 0, 4)).placements() == 8, "exact generation/placement boundary");
        check(run(q, Set.of(DOT), policy(4, 0, 7, 0, 4)).keptPoints() == 3, "one below the placement boundary");
        EffectDelivery thinned = run(q, Set.of(DOT), policy(4, 0, 8, 0, 3));
        check(thinned.outcome() == Outcome.THINNED, "cap thinning"); observed.add(thinned.outcome());
        DeliveryRequest empty = request(1, normal, QualityTier.HEAVY, FAIL, 1, 0);
        EffectDelivery noViewers = run(empty, Set.of(), roomy);
        check(noViewers.outcome() == Outcome.SKIPPED_NO_VIEWERS && noViewers.frame() == null, "empty recipients");
        observed.add(noViewers.outcome());
        EffectDelivery noTier = run(request(1, normal, QualityTier.SIMPLE, FAIL, 1, 0, 1), Set.of(), roomy);
        check(noTier.outcome() == Outcome.SKIPPED_NO_ELIGIBLE_TIER && noTier.frame() == null
                && noTier.tier() == null && noTier.reservedGeneratedPoints() == 0, "strict ceiling before style/evaluation");
        observed.add(noTier.outcome());
        EffectDelivery unsupported = run(q, Set.of(), roomy);
        check(unsupported.outcome() == Outcome.SKIPPED_UNSUPPORTED, "unsupported"); observed.add(unsupported.outcome());
        EffectDelivery denied = run(q, Set.of(DOT), policy(3, 0, 8, 0, 4));
        check(denied.outcome() == Outcome.SKIPPED_GENERATION_BUDGET && denied.frame() == null, "preflight rejection");
        observed.add(denied.outcome());
        EffectDelivery inactive = run(request(1, normal, QualityTier.HEAVY, FAIL, -1, 0, 1), Set.of(DOT), roomy);
        check(inactive.outcome() == Outcome.SKIPPED_INACTIVE && inactive.frame() != null, "inactive frame retained");
        observed.add(inactive.outcome());
        EffectDelivery quota = run(q, Set.of(DOT), policy(4, 0, 0, 0, 4));
        check(quota.outcome() == Outcome.SKIPPED_DELIVERY_BUDGET && quota.frame() != null, "zero placement quota");
        observed.add(quota.outcome());
        EffectDelivery failed = run(request(1, normal, QualityTier.HEAVY, FAIL, 1, 0, 1), Set.of(DOT), roomy);
        check(failed.outcome() == Outcome.FAILED_EVALUATION && failed.frame() == null
                && failed.reservedGeneratedPoints() == 4, "evaluation failure confined, reservation retained");
        observed.add(failed.outcome());
        check(observed.size() == 9, "all nine outcomes");
        EffectRecipe action = recipe("action", 70, EffectPriority.ACTION), ambient = recipe("ambient", 31, EffectPriority.AMBIENT);
        DeliveryPlan reserves = DeliveryPlanner.plan(0, List.of(
                request(2, ambient, QualityTier.HEAVY, Pose.IDENTITY, 1, 0, 1),
                request(1, action, QualityTier.HEAVY, Pose.IDENTITY, 1, 0, 1)), Set.of(DOT), policy(100, 30, 1000, 0, 100));
        check(reserves.reservedGeneratedPoints() == 70 && reserves.deliveries().getFirst().outcome() == Outcome.SKIPPED_GENERATION_BUDGET,
                "reserve protects capacity, cannot fit indivisible 31 in remaining 30");
        EffectRecipe a95 = recipe("a95", 95, EffectPriority.ACTION), a10 = recipe("a10", 10, EffectPriority.AMBIENT);
        DeliveryPlan placementReserve = DeliveryPlanner.plan(0, List.of(
                request(1, a95, QualityTier.HEAVY, Pose.IDENTITY, 1, 0, 1),
                request(2, a10, QualityTier.HEAVY, Pose.IDENTITY, 1, 0, 2, 3, 4)), Set.of(DOT), policy(1000, 0, 100, 5, 100));
        check(placementReserve.placements() == 98 && placementReserve.deliveries().get(1).placements() == 3,
                "multi-recipient cost uses 3 of protected 5");
        List<DeliveryRequest> hostile = new AbstractList<>() {
            public int size() { return Integer.MAX_VALUE; }
            public DeliveryRequest get(int index) { throw new AssertionError("list read before rejection"); }
        };
        try { DeliveryPlanner.plan(0, hostile, Set.of(DOT), roomy); throw new AssertionError("oversized list accepted"); }
        catch (IllegalArgumentException expected) { }
        try { DeliveryPlanner.plan(0, List.of(q, q), Set.of(DOT), roomy); throw new AssertionError("duplicate id accepted"); }
        catch (IllegalArgumentException expected) { }
        List<DeliveryRequest> repeated = new ArrayList<>();
        for (int i = 0; i < 24; i++) repeated.add(request(i, normal, QualityTier.HEAVY, Pose.IDENTITY, 1, i, 0, 5, Long.MAX_VALUE));
        DeliveryPolicy tight = policy(60, 0, 101, 0, 3);
        String expected = signature(DeliveryPlanner.plan(Long.MAX_VALUE, repeated, Set.of(DOT), tight));
        for (int i = 0; i < 100; i++) check(expected.equals(signature(DeliveryPlanner.plan(Long.MAX_VALUE, repeated, Set.of(DOT), tight))), "repetition");
        String[] results = new String[8]; Thread[] threads = new Thread[8];
        for (int i = 0; i < threads.length; i++) {
            final int index = i;
            threads[i] = new Thread(() -> results[index] = signature(DeliveryPlanner.plan(Long.MAX_VALUE, repeated, Set.of(DOT), tight)));
            threads[i].start();
        }
        for (Thread thread : threads) thread.join();
        for (String result : results) check(expected.equals(result), "eight-thread determinism");
        System.out.println("PASS: nine outcomes; strict ceilings; exact/zero/huge budgets; both reserve counterexamples; validation before copy; 100 repetitions and 8 threads.");
    }
}
