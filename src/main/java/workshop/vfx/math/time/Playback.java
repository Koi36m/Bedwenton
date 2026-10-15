package workshop.vfx.math.time;

/** Immutable affine mapping from an explicit instant to local animation ticks. */
public record Playback(TimePoint anchor, double anchorTicks, double rate, boolean paused) {
    public Playback {
        if (anchor == null || !Double.isFinite(anchorTicks) || !Double.isFinite(rate)) {
            throw new IllegalArgumentException("Playback: nonnull anchor and finite anchorTicks and rate required");
        }
    }

    public static Playback start(TimePoint at) {
        return new Playback(at, 0.0, 1.0, false);
    }

    public static Playback start(TimePoint at, double initialTicks, double rate) {
        return new Playback(at, initialTicks, rate, false);
    }

    public double localTicks(TimePoint t) {
        validateDomain(t, "Playback.localTicks");
        if (paused) return anchorTicks;
        return anchorTicks + rate * t.ticksSince(anchor);
    }

    public Playback pause(TimePoint at) {
        validateControl(at, "Playback.pause");
        if (paused) return this;
        return new Playback(at, reanchorTicks(at, "Playback.pause"), rate, true);
    }

    public Playback resume(TimePoint at) {
        validateControl(at, "Playback.resume");
        if (!paused) return this;
        return new Playback(at, anchorTicks, rate, false);
    }

    public Playback withRate(TimePoint at, double rate) {
        validateControl(at, "Playback.withRate");
        if (!Double.isFinite(rate)) {
            throw new IllegalArgumentException("Playback.withRate: rate must be finite");
        }
        return new Playback(at, reanchorTicks(at, "Playback.withRate"), rate, paused);
    }

    public Playback seek(TimePoint at, double ticks) {
        validateControl(at, "Playback.seek");
        if (!Double.isFinite(ticks)) {
            throw new IllegalArgumentException("Playback.seek: ticks must be finite");
        }
        return new Playback(at, ticks, rate, paused);
    }

    public Playback restart(TimePoint at) {
        validateControl(at, "Playback.restart");
        return new Playback(at, 0.0, rate, false);
    }

    private void validateDomain(TimePoint at, String operation) {
        if (at == null || at.domain() != anchor.domain()) {
            throw new IllegalArgumentException(operation + ": instant must have the anchor domain");
        }
    }

    private void validateControl(TimePoint at, String operation) {
        validateDomain(at, operation);
        boolean before = anchor.domain() == TimeDomain.COSMETIC
                ? at.value() - anchor.value() < 0 : at.value() < anchor.value();
        if (before) {
            throw new IllegalArgumentException(operation + ": control instant precedes anchor");
        }
    }

    private double reanchorTicks(TimePoint at, String operation) {
        double ticks = localTicks(at);
        if (!Double.isFinite(ticks)) {
            throw new ArithmeticException(operation + ": re-anchored ticks are not finite");
        }
        return ticks;
    }
}
