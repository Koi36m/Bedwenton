package workshop.vfx.paper.m8;

/** M8 R3 SPI PROPOSAL (not production). Why a snapshot was not used (anchor spec section 3.3); counted per effect. */
public enum AnchorRejection {
    /** Accepted. */
    NONE,
    /** No snapshot (owner could not be resolved): TERMINATE. */
    OWNER_UNRESOLVED,
    /** Offline, invalid or dead: TERMINATE. */
    OWNER_GONE,
    /** Snapshot UUID differs from the bound owner: TERMINATE (adapter defect; never recoverable). */
    OWNER_MISMATCH,
    /** Snapshot viewer id differs from the id bound at start (or the session forgot the owner): TERMINATE. */
    VIEWER_ID_MISMATCH,
    /** Observation tick is not the planning tick (stale or from the future): SKIP_TICK. */
    STALE_OR_FUTURE,
    /** World UUID missing, world id unknown, or world id not mapping back to the world UUID: SKIP_TICK. */
    WORLD_INCONSISTENT,
    /** Non-finite position or yaw: SKIP_TICK. */
    NON_FINITE,
    /** Feet y outside [minHeight, maxHeight), or an empty height range: SKIP_TICK. */
    OUTSIDE_WORLD_HEIGHT,
    /** The chunk at the feet position is not loaded: SKIP_TICK (no chunk is ever loaded for an effect). */
    CHUNK_NOT_LOADED
}
