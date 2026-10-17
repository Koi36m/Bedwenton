package workshop.vfx.delivery;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import workshop.vfx.math.time.TimeDomain;
import workshop.vfx.scene.EffectScene;
import workshop.vfx.scene.Layer;

/** Immutable variants, priority and layer-role metadata for one named effect. */
public final class EffectRecipe {
    public static final int MAX_ROLES = 4096;
    private final String name;
    private final EffectPriority priority;
    private final TimeDomain domain;
    private final Map<QualityTier, EffectScene> variants;
    private final Map<String, LayerRole> roles;
    private final Set<QualityTier> tiers;

    private EffectRecipe(String name, EffectPriority priority, TimeDomain domain,
            Map<QualityTier, EffectScene> variants, Map<String, LayerRole> roles) {
        this.name = name;
        this.priority = priority;
        this.domain = domain;
        this.variants = Collections.unmodifiableMap(variants);
        this.roles = Collections.unmodifiableMap(roles);
        tiers = Collections.unmodifiableSet(EnumSet.copyOf(variants.keySet()));
    }

    public static EffectRecipe of(String name, EffectPriority priority,
            Map<QualityTier, EffectScene> variants, Map<String, LayerRole> roles) {
        if (!DeliveryChecks.name(name)) {
            throw new IllegalArgumentException("EffectRecipe.of: name must match [a-z0-9_.-]{1,64}");
        }
        DeliveryChecks.nonnull(priority, "EffectRecipe.of: priority");
        DeliveryChecks.nonnull(variants, "EffectRecipe.of: variants");
        DeliveryChecks.nonnull(roles, "EffectRecipe.of: roles");
        if (variants.isEmpty() || variants.size() > 3 || roles.size() > MAX_ROLES) {
            throw new IllegalArgumentException("EffectRecipe.of: require 1 to 3 variants and at most MAX_ROLES roles");
        }
        EnumMap<QualityTier, EffectScene> copied = new EnumMap<>(QualityTier.class);
        Set<String> layerNames = new HashSet<>();
        TimeDomain domain = null;
        for (Map.Entry<QualityTier, EffectScene> entry : variants.entrySet()) {
            QualityTier tier = DeliveryChecks.nonnull(entry.getKey(), "EffectRecipe.of: tier");
            EffectScene scene = DeliveryChecks.nonnull(entry.getValue(), "EffectRecipe.of: scene");
            if (domain != null && domain != scene.domain()) {
                throw new IllegalArgumentException("EffectRecipe.of: variants must share one time domain");
            }
            domain = scene.domain();
            copied.put(tier, scene);
            for (Layer layer : scene.layers()) {
                layerNames.add(layer.name());
            }
        }
        Map<String, LayerRole> copiedRoles = new HashMap<>();
        for (Map.Entry<String, LayerRole> entry : roles.entrySet()) {
            String layer = DeliveryChecks.nonnull(entry.getKey(), "EffectRecipe.of: role layer");
            LayerRole role = DeliveryChecks.nonnull(entry.getValue(), "EffectRecipe.of: role");
            if (!layerNames.contains(layer)) {
                throw new IllegalArgumentException("EffectRecipe.of: role names a layer absent from all variants");
            }
            copiedRoles.put(layer, role);
        }
        return new EffectRecipe(name, priority, domain, copied, copiedRoles);
    }

    public String name() { return name; }
    public EffectPriority priority() { return priority; }
    public TimeDomain domain() { return domain; }
    public Set<QualityTier> tiers() { return tiers; }

    public EffectScene variant(QualityTier tier) {
        DeliveryChecks.nonnull(tier, "EffectRecipe.variant: tier");
        EffectScene scene = variants.get(tier);
        if (scene == null) {
            throw new IllegalArgumentException("EffectRecipe.variant: tier is absent");
        }
        return scene;
    }

    public LayerRole role(String layerName) {
        DeliveryChecks.nonnull(layerName, "EffectRecipe.role: layerName");
        return roles.getOrDefault(layerName, LayerRole.STANDARD);
    }
}
