package workshop.vfx.math.time;

/** Nominal tick/second conversions, using the specified IEEE-754 operation order. */
public final class TickTime {
    public static final long TICKS_PER_SECOND = 20L;
    public static final long NANOS_PER_TICK = 50_000_000L;

    private TickTime() { }

    public static double ticksToSeconds(double ticks) {
        return ticks / 20.0;
    }

    public static double secondsToTicks(double seconds) {
        return seconds * 20.0;
    }
}
