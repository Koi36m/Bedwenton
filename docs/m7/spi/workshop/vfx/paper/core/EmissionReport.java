package workshop.vfx.paper.core;

import java.util.Map;
import java.util.Set;
import workshop.vfx.scene.StyleKey;

/**
 * M7 R3 SPI PROPOSAL (not production). Adapter accounting for one tick, kept separate from the M6 plan's counters.
 * Every planned placement is accounted for exactly once (contract section 7.2):
 * {@code plannedPlacements = attemptedCalls + sum(skippedPoints) + placementsInSkippedRecipients
 * + placementsInSkippedDeliveries}, and {@code attemptedCalls = succeededCalls + failedCalls}.
 * {@code skippedDeliveries} counts whole deliveries skipped for a missing, mismatched or invalid context;
 * {@code stylesDisabledThisTick} lists styles whose third consecutive failure happened in this tick.
 */
public record EmissionReport(long tickOrdinal, long plannedPlacements, long attemptedCalls, long succeededCalls,
        long failedCalls, Map<SkipReason, Long> skippedDeliveries, Map<SkipReason, Long> skippedRecipients,
        Map<SkipReason, Long> skippedPoints, long placementsInSkippedDeliveries, long placementsInSkippedRecipients,
        Set<StyleKey> stylesDisabledThisTick, long planningNanos, long emissionNanos) { }
