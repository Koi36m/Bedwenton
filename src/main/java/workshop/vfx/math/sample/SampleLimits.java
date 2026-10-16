package workshop.vfx.math.sample;

/** Caller-selected, bounded sample allocations and table evaluation work. */
public record SampleLimits(int maxSamples, int maxEvaluations) {
    public static final int MAX_SAMPLES_CEILING = 1 << 20;
    public static final int MIN_EVALUATIONS = 256;
    public static final int MAX_EVALUATIONS_CEILING = 1 << 24;

    public SampleLimits {
        if (maxSamples < 1 || maxSamples > MAX_SAMPLES_CEILING
                || maxEvaluations < MIN_EVALUATIONS || maxEvaluations > MAX_EVALUATIONS_CEILING) {
            throw new IllegalArgumentException("Sample limits outside supported bounds");
        }
    }
}
