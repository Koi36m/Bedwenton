package workshop.vfx.math.time;

/** An exact tick count or nanosecond count; no clock is read by this value. */
public record TimePoint(TimeDomain domain, long value) {
    public TimePoint {
        if (domain == null) {
            throw new IllegalArgumentException("TimePoint: domain must not be null");
        }
    }

    public static TimePoint gameTick(long tick) {
        return new TimePoint(TimeDomain.GAME, tick);
    }

    public static TimePoint cosmeticNanos(long nanos) {
        return new TimePoint(TimeDomain.COSMETIC, nanos);
    }

    public double ticksSince(TimePoint origin) {
        if (origin == null || origin.domain != domain) {
            throw new IllegalArgumentException("TimePoint.ticksSince: origin must have the same domain");
        }
        long delta;
        try {
            delta = Math.subtractExact(value, origin.value);
        } catch (ArithmeticException e) {
            throw new ArithmeticException("TimePoint.ticksSince: instant difference overflows long");
        }
        return domain == TimeDomain.GAME ? (double) delta : (double) delta / 50_000_000.0;
    }
}
