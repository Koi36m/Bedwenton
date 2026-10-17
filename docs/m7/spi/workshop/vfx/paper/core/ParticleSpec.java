package workshop.vfx.paper.core;

/**
 * M7 R1 SPI PROPOSAL (not production). A platform-neutral description of one whitelisted Paper particle style.
 * The Paper layer resolves it once at enable time; an unresolvable spec disables that style (fail closed).
 */
public sealed interface ParticleSpec permits ParticleSpec.Plain, ParticleSpec.Dust {
    /** A particle whose {@code getDataType()} is {@code Void}, named by its namespaced key, e.g. {@code minecraft:end_rod}. */
    record Plain(String particleKey) implements ParticleSpec { }

    /** {@code Particle.DUST} with {@code new Particle.DustOptions(Color.fromRGB(rgb), size)}; size in [0.01, 4.0]. */
    record Dust(int rgb, float size) implements ParticleSpec { }
}
