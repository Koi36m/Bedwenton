package workshop.vfx.paper.core;
/** Closed payload descriptions, validated before any Paper objects are constructed. */
public sealed interface ParticleSpec permits ParticleSpec.Plain, ParticleSpec.Dust {
    record Plain(String particleKey) implements ParticleSpec {
        public Plain {
            if (!"minecraft:end_rod".equals(particleKey)) throw new IllegalArgumentException("Unknown plain particle");
        }
    }
    record Dust(int rgb, float size) implements ParticleSpec {
        public Dust {
            if (rgb < 0 || rgb > 0xFFFFFF || !Float.isFinite(size) || size < 0.01f || size > 4.0f)
                throw new IllegalArgumentException("Invalid dust RGB or size");
        }
    }
}
