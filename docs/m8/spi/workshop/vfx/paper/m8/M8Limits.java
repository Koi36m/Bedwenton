package workshop.vfx.paper.m8;

/**
 * M8 R1 SPI PROPOSAL (not production). Validated M8 configuration (performance spec section 3). Values outside the
 * hard ranges refuse enable, as M7's DemoConfig does; there is no silent fallback.
 *
 * @param defaultTier          "simple" (conservative default for M8 commands)
 * @param maxEffectsPerPlayer  concurrent M8 effects per player, 1..3 (one per kind)
 * @param auraLifetimeTicks    20..12000
 * @param trailLifetimeTicks   20..12000
 * @param heavyAllowed         false refuses heavy for M8 commands without affecting the M7 ring
 */
public record M8Limits(String defaultTier, int maxEffectsPerPlayer, int auraLifetimeTicks, int trailLifetimeTicks,
        boolean heavyAllowed) { }
