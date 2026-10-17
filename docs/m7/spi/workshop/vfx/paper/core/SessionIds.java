package workshop.vfx.paper.core;

import java.util.OptionalLong;
import java.util.UUID;

/**
 * M7 R1 SPI PROPOSAL (not production). Collision-free session ids for M6: viewer UUIDs map to longs and world UUIDs to
 * ints, assigned 0, 1, 2, ... in first-use order and never reused while the plugin is enabled (no hashing).
 * Main thread only.
 */
public interface SessionIds {
    long viewerId(UUID player);

    OptionalLong existingViewerId(UUID player);

    UUID viewer(long viewerId);

    int worldId(UUID world);

    UUID world(int worldId);

    /** Forgets a quitting player's mapping; the number is not reused. */
    void release(UUID player);
}
