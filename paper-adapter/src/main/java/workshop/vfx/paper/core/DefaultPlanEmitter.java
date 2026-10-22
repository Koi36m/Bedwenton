package workshop.vfx.paper.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import workshop.vfx.delivery.DeliveryPlan;
import workshop.vfx.delivery.EffectDelivery;
import workshop.vfx.delivery.Outcome;
import workshop.vfx.scene.FrameBatch;
import workshop.vfx.scene.StyleKey;

/** M7 allocation-order emitter with exact recipient-prefix accounting. */
public final class DefaultPlanEmitter implements PlanEmitter {
    private final ThreadGuard thread;
    public DefaultPlanEmitter() { this(new ThreadGuard()); }
    public DefaultPlanEmitter(ThreadGuard thread) { this.thread = java.util.Objects.requireNonNull(thread); }
    @Override
    public EmissionReport emit(DeliveryPlan plan, Map<Long, RequestContext> contexts, StyleHealth health, RecipientGate gate,
            PointValidator points, ParticleSink sink, EmissionLimits limits) {
        thread.check();
        Map<SkipReason, Long> sd = new EnumMap<>(SkipReason.class), sr = new EnumMap<>(SkipReason.class), sp = new EnumMap<>(SkipReason.class);
        Set<StyleKey> disabledNow = new LinkedHashSet<>();
        long attempted = 0, succeeded = 0, failed = 0, calls = 0, inSkippedDeliveries = 0, inSkippedRecipients = 0;
        Map<Long, Integer> perViewer = new HashMap<>();
        List<EffectDelivery> ordered = new ArrayList<>(plan.deliveries());
        ordered.sort(Comparator.comparingInt(EffectDelivery::allocationRank));
        for (EffectDelivery d : ordered) {
            if (d.outcome() != Outcome.DELIVERED && d.outcome() != Outcome.THINNED) continue;
            RequestContext c = contexts.get(d.requestId());
            SkipReason bad = c == null ? SkipReason.MISSING_REQUEST_CONTEXT
                    : c.requestId() != d.requestId() ? SkipReason.MISMATCHED_REQUEST_CONTEXT
                    : !validContext(c) ? SkipReason.INVALID_REQUEST_CONTEXT : null;
            if (bad != null) { sd.merge(bad, 1L, Long::sum); inSkippedDeliveries += d.placements(); continue; }
            boolean directional=!c.directions().isEmpty() || d.frame().batches().stream().anyMatch(b->DirectionalEmission.STYLES.contains(b.style()));
            if(directional&&!DirectionalEmission.validPair(d.frame(),c.directions())) {
                sd.merge(SkipReason.INVALID_DIRECTIONAL_CONTEXT,1L,Long::sum);inSkippedDeliveries+=d.placements();continue;
            }
            for (int r = 0; r < d.recipientCount(); r++) {
                long viewer = d.recipient(r);
                Optional<SkipReason> g = gate.check(c, viewer);
                if (g.isPresent()) { sr.merge(g.get(), 1L, Long::sum); inSkippedRecipients += d.recipientPoints(r); continue; }
                if(directional) {
                    SkipReason refusal=d.recipientPoints(r)!=2||d.keptPoints()!=2 ? SkipReason.INCOMPLETE_STREAM_PAIR
                        : health.isDisabled(DirectionalEmission.A)||health.isDisabled(DirectionalEmission.B) ? SkipReason.STYLE_DISABLED
                        : calls>limits.maxCallsPerTick()-2 || perViewer.getOrDefault(viewer,0)>limits.maxCallsPerViewerPerTick()-2 ? SkipReason.EMISSION_LIMIT : null;
                    if(refusal!=null){sr.merge(refusal,1L,Long::sum);inSkippedRecipients+=d.recipientPoints(r);continue;}
                }
                int remaining = d.recipientPoints(r);
                for (int pos = 0; pos < d.orderedBatchCount() && remaining > 0; pos++) {
                    FrameBatch b = d.frame().batches().get(d.orderedBatch(pos));
                    for (int j = 0; j < d.kept(pos) && remaining > 0; j++, remaining--) {
                        int i = d.selectedIndex(pos, j);
                        double x = b.x(i), y = b.y(i), z = b.z(i);
                        StyleKey style = b.style();
                        if (health.isDisabled(style)) { sp.merge(SkipReason.STYLE_DISABLED, 1L, Long::sum); continue; }
                        Optional<SkipReason> pv = (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z))
                                ? points.check(c.worldId(), x, y, z) : Optional.of(SkipReason.POINT_NOT_FINITE);
                        if (pv.isPresent()) { sp.merge(pv.get(), 1L, Long::sum); continue; }
                        int pvCount = perViewer.getOrDefault(viewer, 0);
                        if (calls >= limits.maxCallsPerTick() || pvCount >= limits.maxCallsPerViewerPerTick()) {
                            sp.merge(SkipReason.EMISSION_LIMIT, 1L, Long::sum); continue;
                        }
                        calls++; perViewer.put(viewer, pvCount + 1); attempted++;
                        try {
                            if(directional)sink.sendDirectional(viewer,c.worldId(),style,x,y,z,c.directions().get(style));
                            else sink.send(viewer, c.worldId(), style, x, y, z);
                            succeeded++; health.recordSuccess(style);
                        } catch (RuntimeException e) {
                            failed++;
                            if (health.recordFailure(style)) disabledNow.add(style);
                        }
                    }
                }
            }
        }
        return report(plan, attempted, succeeded, failed, sd, sr, sp, inSkippedDeliveries, inSkippedRecipients, disabledNow);
    }

    static boolean validContext(RequestContext c) {
        return c.ownerViewerId() >= 0 && c.worldId() >= 0
                && OwnerAwareRecipientRule.finite(c.anchorX(), c.anchorY(), c.anchorZ());
    }

    private static EmissionReport report(DeliveryPlan plan, long attempted, long succeeded, long failed, Map<SkipReason, Long> sd,
            Map<SkipReason, Long> sr, Map<SkipReason, Long> sp, long inSkippedDeliveries, long inSkippedRecipients,
            Set<StyleKey> disabledNow) {
        return new EmissionReport(plan.tickOrdinal(), plan.placements(), attempted, succeeded, failed, sd, sr, sp,
                inSkippedDeliveries, inSkippedRecipients, Set.copyOf(disabledNow), 0, 0);
    }
}
