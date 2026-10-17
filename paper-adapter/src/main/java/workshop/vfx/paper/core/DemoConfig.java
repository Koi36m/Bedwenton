package workshop.vfx.paper.core;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;
import workshop.vfx.delivery.DeliveryPolicy;
import workshop.vfx.delivery.QualityTier;
/** Strict configuration parsing: invalid values never fall back to defaults. */
public record DemoConfig(int maxActiveEffects, int maxViewersPerEffect, double viewRadius, int ringLifetimeTicks,
        QualityTier defaultTier, int generatedPointBudget, int placementBudget, int worldPlacementBudget,
        int viewerPlacementBudget, int simpleKeptCap, int balancedKeptCap, int heavyKeptCap,
        int maxCallsPerTick, int maxCallsPerViewerPerTick, boolean debug) {
    public DemoConfig {
        bound("maxActiveEffects", maxActiveEffects, 1, 16); bound("maxViewersPerEffect", maxViewersPerEffect, 1, 16);
        if (!Double.isFinite(viewRadius) || viewRadius < 4 || viewRadius > 64)
            throw new IllegalArgumentException("viewRadius must be finite and within 4.0–64.0");
        bound("ringLifetimeTicks", ringLifetimeTicks, 20, 6000);
        if (defaultTier == null) throw new IllegalArgumentException("defaultTier must be simple, balanced or heavy");
        bound("generatedPointBudget", generatedPointBudget, 0, 1152);
        bound("placementBudget", placementBudget, 0, 4096); bound("worldPlacementBudget", worldPlacementBudget, 0, 4096);
        bound("viewerPlacementBudget", viewerPlacementBudget, 0, 1024);
        bound("simpleKeptCap", simpleKeptCap, 0, 72); bound("balancedKeptCap", balancedKeptCap, 0, 72);
        bound("heavyKeptCap", heavyKeptCap, 0, 72);
        if (simpleKeptCap > balancedKeptCap || balancedKeptCap > heavyKeptCap)
            throw new IllegalArgumentException("Kept caps must be non-decreasing");
        bound("maxCallsPerTick", maxCallsPerTick, 1, 4096);
        bound("maxCallsPerViewerPerTick", maxCallsPerViewerPerTick, 1, 1024);
    }
    private static void bound(String key, int n, int min, int max) {
        if (n < min || n > max) throw new IllegalArgumentException(key + " must be within " + min + "–" + max);
    }
    public static Map<String, Object> defaults() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("maxActiveEffects", 4); m.put("maxViewersPerEffect", 4); m.put("viewRadius", 32.0);
        m.put("ringLifetimeTicks", 600); m.put("defaultTier", "balanced"); m.put("generatedPointBudget", 1152);
        m.put("placementBudget", 2048); m.put("worldPlacementBudget", 2048); m.put("viewerPlacementBudget", 256);
        m.put("simpleKeptCap", 24); m.put("balancedKeptCap", 48); m.put("heavyKeptCap", 72);
        m.put("maxCallsPerTick", 2048); m.put("maxCallsPerViewerPerTick", 256); m.put("debug", false);
        return Map.copyOf(m);
    }
    public static DemoConfig from(Map<String, ?> raw) {
        Map<String, Object> m = new LinkedHashMap<>(defaults()); m.putAll(raw);
        Object radius = m.get("viewRadius");
        if (!(radius instanceof Number n)) throw new IllegalArgumentException("viewRadius must be numeric");
        Object tier = m.get("defaultTier");
        if (!(tier instanceof String s)) throw new IllegalArgumentException("defaultTier must be a string");
        QualityTier quality;
        try { quality = QualityTier.valueOf(s.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("defaultTier must be simple, balanced or heavy"); }
        if (!(m.get("debug") instanceof Boolean debug)) throw new IllegalArgumentException("debug must be boolean");
        return new DemoConfig(integer(m,"maxActiveEffects"), integer(m,"maxViewersPerEffect"), n.doubleValue(),
            integer(m,"ringLifetimeTicks"), quality, integer(m,"generatedPointBudget"), integer(m,"placementBudget"),
            integer(m,"worldPlacementBudget"), integer(m,"viewerPlacementBudget"), integer(m,"simpleKeptCap"),
            integer(m,"balancedKeptCap"), integer(m,"heavyKeptCap"), integer(m,"maxCallsPerTick"),
            integer(m,"maxCallsPerViewerPerTick"), debug);
    }
    private static int integer(Map<String,Object> m, String key) {
        Object value = m.get(key);
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long))
            throw new IllegalArgumentException(key + " must be an integer");
        long n = ((Number) value).longValue();
        if (n < Integer.MIN_VALUE || n > Integer.MAX_VALUE) throw new IllegalArgumentException(key + " integer overflow");
        return (int) n;
    }
    public DeliveryPolicy policy() {
        return new DeliveryPolicy(maxActiveEffects, maxViewersPerEffect, (long)maxActiveEffects*maxViewersPerEffect,
            generatedPointBudget, 0, Math.min(placementBudget, maxCallsPerTick), 0,
            Math.min(worldPlacementBudget, maxCallsPerTick), Math.min(viewerPlacementBudget, maxCallsPerViewerPerTick),
            simpleKeptCap, balancedKeptCap, heavyKeptCap);
    }
    public EmissionLimits emissionLimits() { return new EmissionLimits(maxCallsPerTick,maxCallsPerViewerPerTick); }
}
