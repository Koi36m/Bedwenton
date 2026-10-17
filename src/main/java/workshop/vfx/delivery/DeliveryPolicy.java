package workshop.vfx.delivery;

/** Owner-chosen resource limits, not recommended server settings. */
public record DeliveryPolicy(int maxRequests, int maxViewersPerRequest, long maxViewerEntries,
        long generatedPointBudget, long ambientGeneratedReserve, long placementBudget, long ambientPlacementReserve,
        long worldPlacementBudget, long viewerPlacementBudget, int simpleKeptCap, int balancedKeptCap, int heavyKeptCap) {
    public static final int MAX_REQUESTS_CEILING = 1 << 16;
    public static final long MAX_VIEWER_ENTRIES_CEILING = 1L << 24;
    public static final long MAX_BUDGET = 1L << 50;

    public DeliveryPolicy {
        if (maxRequests < 1 || maxRequests > MAX_REQUESTS_CEILING
                || maxViewersPerRequest < 0 || maxViewersPerRequest > DeliveryRequest.MAX_VIEWERS
                || maxViewerEntries < 0 || maxViewerEntries > MAX_VIEWER_ENTRIES_CEILING
                || !DeliveryChecks.budget(generatedPointBudget) || !DeliveryChecks.budget(placementBudget)
                || !DeliveryChecks.budget(worldPlacementBudget) || !DeliveryChecks.budget(viewerPlacementBudget)
                || ambientGeneratedReserve < 0 || ambientGeneratedReserve > generatedPointBudget
                || ambientPlacementReserve < 0 || ambientPlacementReserve > placementBudget
                || simpleKeptCap < 0 || simpleKeptCap > balancedKeptCap || balancedKeptCap > heavyKeptCap
                || heavyKeptCap > (1 << 20)) {
            throw new IllegalArgumentException("DeliveryPolicy: fields must satisfy the approved ceilings and ordered caps");
        }
    }

    public int keptCap(QualityTier tier) {
        DeliveryChecks.nonnull(tier, "DeliveryPolicy.keptCap: tier");
        return switch (tier) {
            case SIMPLE -> simpleKeptCap;
            case BALANCED -> balancedKeptCap;
            case HEAVY -> heavyKeptCap;
        };
    }
}
