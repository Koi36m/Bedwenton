package workshop.vfx.paper.m8;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import workshop.vfx.paper.core.DemoStyleCatalog;
import workshop.vfx.paper.core.ParticleSpec;
import workshop.vfx.paper.core.StyleCatalog;
import workshop.vfx.scene.StyleKey;

/** K1 closed whitelist. Historical violet/gold and future showcase keys are not reassigned. */
public final class M8StyleCatalog implements StyleCatalog {
    public static final StyleKey CYAN_HALO = StyleKey.of("bedwenton:m8_arcane_halo_cyan");
    private final Map<StyleKey, ParticleSpec> styles;

    public static Map<StyleKey, ParticleSpec> whitelist() {
        Map<StyleKey, ParticleSpec> all = new HashMap<>(DemoStyleCatalog.whitelist());
        all.put(CYAN_HALO, new ParticleSpec.Dust(0x5CE1FF, 0.7f));
        return Map.copyOf(all);
    }

    public M8StyleCatalog(Set<StyleKey> resolved) {
        Map<StyleKey, ParticleSpec> all = whitelist();
        Map<StyleKey, ParticleSpec> selected = new HashMap<>();
        for (StyleKey key : resolved) {
            if (!all.containsKey(key)) throw new IllegalArgumentException("Style outside K1 catalogue");
            selected.put(key, all.get(key));
        }
        styles = Map.copyOf(selected);
    }

    @Override public Set<StyleKey> supportedStyles() { return styles.keySet(); }
    @Override public ParticleSpec spec(StyleKey key) { return styles.get(key); }
    @Override public Map<StyleKey, ParticleSpec> asMap() { return styles; }
}
