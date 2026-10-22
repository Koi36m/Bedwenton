package workshop.vfx.paper.core;

import workshop.vfx.scene.StyleKey;

/**
 * Sends one particle at an exact position to one viewer. The Paper
 * implementation calls {@code Player.spawnParticle(particle, x, y, z, 0, 0, 0, 0, 0, data, false)}. A
 * {@link RuntimeException} counts as a failed call and never escapes the tick.
 */
@FunctionalInterface
public interface ParticleSink {
    void send(long viewerId, int worldId, StyleKey style, double x, double y, double z);

    /** Unsupported implementations fail closed; a directional placement must never fall back to positional send. */
    default void sendDirectional(long viewerId, int worldId, StyleKey style, double x, double y, double z,
                                 DirectionalEmission motion) {
        throw new UnsupportedOperationException("Directional END_ROD sink unavailable");
    }
}
