package workshop.vfx.paper.m8;

/** M8 R1 SPI PROPOSAL (not production). Kinds of opt-in demo effects sharing the single M7 tick and M6 plan. */
public enum EffectKind {
    /** The accepted M7 static ring (unchanged; still owned by RingDriver). */
    RING,
    /** Showcase A: orbit formation that follows its owner. */
    AURA,
    /** Showcase B: bounded movement trail behind its owner. */
    TRAIL,
    /** Showcase C: short, static-anchored, multi-phase animation that ends by itself. */
    ACTIVATION
}
