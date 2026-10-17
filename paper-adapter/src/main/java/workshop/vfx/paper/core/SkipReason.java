package workshop.vfx.paper.core;

/** Why a planned delivery, recipient or point was not sent. */
public enum SkipReason {
    /** Delivery level: no {@link RequestContext} for the delivery's request id. */
    MISSING_REQUEST_CONTEXT,
    /** Delivery level: the context found under the delivery's id carries a different {@code requestId} (R3). */
    MISMATCHED_REQUEST_CONTEXT,
    /**
     * Delivery level (emitter) or recipient level (rule used alone): the context has a non-finite anchor coordinate or a
     * negative owner or world id (R3).
     */
    INVALID_REQUEST_CONTEXT,
    /** Recipient level (gate). */
    VIEWER_OFFLINE, VIEWER_WORLD_CHANGED, VIEWER_PERMISSION_REVOKED,
    /** Recipient level: a viewer position coordinate is NaN or infinite (R3; fail closed). */
    VIEWER_POSITION_INVALID,
    VIEWER_OUT_OF_RANGE,
    /** Point level. */
    POINT_NOT_FINITE, POINT_OUTSIDE_WORLD, STYLE_DISABLED, EMISSION_LIMIT
}
