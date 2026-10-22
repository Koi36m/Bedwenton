package workshop.vfx.paper.reference;

import java.util.HashMap;
import java.util.Map;
import workshop.vfx.paper.core.ParticleSpec;
import workshop.vfx.paper.m8.M8StyleCatalog;
import workshop.vfx.scene.StyleKey;

/** Experimental keys only: never reassign the historical M7/Arcane Halo palette. */
public final class ReferenceStyleCatalog {
    public static final StyleKey LIME = StyleKey.of("bedwenton:reference_lime");
    public static final StyleKey GOLD = StyleKey.of("bedwenton:reference_gold");
    public static final StyleKey WHITE = StyleKey.of("bedwenton:reference_star_white");
    public static final StyleKey WARM = StyleKey.of("bedwenton:reference_warm");
    private ReferenceStyleCatalog() { }
    public static Map<StyleKey, ParticleSpec> whitelist() {
        var all = new HashMap<>(M8StyleCatalog.whitelist());
        all.put(LIME, new ParticleSpec.Dust(0xD9F76A, 0.60f));
        all.put(GOLD, new ParticleSpec.Dust(0xFFDA72, 0.48f));
        all.put(WHITE, new ParticleSpec.Dust(0xFFF9E8, 0.55f));
        all.put(WARM, new ParticleSpec.Dust(0xF3C987, 0.38f));
        return Map.copyOf(all);
    }
}
