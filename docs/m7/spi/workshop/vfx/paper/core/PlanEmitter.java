package workshop.vfx.paper.core;

import java.util.Map;
import workshop.vfx.delivery.DeliveryPlan;

/**
 * M7 R3 SPI PROPOSAL (not production). Walks a {@link DeliveryPlan} in M6 allocation order: deliveries sorted by
 * {@code allocationRank()} ascending (never by list or input order), DELIVERED and THINNED only. Before any recipient
 * of a delivery is considered, its context is validated: missing, mismatched ({@code context.requestId() !=
 * delivery.requestId()}) or invalid (non-finite anchor, negative owner or world id) skips the whole delivery with that
 * reason. Then, for each recipient r in recipient order, its first {@code recipientPoints(r)} selections over the
 * ordered batches; no unsupported style, no unselected point. Sends of a disabled style are skipped
 * ({@link SkipReason#STYLE_DISABLED}) and failures are reported to {@code health}.
 */
public interface PlanEmitter {
    EmissionReport emit(DeliveryPlan plan, Map<Long, RequestContext> contexts, StyleHealth health, RecipientGate gate,
            PointValidator points, ParticleSink sink, EmissionLimits limits);
}
