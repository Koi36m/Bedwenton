package workshop.vfx.paper.m8;

/**
 * M8 R1 SPI PROPOSAL (not production). Validated trail parameters (anchor spec section 7; showcase B).
 *
 * @param capacity          ring-buffer size, 1..48 (tier dependent); the oldest sample is overwritten
 * @param minSpacing        distance a new sample must be from the newest one, (0, 4] blocks; rotation alone never qualifies
 * @param maxStep           larger per-tick displacement starts a new segment (no connecting points), (minSpacing, 64] blocks
 * @param maxSamplesPerTick samples added in one tick when interpolating a fast but continuous step, 1..4
 * @param maxAgeTicks       samples older than this are dropped, 1..200
 */
public record TrailRules(int capacity, double minSpacing, double maxStep, int maxSamplesPerTick, int maxAgeTicks) { }
