package workshop.vfx.paper.m8;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import workshop.vfx.delivery.EffectRecipe;
import workshop.vfx.delivery.QualityTier;
import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Quat;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.time.Playback;
import workshop.vfx.math.time.TimePoint;
import workshop.vfx.paper.core.ThreadGuard;

/** Player-only, body-yaw attachment. No retained platform objects, history, task or clock reads. */
public final class AuraEffect implements DynamicEffect {
    private final ThreadGuard thread;
    private final AnchorIdentityGuard guard;
    private final AnchorBinding binding;
    private final long id, startTick;
    private final int lifetime;
    private final EffectRecipe recipe;
    private final QualityTier tier;
    private final Playback playback;
    private boolean disposed;

    // Session-owned construction ensures the configured lifetime and shared recipe cannot disagree.
    AuraEffect(ThreadGuard thread, AnchorIdentityGuard guard, long id, AnchorBinding binding,
               long startTick, int lifetime, EffectRecipe recipe, QualityTier tier) {
        this.thread = Objects.requireNonNull(thread); this.guard = Objects.requireNonNull(guard);
        this.binding = Objects.requireNonNull(binding); this.recipe = Objects.requireNonNull(recipe);
        this.tier = Objects.requireNonNull(tier);
        if (id < DynamicEffectRegistry.FIRST_ID || lifetime < 20 || lifetime > 12000)
            throw new IllegalArgumentException("Invalid aura identity or lifetime");
        this.id = id; this.startTick = startTick; this.lifetime = lifetime;
        playback = Playback.start(TimePoint.gameTick(startTick));
    }

    @Override public long requestId() { thread.check(); return id; }
    @Override public UUID owner() { thread.check(); return binding.owner(); }
    @Override public EffectKind kind() { thread.check(); return EffectKind.AURA; }
    public QualityTier tier() { thread.check(); return tier; }
    @Override public boolean finished(long tick) {
        thread.check();
        return disposed || tick < startTick || tick - startTick >= lifetime;
    }
    @Override public Optional<EffectDraft> contribute(long tick, AnchorSnapshot snapshot) {
        thread.check();
        if (finished(tick) || guard.validate(snapshot, binding, tick).state() != AnchorState.READY)
            return Optional.empty();
        // Normative Paper yaw: +Z forward -> (-sin(yaw), 0, cos(yaw)); pitch is deliberately unused.
        double half = -StrictMath.toRadians(snapshot.bodyYawDegrees()) / 2;
        Pose anchor = new Pose(new Vec3(snapshot.x(), snapshot.y(), snapshot.z()),
            new Quat(StrictMath.cos(half), 0, StrictMath.sin(half), 0), 1);
        return Optional.of(new EffectDraft(EffectKind.AURA, id, binding.owner(), binding.ownerViewerId(),
            snapshot.worldId(), anchor, recipe, tier, playback, TimePoint.gameTick(tick), tick - startTick));
    }
    @Override public void dispose() { thread.check(); disposed = true; }
}
