package workshop.vfx.delivery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import workshop.vfx.math.time.TimingSample;
import workshop.vfx.scene.EffectFrame;
import workshop.vfx.scene.EffectScene;
import workshop.vfx.scene.FrameBatch;
import workshop.vfx.scene.Layer;
import workshop.vfx.scene.StyleKey;

/** Stateless integer-only admission, evaluation and delivery allocation. */
public final class DeliveryPlanner {
    private DeliveryPlanner() { }

    public static DeliveryPlan plan(long tickOrdinal, List<DeliveryRequest> requests,
            Set<StyleKey> supportedStyles, DeliveryPolicy policy) {
        DeliveryChecks.nonnull(requests, "DeliveryPlanner.plan: requests");
        DeliveryChecks.nonnull(supportedStyles, "DeliveryPlanner.plan: supportedStyles");
        DeliveryChecks.nonnull(policy, "DeliveryPlanner.plan: policy");
        if (requests.size() > policy.maxRequests()) {
            throw new IllegalArgumentException("DeliveryPlanner.plan: too many requests");
        }
        List<DeliveryRequest> input = new ArrayList<>(requests);
        Set<StyleKey> styles = new HashSet<>(supportedStyles);
        if (input.contains(null) || styles.contains(null)) {
            throw new IllegalArgumentException("DeliveryPlanner.plan: null input element");
        }
        Set<Long> ids = new HashSet<>();
        for (DeliveryRequest q : input) {
            if (!ids.add(q.id())) {
                throw new IllegalArgumentException("DeliveryPlanner.plan: request ids must be unique");
            }
        }
        long viewerEntries = 0;
        for (DeliveryRequest q : input) {
            viewerEntries += q.viewerCount();
            if (q.viewerCount() > policy.maxViewersPerRequest() || viewerEntries > policy.maxViewerEntries()) {
                throw new IllegalArgumentException("DeliveryPlanner.plan: viewer limits exceeded");
            }
        }
        Work[] inOrder = new Work[input.size()];
        for (int i = 0; i < inOrder.length; i++) {
            inOrder[i] = new Work(input.get(i));
        }
        List<Work> order = allocationOrder(tickOrdinal, inOrder);
        long usedGeneration = 0;
        // Complete admission before any M5 evaluation. Failed evaluations keep their reservation.
        for (Work w : order) {
            DeliveryRequest q = w.request;
            if (q.viewerCount() == 0) {
                w.outcome = Outcome.SKIPPED_NO_VIEWERS;
                continue;
            }
            long available = available(policy.generatedPointBudget(), policy.ambientGeneratedReserve(),
                    usedGeneration, q.recipe().priority());
            boolean eligible = false;
            boolean deliverable = false;
            QualityTier[] tiers = QualityTier.values();
            for (int t = q.tier().ordinal(); t >= 0; t--) {
                QualityTier tier = tiers[t];
                if (!q.recipe().tiers().contains(tier)) {
                    continue;
                }
                eligible = true;
                EffectScene scene = q.recipe().variant(tier);
                if (!deliverable(scene, styles)) {
                    continue;
                }
                deliverable = true;
                if (scene.maxPointsPerFrame() <= available) {
                    w.tier = tier;
                    w.scene = scene;
                    w.reserved = scene.maxPointsPerFrame();
                    usedGeneration += w.reserved;
                    break;
                }
            }
            if (w.tier == null) {
                w.outcome = !eligible ? Outcome.SKIPPED_NO_ELIGIBLE_TIER
                        : !deliverable ? Outcome.SKIPPED_UNSUPPORTED : Outcome.SKIPPED_GENERATION_BUDGET;
            }
        }
        for (Work w : order) {
            if (w.scene == null) {
                continue;
            }
            DeliveryRequest q = w.request;
            try {
                w.frame = w.scene.evaluate(q.anchor(), q.playback(), q.now());
            } catch (ArithmeticException failure) {
                w.outcome = Outcome.FAILED_EVALUATION;
                continue;
            }
            if (w.frame.stage() != TimingSample.Stage.ACTIVE || w.frame.isEmpty()) {
                w.outcome = Outcome.SKIPPED_INACTIVE;
            }
        }
        Map<Integer, Long> worldRemaining = new HashMap<>();
        Map<Long, Long> viewerRemaining = new HashMap<>();
        long usedPlacements = 0;
        for (Work w : order) {
            if (w.outcome != null) {
                continue;
            }
            DeliveryRequest q = w.request;
            List<Batch> supported = new ArrayList<>();
            long n = 0;
            long unsupported = 0;
            List<FrameBatch> batches = w.frame.batches();
            for (int i = 0; i < batches.size(); i++) {
                FrameBatch batch = batches.get(i);
                if (styles.contains(batch.style())) {
                    supported.add(new Batch(i, batch.layerIndex(), batch.size(), q.recipe().role(batch.layer())));
                    n += batch.size();
                } else {
                    unsupported += batch.size();
                }
            }
            w.unsupported = (int) unsupported;
            if (n == 0) {
                w.outcome = Outcome.SKIPPED_UNSUPPORTED;
                continue;
            }
            // Snapshot each viewer budget once: the binary search uses primitive arrays, without repeated boxing.
            long[] room = new long[q.viewerCount()];
            long maxViewer = 0;
            for (int v = 0; v < room.length; v++) {
                room[v] = viewerRemaining.getOrDefault(q.viewer(v), policy.viewerPlacementBudget());
                maxViewer = Math.max(maxViewer, room[v]);
            }
            int kmax = (int) Math.min(Math.min(n, policy.keptCap(w.tier)), maxViewer);
            long world = worldRemaining.getOrDefault(q.worldId(), policy.worldPlacementBudget());
            long budget = Math.min(available(policy.placementBudget(), policy.ambientPlacementReserve(),
                    usedPlacements, q.recipe().priority()), world);
            long lo = 0, hi = kmax;
            for (int step = 0; step < 21 && lo < hi; step++) {
                long mid = lo + (hi - lo + 1) / 2;
                if (cost(mid, room) <= budget) {
                    lo = mid;
                } else {
                    hi = mid - 1;
                }
            }
            int k = (int) lo;
            if (k == 0) {
                w.outcome = Outcome.SKIPPED_DELIVERY_BUDGET;
                continue;
            }
            split(w, supported, k);
            long recipientCount = 0;
            for (long remaining : room) {
                if (remaining > 0) {
                    recipientCount++;
                }
            }
            w.recipients = new long[(int) recipientCount];
            w.shares = new int[(int) recipientCount];
            int r = 0;
            boolean complete = recipientCount == q.viewerCount();
            long placements = 0;
            for (int v = 0; v < room.length; v++) {
                int share = (int) Math.min(k, room[v]);
                if (share == 0) {
                    continue;
                }
                long viewer = q.viewer(v);
                w.recipients[r] = viewer;
                w.shares[r] = share;
                r++;
                placements += share;
                complete &= share == k;
                viewerRemaining.put(viewer, room[v] - share);
            }
            worldRemaining.put(q.worldId(), world - placements);
            usedPlacements += placements;
            w.kept = k;
            w.placements = placements;
            w.outcome = k == n && unsupported == 0 && complete ? Outcome.DELIVERED : Outcome.THINNED;
        }
        List<EffectDelivery> result = new ArrayList<>(inOrder.length);
        for (Work w : inOrder) {
            result.add(new EffectDelivery(w.request.id(), w.rank, w.outcome, w.tier, w.frame, w.reserved,
                    w.unsupported, w.kept, w.placements, w.batchIndices, w.batchKept, w.batchSizes, w.offsets,
                    w.recipients, w.shares));
        }
        return new DeliveryPlan(tickOrdinal, result);
    }

