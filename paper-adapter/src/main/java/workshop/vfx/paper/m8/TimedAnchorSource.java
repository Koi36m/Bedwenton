package workshop.vfx.paper.m8;

import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;
import workshop.vfx.paper.core.ThreadGuard;

/** Bounded diagnostic counters, separate from deterministic effect timing. Measures snapshot reads only. */
public final class TimedAnchorSource implements AnchorSource {
    private final ThreadGuard thread;
    private final AnchorSource source;
    private final LongSupplier nanos;
    private long tick = Long.MIN_VALUE, snapshotNanos, reads;
    public TimedAnchorSource(ThreadGuard thread, AnchorSource source, LongSupplier nanos) {
        this.thread = Objects.requireNonNull(thread); this.source = Objects.requireNonNull(source);
        this.nanos = Objects.requireNonNull(nanos);
    }
    @Override public AnchorSnapshot snapshot(UUID owner, long planningTick) {
        thread.check();
        if (planningTick != tick) { tick = planningTick; snapshotNanos = 0; reads = 0; }
        long start = nanos.getAsLong();
        try { return source.snapshot(owner, planningTick); }
        finally { snapshotNanos += nanos.getAsLong() - start; reads++; }
    }
    public long tick() { thread.check(); return tick; }
    public long snapshotNanos() { thread.check(); return snapshotNanos; }
    public long reads() { thread.check(); return reads; }
}
