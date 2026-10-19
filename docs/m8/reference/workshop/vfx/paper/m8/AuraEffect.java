package workshop.vfx.paper.m8;

import java.util.Optional;
import workshop.vfx.delivery.EffectRecipe;
import workshop.vfx.delivery.QualityTier;
import workshop.vfx.math.time.Playback;
import workshop.vfx.math.time.TimePoint;

/** M8 R3 THROWAWAY REFERENCE (design verification only; never production). */
public final class AuraEffect extends AbstractEffect {
    private final EffectRecipe recipe;
    private final QualityTier tier;
    public AuraEffect(long requestId, AnchorBinding binding, long startTick, int lifetimeTicks, QualityTier tier, AnchorRules rules) {
        super(requestId, binding, startTick, lifetimeTicks, rules, 20);
        this.recipe = M8Recipes.aura(lifetimeTicks); this.tier = tier;
    }
    @Override public EffectKind kind() { return EffectKind.AURA; }
    @Override protected Optional<EffectDraft> draft(long tick, AnchorSnapshot s) {
        return Optional.of(new EffectDraft(EffectKind.AURA, requestId, owner, binding.ownerViewerId(), s.worldId(),
                rules.pose(s, OrientationMode.BODY_YAW, 0), recipe, tier, Playback.start(TimePoint.gameTick(startTick)),
                TimePoint.gameTick(tick), age(tick)));
    }
}