    private static List<Work> allocationOrder(long ordinal, Work[] input) {
        List<Work> sorted = new ArrayList<>(Arrays.asList(input));
        sorted.sort((a, b) -> {
            int p = Integer.compare(a.request.recipe().priority().ordinal(), b.request.recipe().priority().ordinal());
            return p != 0 ? p : Long.compare(a.request.id(), b.request.id());
        });
        List<Work> order = new ArrayList<>(input.length);
        int start = 0;
        for (EffectPriority priority : EffectPriority.values()) {
            int end = start;
            for (; end < sorted.size() && sorted.get(end).request.recipe().priority() == priority; end++) { }
            int size = end - start;
            if (size > 0) {
                int rotation = Math.floorMod(ordinal, size);
                for (int i = 0; i < size; i++) {
                    Work w = sorted.get(start + (rotation + i) % size);
                    w.rank = order.size();
                    order.add(w);
                }
            }
            start = end;
        }
        return order;
    }

    private static boolean deliverable(EffectScene scene, Set<StyleKey> styles) {
        for (Layer layer : scene.layers()) {
            if (layer.shape().size() > 0 && styles.contains(layer.style())) {
                return true;
            }
        }
        return false;
    }

    private static long available(long total, long reserve, long used, EffectPriority priority) {
        return priority == EffectPriority.AMBIENT ? total - used : Math.max(0, total - reserve - used);
    }

