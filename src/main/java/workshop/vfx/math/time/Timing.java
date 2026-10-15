package workshop.vfx.math.time;

/** Delay and half-open timing passes, evaluated without accumulating state. */
public record Timing(double delayTicks, double durationTicks, RepeatMode mode, long cycles) {
    public static final long FOREVER = Long.MAX_VALUE;

    public Timing {
        if (mode == null || !Double.isFinite(delayTicks) || !Double.isFinite(durationTicks)
                || durationTicks <= 0.0 || cycles < 1 || (mode == RepeatMode.ONCE && cycles != 1)) {
            throw new IllegalArgumentException("Timing: finite delay, positive finite duration and valid mode/cycles required");
        }
    }

    public static Timing once(double durationTicks) {
        return new Timing(0.0, durationTicks, RepeatMode.ONCE, 1);
    }

    public static Timing loop(double durationTicks) {
        return new Timing(0.0, durationTicks, RepeatMode.LOOP, FOREVER);
    }

    public static Timing pingPong(double durationTicks) {
        return new Timing(0.0, durationTicks, RepeatMode.PING_PONG, FOREVER);
    }

    public Timing withDelay(double delayTicks) {
        return new Timing(delayTicks, durationTicks, mode, cycles);
    }

    public Timing withCycles(long cycles) {
        return new Timing(delayTicks, durationTicks, mode, cycles);
    }

    public TimingSample sample(double localTicks) {
        if (!Double.isFinite(localTicks)) {
            throw new IllegalArgumentException("Timing.sample: localTicks must be finite");
        }
        double u = localTicks - delayTicks;
        if (!Double.isFinite(u)) {
            throw new ArithmeticException("Timing.sample: elapsed ticks are not finite");
        }
        if (u == 0.0) u = 0.0;
        if (u < 0.0) return new TimingSample(TimingSample.Stage.BEFORE, 0, 0.0);
        double d = durationTicks;
        double r = u % d;
        double n = Math.rint((u - r) / d);
        if (n >= (double) cycles) {
            double end = mode == RepeatMode.PING_PONG && (cycles & 1L) == 0 ? 0.0 : 1.0;
            return new TimingSample(TimingSample.Stage.AFTER, cycles - 1, end);
        }
        long k = (long) n;
        double progress = mode == RepeatMode.PING_PONG && (k & 1L) != 0 ? (d - r) / d : r / d;
        return new TimingSample(TimingSample.Stage.ACTIVE, k, progress);
    }
}
