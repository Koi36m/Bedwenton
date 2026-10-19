package workshop.vfx.paper.m8;

import java.util.Optional;
import java.util.UUID;

/**
 * K0 foundation. One registered future effect (no visual implementation in K0) (architecture section 5). Created by a
 * command on the main thread; contributes at most one request per tick; disposed exactly once.
 */
public interface DynamicEffect {
    long requestId();

    UUID owner();

    EffectKind kind();

    /**
     * Called once per tick during the snapshot phase with the owner's snapshot (null when none could be read).
     * Returns the draft to plan, or empty to contribute nothing this tick (the effect stays registered unless
     * {@link #finished} becomes true). Never touches Paper objects.
     */
    Optional<EffectDraft> contribute(long tick, AnchorSnapshot snapshot);

    /** True once the effect must be removed (lifetime over, owner gone, or terminated); evaluated after contribute. */
    boolean finished(long tick);

    /** Releases history and buffers; idempotent; must not throw (the registry contains and counts a throwing dispose). */
    void dispose();
}