    private static long cost(long k, long[] room) {
        long sum = 0;
        for (long remaining : room) {
            sum += Math.min(k, remaining);
        }
        return sum;
    }

    private static void split(Work work, List<Batch> batches, int k) {
        batches.sort((a, b) -> {
            int rank = Integer.compare(a.role.ordinal(), b.role.ordinal());
            return rank != 0 ? rank : Integer.compare(a.layerIndex, b.layerIndex);
        });
        long left = k;
        int start = 0;
        for (LayerRole role : LayerRole.values()) {
            int end = start;
            long total = 0;
            for (; end < batches.size() && batches.get(end).role == role; end++) {
                total += batches.get(end).size;
            }
            if (total <= left) {
                for (int i = start; i < end; i++) {
                    Batch b = batches.get(i);
                    b.kept = b.size;
                }
                left -= total;
            } else if (left > 0) {
                Batch[] byRemainder = new Batch[end - start];
                long assigned = 0;
                for (int i = start; i < end; i++) {
                    Batch b = batches.get(i);
                    long product = left * b.size;
                    b.kept = (int) (product / total);
                    b.remainder = product % total;
                    assigned += b.kept;
                    byRemainder[i - start] = b;
                }
                Arrays.sort(byRemainder, (a, b) -> {
                    int rank = Long.compare(b.remainder, a.remainder);
                    return rank != 0 ? rank : Integer.compare(a.layerIndex, b.layerIndex);
                });
                for (int i = 0; i < left - assigned; i++) {
                    byRemainder[i].kept++;
                }
                left = 0;
            }
            start = end;
        }
        long count = 0;
        for (Batch b : batches) {
            if (b.kept > 0) {
                count++;
            }
        }
        int length = (int) count;
        work.batchIndices = new int[length];
        work.batchKept = new int[length];
        work.batchSizes = new int[length];
        work.offsets = new int[length];
        int position = 0;
        for (Batch b : batches) {
            if (b.kept > 0) {
                work.batchIndices[position] = b.frameIndex;
                work.batchKept[position] = b.kept;
                work.batchSizes[position] = b.size;
                int g = (int) (((long) b.size + b.kept - 1) / b.kept);
                work.offsets[position] = Math.floorMod(work.request.frameOrdinal(), g);
                position++;
            }
        }
    }

    private static final class Work {
        private final DeliveryRequest request;
        private int rank;
        private Outcome outcome;
        private QualityTier tier;
        private EffectScene scene;
        private EffectFrame frame;
        private long reserved;
        private int unsupported;
        private int kept;
        private long placements;
        private int[] batchIndices;
        private int[] batchKept;
        private int[] batchSizes;
        private int[] offsets;
        private long[] recipients;
        private int[] shares;

        private Work(DeliveryRequest request) { this.request = request; }
    }

    private static final class Batch {
        private final int frameIndex;
        private final int layerIndex;
        private final int size;
        private final LayerRole role;
        private int kept;
        private long remainder;

        private Batch(int frameIndex, int layerIndex, int size, LayerRole role) {
            this.frameIndex = frameIndex;
            this.layerIndex = layerIndex;
            this.size = size;
            this.role = role;
        }
    }
}
