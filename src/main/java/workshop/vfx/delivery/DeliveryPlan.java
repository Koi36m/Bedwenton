package workshop.vfx.delivery;

import java.util.List;

/** Immutable input-order deliveries and exact sums of their accounting fields. */
public final class DeliveryPlan {
    private final long tickOrdinal;
    private final List<EffectDelivery> deliveries;
    private final int[] counts;
    private final long reserved;
    private final long source;
    private final long kept;
    private final long recipients;
    private final long placements;
    private final long unsupported;

    DeliveryPlan(long tickOrdinal, List<EffectDelivery> deliveries) {
        this.tickOrdinal = tickOrdinal;
        this.deliveries = List.copyOf(deliveries);
        counts = new int[Outcome.values().length];
        long reservedSum = 0, sourceSum = 0, keptSum = 0, recipientSum = 0, placementSum = 0, unsupportedSum = 0;
        for (EffectDelivery delivery : deliveries) {
            counts[delivery.outcome().ordinal()]++;
            reservedSum += delivery.reservedGeneratedPoints();
            sourceSum += delivery.sourcePoints();
            keptSum += delivery.keptPoints();
            recipientSum += delivery.recipientCount();
            placementSum += delivery.placements();
            unsupportedSum += delivery.unsupportedPoints();
        }
        reserved = reservedSum;
        source = sourceSum;
        kept = keptSum;
        recipients = recipientSum;
        placements = placementSum;
        unsupported = unsupportedSum;
    }

    public long tickOrdinal() { return tickOrdinal; }
    public List<EffectDelivery> deliveries() { return deliveries; }
    public int count(Outcome outcome) {
        return counts[DeliveryChecks.nonnull(outcome, "DeliveryPlan.count: outcome").ordinal()];
    }
    public long reservedGeneratedPoints() { return reserved; }
    public long sourcePoints() { return source; }
    public long keptPoints() { return kept; }
    public long recipients() { return recipients; }
    public long placements() { return placements; }
    public long unsupportedPoints() { return unsupported; }
}
