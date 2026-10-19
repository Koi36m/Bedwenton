package workshop.vfx.paper.m8;

import java.util.List;
import java.util.Optional;
import workshop.vfx.delivery.QualityTier;
import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.time.Playback;
import workshop.vfx.math.time.TimePoint;

/**
 * M8 R3 THROWAWAY REFERENCE (design verification only; never production). Two clocks (F3): the effect clock
 * ({@code startTick}, {@code lifetimeTicks}, frame ordinal = age) and the rebuilt scene's clock, restarted at every tick
 * ({@code Playback.start(gameTick(tick))}, {@code now = gameTick(tick)}, so the 2-tick scene is always at local tick 0).
 */
public final class TrailEffect extends AbstractEffect {
    private final TrailHistory history;
    private final TrailRules trailRules;
    private final QualityTier tier;
    public TrailEffect(long requestId, AnchorBinding binding, long startTick, int lifetimeTicks, QualityTier tier, TrailRules trailRules, AnchorRules rules) {
        super(requestId, binding, startTick, lifetimeTicks, rules, 20);
        this.trailRules = trailRules; this.history = new RingBufferTrailHistory(trailRules); this.tier = tier;
    }
    @Override public EffectKind kind() { return EffectKind.TRAIL; }
    public TrailHistory history() { return history; }
    @Override protected Optional<EffectDraft> draft(long tick, AnchorSnapshot s) {
        history.observe(s);
        List<TrailSample> live = history.live(tick);
        if (live.isEmpty()) return Optional.empty();          // stationary and expired: no request, effect stays alive
        TrailSample newest = live.getFirst();
        return Optional.of(new EffectDraft(EffectKind.TRAIL, requestId, owner, binding.ownerViewerId(), s.worldId(),
                Pose.ofTranslation(new Vec3(newest.x(), newest.y(), newest.z())),
                M8Recipes.trail(live, tick, trailRules.maxAgeTicks(), tier), tier,
                Playback.start(TimePoint.gameTick(tick)), TimePoint.gameTick(tick), age(tick)));
    }
    @Override protected void onDispose() { history.clear(); }
}
