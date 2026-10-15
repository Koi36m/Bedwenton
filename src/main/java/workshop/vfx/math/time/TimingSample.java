package workshop.vfx.math.time;

/** Validated timing stage, nonnegative pass index and progress in [0, 1]. */
public record TimingSample(Stage stage, long cycle, double progress) {
    public enum Stage { BEFORE, ACTIVE, AFTER }

    public TimingSample {
        if (stage == null || cycle < 0 || !(progress >= 0.0 && progress <= 1.0)) {
            throw new IllegalArgumentException("TimingSample: stage, nonnegative cycle and progress in [0, 1] required");
        }
    }
}
