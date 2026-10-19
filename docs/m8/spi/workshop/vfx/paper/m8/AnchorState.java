package workshop.vfx.paper.m8;

/** M8 R1 SPI PROPOSAL (not production). Result of validating a snapshot (anchor spec section 3.3). */
public enum AnchorState {
    /** Usable this tick. */
    READY,
    /** Not usable this tick; the effect contributes no request but stays alive (for example coordinates non-finite). */
    SKIP_TICK,
    /** The owner is gone (offline, invalid, dead, or no snapshot) or the snapshot names another identity (R3): stopped and disposed. */
    TERMINATE
}
