package workshop.vfx.paper.m8;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import workshop.vfx.delivery.QualityTier;

/** Opt-in configuration reserved for the staged M8 checkpoints. K0 implements no visual recipes. */
public record M8Config(boolean enabled, QualityTier defaultTier, boolean heavyAllowed, int maxEffectsPerPlayer,
                       int auraLifetimeTicks, int trailLifetimeTicks, double trailMaxStep, int invalidAnchorTicksToTerminate) {
    public M8Config {
        if(defaultTier==null || maxEffectsPerPlayer<1 || maxEffectsPerPlayer>3
                || auraLifetimeTicks<20 || auraLifetimeTicks>12000 || trailLifetimeTicks<20 || trailLifetimeTicks>12000
                || !Double.isFinite(trailMaxStep) || trailMaxStep<=0.5 || trailMaxStep>64
                || invalidAnchorTicksToTerminate<1 || invalidAnchorTicksToTerminate>200)
            throw new IllegalArgumentException("M8 configuration outside approved bounds");
    }
    public static Map<String,Object> defaults() {
        return Map.of("enabled",false,"defaultTier","simple","heavyAllowed",true,"maxEffectsPerPlayer",2,
            "auraLifetimeTicks",1200,"trailLifetimeTicks",1200,"trailMaxStep",8.0,"invalidAnchorTicksToTerminate",20);
    }
    public static M8Config from(Map<String,?> input) {
        Map<String,Object> m=new HashMap<>(defaults());m.putAll(input);
        if(!(m.get("enabled") instanceof Boolean enabled) || !(m.get("heavyAllowed") instanceof Boolean heavy))
            throw new IllegalArgumentException("M8 enabled and heavyAllowed must be boolean");
        if(!(m.get("defaultTier") instanceof String tier) || !(m.get("trailMaxStep") instanceof Number step))
            throw new IllegalArgumentException("M8 tier must be text and trailMaxStep numeric");
        QualityTier quality;
        try {quality=QualityTier.valueOf(tier.toUpperCase(Locale.ROOT));}
        catch(IllegalArgumentException e){throw new IllegalArgumentException("M8 defaultTier must be simple, balanced or heavy");}
        return new M8Config(enabled,quality,heavy,integer(m,"maxEffectsPerPlayer"),integer(m,"auraLifetimeTicks"),
            integer(m,"trailLifetimeTicks"),step.doubleValue(),integer(m,"invalidAnchorTicksToTerminate"));
    }
    private static int integer(Map<String,Object> m,String key) {
        Object raw=m.get(key);
        if(!(raw instanceof Byte || raw instanceof Short || raw instanceof Integer || raw instanceof Long))
            throw new IllegalArgumentException("M8 "+key+" must be an integer");
        long value=((Number)raw).longValue();
        if(value<Integer.MIN_VALUE || value>Integer.MAX_VALUE)throw new IllegalArgumentException("M8 "+key+" integer overflow");
        return (int)value;
    }
}
