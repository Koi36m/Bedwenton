package workshop.vfx.paper.core;

import java.util.Map;
import java.util.Set;
import workshop.vfx.scene.StyleKey;

/**
 * Adapter accounting for one tick, kept separate from the M6 plan's counters.
 * Every planned placement is accounted for exactly once (contract section 7.2):
 * {@code plannedPlacements = attemptedCalls + sum(skippedPoints) + placementsInSkippedRecipients
 * + placementsInSkippedDeliveries}, and {@code attemptedCalls = succeededCalls + failedCalls}.
 * {@code skippedDeliveries} counts whole deliveries skipped for a missing, mismatched or invalid context;
 * {@code stylesDisabledThisTick} lists styles whose third consecutive failure happened in this tick.
 */
public record EmissionReport(long tickOrdinal, long plannedPlacements, long attemptedCalls, long succeededCalls,
        long failedCalls, Map<SkipReason, Long> skippedDeliveries, Map<SkipReason, Long> skippedRecipients,
        Map<SkipReason, Long> skippedPoints, long placementsInSkippedDeliveries, long placementsInSkippedRecipients,
        Set<StyleKey> stylesDisabledThisTick, long planningNanos, long emissionNanos) {
    public EmissionReport {
        skippedDeliveries = Map.copyOf(skippedDeliveries);
        skippedRecipients = Map.copyOf(skippedRecipients);
        skippedPoints = Map.copyOf(skippedPoints);
        stylesDisabledThisTick = Set.copyOf(stylesDisabledThisTick);
    }
    public EmissionReport withTimings(long planning, long emission) {
        return new EmissionReport(tickOrdinal, plannedPlacements, attemptedCalls, succeededCalls, failedCalls,
                skippedDeliveries, skippedRecipients, skippedPoints, placementsInSkippedDeliveries,
                placementsInSkippedRecipients, stylesDisabledThisTick, planning, emission);
    }
}
