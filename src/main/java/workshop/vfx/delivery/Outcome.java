package workshop.vfx.delivery;

/** Complete per-request planning outcome; no emission is performed. */
public enum Outcome {
    DELIVERED, THINNED,
    SKIPPED_NO_VIEWERS, SKIPPED_NO_ELIGIBLE_TIER, SKIPPED_UNSUPPORTED, SKIPPED_GENERATION_BUDGET,
    SKIPPED_INACTIVE, SKIPPED_DELIVERY_BUDGET, FAILED_EVALUATION
}
