package workshop.vfx.delivery;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import workshop.vfx.scene.EffectScene;
import workshop.vfx.scene.StyleKey;

/**
 * M6 R1 performance probe (review scratch; run against the throwaway reference implementation, which is not delivered).
 * Compile together with src/test/java/workshop/vfx/delivery/M6TestSupport.java (it needs JUnit on the classpath only
 * for that helper's imports). Reports indicative plan() cost per call, the share spent in M5 evaluation, and bytes
 * allocated per call, for workloads at and near the contract ceilings. No number here is a server budget (DM6-11).
 */
public class PerformanceProbe {
    static final com.sun.management.ThreadMXBean MX = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    static long sink;

    record Workload(String name, List<DeliveryRequest> requests, Set<StyleKey> styles, DeliveryPolicy policy, int rounds) { }

    static void measure(Workload w) {
        for (int warm = 0; warm < 3; warm++) {
            for (int i = 0; i < w.rounds; i++) {
                sink += DeliveryPlanner.plan(i, w.requests, w.styles, w.policy).placements();
            }
        }
        long b0 = MX.getCurrentThreadAllocatedBytes(), t0 = System.nanoTime();
        DeliveryPlan last = null;
        for (int i = 0; i < w.rounds; i++) {
            last = DeliveryPlanner.plan(i, w.requests, w.styles, w.policy);
            sink += last.placements();
        }
        long ns = System.nanoTime() - t0, bytes = MX.getCurrentThreadAllocatedBytes() - b0;
        // the M5 evaluations alone, for the same admitted requests and tiers
        long e0 = System.nanoTime();
        for (int i = 0; i < w.rounds; i++) {
            for (int k = 0; k < w.requests.size(); k++) {
                EffectDelivery d = last.deliveries().get(k);
                if (d.frame() != null) {
                    DeliveryRequest q = w.requests.get(k);
                    sink += q.recipe().variant(d.tier()).evaluate(q.anchor(), q.playback(), q.now()).pointCount();
                }
            }
        }
        long evalNs = System.nanoTime() - e0;
        long entries = 0;
        for (DeliveryRequest q : w.requests) {
            entries += q.viewerCount();
        }
        System.out.printf("%-44s requests %6d, viewer entries %8d, generated %8d, placements %9d: %11.0f ns/plan (M5 evaluation %11.0f ns), %6.1f ns/viewer entry, %11.0f bytes/plan%n",
                w.name, w.requests.size(), entries, last.sourcePoints(), last.placements(), ns / (double) w.rounds, evalNs / (double) w.rounds,
                (ns - evalNs) / (double) w.rounds / Math.max(1, entries), bytes / (double) w.rounds);
    }

    static EffectRecipe recipe(String name, EffectPriority p, int simple, int balanced, int heavy) {
        Map<QualityTier, EffectScene> v = new EnumMap<>(QualityTier.class);
        v.put(QualityTier.SIMPLE, M6TestSupport.scene(M6TestSupport.layer("core", simple / 2, "t:a"), M6TestSupport.layer("trail", simple - simple / 2, "t:b")));
        v.put(QualityTier.BALANCED, M6TestSupport.scene(M6TestSupport.layer("core", balanced / 2, "t:a"), M6TestSupport.layer("trail", balanced - balanced / 2, "t:b")));
        v.put(QualityTier.HEAVY, M6TestSupport.scene(M6TestSupport.layer("core", heavy / 2, "t:a"), M6TestSupport.layer("trail", heavy - heavy / 2, "t:b")));
        return EffectRecipe.of(name, p, v, Map.of("core", LayerRole.ESSENTIAL, "trail", LayerRole.DECORATIVE));
    }

    static List<DeliveryRequest> requests(int n, int viewers, int viewerPool, EffectRecipe... recipes) {
        List<DeliveryRequest> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            long[] v = new long[viewers];
            for (int j = 0; j < viewers; j++) {
                v[j] = j + (long) (i % Math.max(1, viewerPool - viewers + 1)); // a sliding window over the viewer pool
            }
            out.add(M6TestSupport.request(i, recipes[i % recipes.length], QualityTier.HEAVY, i % 4, i, 1, v));
        }
        return out;
    }

    public static void main(String[] args) {
        Set<StyleKey> st = M6TestSupport.styles("t:a", "t:b");
        EffectRecipe action = recipe("action", EffectPriority.ACTION, 16, 64, 256);
        EffectRecipe normal = recipe("normal", EffectPriority.NORMAL, 8, 32, 128);
        EffectRecipe ambient = recipe("ambient", EffectPriority.AMBIENT, 4, 12, 24);
        DeliveryPolicy roomy = new DeliveryPolicy(1 << 16, 1 << 16, 1 << 24, 1L << 40, 0, 1L << 40, 0, 1L << 40, 1L << 40, 1 << 20, 1 << 20, 1 << 20);
        DeliveryPolicy tight = new DeliveryPolicy(1 << 16, 1 << 16, 1 << 24, 20_000, 2_000, 60_000, 6_000, 25_000, 400, 32, 96, 192);
        List<Workload> ws = List.of(
                new Workload("small: 16 effects x 8 viewers, roomy", requests(16, 8, 64, action, normal, ambient), st, roomy, 20_000),
                new Workload("small: 16 effects x 8 viewers, tight", requests(16, 8, 64, action, normal, ambient), st, tight, 20_000),
                new Workload("busy: 256 effects x 32 viewers, roomy", requests(256, 32, 200, action, normal, ambient), st, roomy, 500),
                new Workload("busy: 256 effects x 32 viewers, tight", requests(256, 32, 200, action, normal, ambient), st, tight, 500),
                new Workload("crowd: 64 effects x 1024 viewers, tight", requests(64, 1024, 2048, action, normal, ambient), st, tight, 200),
                new Workload("ceiling: 65536 effects x 1 viewer, tight", requests(1 << 16, 1, 1 << 16, ambient), st, tight, 5),
                new Workload("ceiling: 256 effects x 4096 viewers, tight", requests(256, 4096, 8192, ambient), st, tight, 5));
        for (Workload w : ws) {
            measure(w);
        }
        EffectRecipe big = EffectRecipe.of("big", EffectPriority.AMBIENT,
                Map.of(QualityTier.HEAVY, M6TestSupport.scene(M6TestSupport.layer("all", 1 << 20, "t:a"))), Map.of());
        DeliveryPolicy full = new DeliveryPolicy(1, 4, 4, 1 << 20, 0, 1L << 40, 0, 1L << 40, 1L << 40, 1 << 20, 1 << 20, 1 << 20);
        measure(new Workload("full frame: 2^20 points, kept 2^20 x 4 viewers", List.of(M6TestSupport.request(1, big, QualityTier.HEAVY, 0, 0, 1, 1, 2, 3, 4)), st, full, 5));
        System.out.println("sink " + (sink & 1));
    }
}
