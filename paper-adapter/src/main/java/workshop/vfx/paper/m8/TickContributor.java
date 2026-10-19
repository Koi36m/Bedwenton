package workshop.vfx.paper.m8;

import java.util.List;

/**
 * K0 foundation. The additive hook proposed for M7's RingDriver (architecture section 4.2,
 * corrected tick order in R2). During each tick the driver calls {@link #drafts(long)} exactly once, after ring pruning
 * and before the single planning call, also when no ring is active. Production: {@code DynamicEffectRegistry}.
 */
public interface TickContributor {
    /** Drafts for this tick in ascending request id; effects that finish or terminate are disposed before returning. */
    List<EffectDraft> drafts(long tick);

    /** True while at least one M8 effect is registered (it keeps the shared task alive even with zero drafts). */
    boolean active();

    /** Number of registered M8 effects; counts toward the shared global cap. */
    int activeCount();

    /** Stops and disposes every M8 effect (plugin disable). Idempotent. */
    void close();

    /** The contributor used when M8 is absent: M7-only behaviour is exactly unchanged. */
    TickContributor NONE = new TickContributor() {
        @Override public List<EffectDraft> drafts(long tick) { return List.of(); }
        @Override public boolean active() { return false; }
        @Override public int activeCount() { return 0; }
        @Override public void close() { }
    };
}
