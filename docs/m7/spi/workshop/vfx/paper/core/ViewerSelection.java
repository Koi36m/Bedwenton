package workshop.vfx.paper.core;

/**
 * M7 R3 SPI PROPOSAL (not production). Snapshot viewer selection for one ring (contract section 6, step 1). The owner
 * is admitted first when eligible; the remaining {@code cap - 1} (or {@code cap}) places go to the smallest other
 * eligible viewer ids; only then is the chosen set sorted strictly increasing for {@code DeliveryRequest.of}. Sorting
 * never decides whether the owner is included. Production: {@code OwnerFirstViewerSelection}.
 */
@FunctionalInterface
public interface ViewerSelection {
    long[] select(long ownerViewerId, boolean ownerEligible, long[] otherEligibleAscending, int cap);
}
