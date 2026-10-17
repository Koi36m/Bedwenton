package workshop.vfx.paper.core;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import workshop.vfx.scene.StyleKey;
/** Two known styles, restricted to the keys that successfully resolved on this server. */
public final class DemoStyleCatalog implements StyleCatalog {
    public static final StyleKey END_ROD = StyleKey.of("bedwenton:demo_end_rod");
    public static final StyleKey DUST = StyleKey.of("bedwenton:demo_dust_red");
    private static final Map<StyleKey, ParticleSpec> WHITELIST = Map.of(
        END_ROD, new ParticleSpec.Plain("minecraft:end_rod"), DUST, new ParticleSpec.Dust(0xFF3030, 1.0f));
    private final Map<StyleKey, ParticleSpec> styles;
    public DemoStyleCatalog(Set<StyleKey> resolved) {
        Map<StyleKey, ParticleSpec> selected = new HashMap<>();
        for (StyleKey key : resolved) {
            if (!WHITELIST.containsKey(key)) throw new IllegalArgumentException("Style outside catalogue");
            selected.put(key, WHITELIST.get(key));
        }
        styles = Map.copyOf(selected);
    }
    public static Map<StyleKey, ParticleSpec> whitelist() { return WHITELIST; }
    @Override public Set<StyleKey> supportedStyles() { return styles.keySet(); }
    @Override public ParticleSpec spec(StyleKey key) { return styles.get(key); }
    @Override public Map<StyleKey, ParticleSpec> asMap() { return styles; }
}
