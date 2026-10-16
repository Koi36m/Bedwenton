package workshop.vfx.math.sample;

/** Validation shared by the sampling entry points. */
final class SampleChecks {
    private SampleChecks() { }

    static <T> T nonnull(T value) {
        if (value == null) {
            throw new IllegalArgumentException("Sampling argument must be nonnull");
        }
        return value;
    }

    static void range(double t0, double t1) {
        if (!Double.isFinite(t0) || !Double.isFinite(t1) || t0 > t1
                || !Double.isFinite(t1 - t0)) {
            throw new IllegalArgumentException("Parameter range must be finite, ordered and have finite width");
        }
    }

    static void count(int count, SampleLimits limits) {
        nonnull(limits);
        if (count < 0 || count > limits.maxSamples()) {
            throw new IllegalArgumentException("Sample count outside selected limits");
        }
    }
}
