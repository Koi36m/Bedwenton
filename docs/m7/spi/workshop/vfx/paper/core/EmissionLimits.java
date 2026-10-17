package workshop.vfx.paper.core;

/**
 * M7 R3 SPI PROPOSAL (not production). Defensive per-tick ceilings on actual adapter calls, checked before each call.
 * The M6 policy is aligned so that they never bind when nothing else is skipped (contract section 9.1); they remain a
 * backstop. When they do bind, the points not sent are those latest in allocation order.
 */
public record EmissionLimits(int maxCallsPerTick, int maxCallsPerViewerPerTick) { }
