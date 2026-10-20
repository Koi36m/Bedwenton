package workshop.vfx.paper.m8;

import java.util.Objects;
import java.util.UUID;
import workshop.vfx.delivery.EffectRecipe;
import workshop.vfx.delivery.QualityTier;
import workshop.vfx.paper.core.SessionIds;
import workshop.vfx.paper.core.ThreadGuard;

/** K1 command-to-registry boundary. All fallible start validation precedes transactional replacement. */
public final class AuraSession {
    private final ThreadGuard thread;
    private final SessionIds ids;
    private final AnchorSource anchors;
    private final AnchorIdentityGuard guard;
    private final DynamicEffectRegistry registry;
    private final M8Config config;
    private final EffectRecipe recipe;
    private final EffectRecipe haloOnly;

    public AuraSession(ThreadGuard thread, SessionIds ids, AnchorSource anchors,
                       DynamicEffectRegistry registry, M8Config config) {
        this.thread = Objects.requireNonNull(thread); this.ids = Objects.requireNonNull(ids);
        this.anchors = Objects.requireNonNull(anchors); this.registry = Objects.requireNonNull(registry);
        this.config = Objects.requireNonNull(config); guard = new AnchorIdentityGuard(ids, thread);
        recipe = AuraRecipe.create(config.auraLifetimeTicks());
        haloOnly = AuraRecipe.withoutSigil(recipe);
    }

    public long start(UUID owner, QualityTier tier, long tick) {
        thread.check(); Objects.requireNonNull(owner); Objects.requireNonNull(tier);
        if (!config.enabled()) throw new IllegalArgumentException("M8 is disabled in configuration");
        if (tier == QualityTier.HEAVY && !config.heavyAllowed()) throw new IllegalArgumentException("M8 heavy tier is disabled");
        AnchorBinding binding = new AnchorBinding(owner, ids.viewerId(owner));
        AnchorVerdict verdict = guard.validate(anchors.snapshot(owner, tick), binding, tick);
        if (verdict.state() != AnchorState.READY)
            throw new IllegalArgumentException("Aura anchor unavailable: " + verdict.reason());
        return registry.start(owner, EffectKind.AURA, (id, bound) ->
            new AuraEffect(thread, guard, id, bound, tick, config.auraLifetimeTicks(), recipe, haloOnly, tier));
    }

    public void stop(UUID owner) { thread.check(); registry.stop(owner, EffectKind.AURA); }
    public String status(UUID owner) {
        thread.check();
        return registry.effect(owner, EffectKind.AURA).map(e -> "aura=" + ((AuraEffect)e).tier().name().toLowerCase(java.util.Locale.ROOT))
            .orElse("aura=off");
    }
}
