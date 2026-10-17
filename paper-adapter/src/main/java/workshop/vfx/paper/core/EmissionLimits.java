package workshop.vfx.paper.core;
/** Actual call backstops. The planner policy is aligned to these bounds. */
public record EmissionLimits(int maxCallsPerTick, int maxCallsPerViewerPerTick) {
    public EmissionLimits {
        if (maxCallsPerTick < 1 || maxCallsPerTick > 4096 || maxCallsPerViewerPerTick < 1 || maxCallsPerViewerPerTick > 1024)
            throw new IllegalArgumentException("Emission limits outside hard ceilings");
    }
}
