package workshop.vfx.paper.m8;

import java.util.List;

/**
 * M8 R1 SPI PROPOSAL (not production). Bounded per-trail movement history (anchor spec section 7). Fixed capacity
 * chosen at construction; never grows. Main thread only. Production: {@code RingBufferTrailHistory}.
 */
public interface TrailHistory {
    /** Observes one READY snapshot (at most once per tick); may add 0..maxSamplesPerTick samples or start a segment. */
    void observe(AnchorSnapshot snapshot);

    /** Drops expired samples and returns the live ones, newest first; never more than capacity. */
    List<TrailSample> live(long tick);

    int size();

    int capacity();

    /** Forgets everything (world change, stop, dispose). */
    void clear();
}
