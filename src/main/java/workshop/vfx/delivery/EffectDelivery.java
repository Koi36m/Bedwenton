package workshop.vfx.delivery;

import workshop.vfx.scene.EffectFrame;

/** Immutable frame reference and compact selection metadata; coordinates are never copied. */
public final class EffectDelivery {
    private static final int[] EMPTY_INTS = new int[0];
    private static final long[] EMPTY_LONGS = new long[0];
    private final long requestId;
    private final int rank;
    private final Outcome outcome;
    private final QualityTier tier;
    private final EffectFrame frame;
    private final long reserved;
    private final int unsupported;
    private final int keptPoints;
    private final long placements;
    private final int[] batches;
    private final int[] kept;
    private final int[] sizes;
    private final int[] offsets;
    private final long[] recipients;
    private final int[] shares;

    /** Takes ownership of fresh selection/recipient arrays, which the planner does not retain. */
    EffectDelivery(long requestId, int rank, Outcome outcome, QualityTier tier, EffectFrame frame, long reserved,
            int unsupported, int keptPoints, long placements, int[] batches, int[] kept, int[] sizes, int[] offsets,
            long[] recipients, int[] shares) {
        this.requestId = requestId;
        this.rank = rank;
        this.outcome = outcome;
        this.tier = tier;
        this.frame = frame;
        this.reserved = reserved;
        this.unsupported = unsupported;
        this.keptPoints = keptPoints;
        this.placements = placements;
        this.batches = batches == null ? EMPTY_INTS : batches;
        this.kept = kept == null ? EMPTY_INTS : kept;
        this.sizes = sizes == null ? EMPTY_INTS : sizes;
        this.offsets = offsets == null ? EMPTY_INTS : offsets;
        this.recipients = recipients == null ? EMPTY_LONGS : recipients;
        this.shares = shares == null ? EMPTY_INTS : shares;
    }

    public long requestId() { return requestId; }
    public int allocationRank() { return rank; }
    public Outcome outcome() { return outcome; }
    public QualityTier tier() { return tier; }
    public EffectFrame frame() { return frame; }
    public long reservedGeneratedPoints() { return reserved; }
    public int sourcePoints() { return frame == null ? 0 : frame.pointCount(); }
    public int unsupportedPoints() { return unsupported; }
    public int keptPoints() { return keptPoints; }
    public long placements() { return placements; }
    public int orderedBatchCount() { return batches.length; }
    public int orderedBatch(int position) { return batches[position]; }
    public int kept(int position) { return kept[position]; }

    public int selectedIndex(int position, int j) {
        int m = kept[position];
        if (j < 0 || j >= m) {
            throw new IndexOutOfBoundsException("EffectDelivery.selectedIndex: selection index outside batch");
        }
        return offsets[position] + (int) ((long) j * sizes[position] / m);
    }

    public int recipientCount() { return recipients.length; }
    public long recipient(int r) { return recipients[r]; }
    public int recipientPoints(int r) { return shares[r]; }
}
