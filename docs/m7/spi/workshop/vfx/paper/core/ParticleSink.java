package workshop.vfx.paper.core;

import workshop.vfx.scene.StyleKey;

/**
 * M7 R1 SPI PROPOSAL (not production). Sends one particle at an exact position to one viewer. The Paper
 * implementation calls {@code Player.spawnParticle(particle, x, y, z, 0, 0, 0, 0, 0, data, false)}. A
 * {@link RuntimeException} counts as a failed call and never escapes the tick.
 */
@FunctionalInterface
public interface ParticleSink {
    void send(long viewerId, int worldId, StyleKey style, double x, double y, double z);
}
