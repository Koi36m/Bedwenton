package workshop.vfx.delivery;

import workshop.vfx.math.core.Pose;
import workshop.vfx.math.time.Playback;
import workshop.vfx.math.time.TimePoint;

/** Validated immutable request; the caller supplies time, audience and ordinals. */
public final class DeliveryRequest {
    public static final int MAX_VIEWERS = 1 << 16;
    private final long id;
    private final EffectRecipe recipe;
    private final QualityTier tier;
    private final int worldId;
    private final Pose anchor;
    private final Playback playback;
    private final TimePoint now;
    private final long frameOrdinal;
    private final long[] viewers;

    private DeliveryRequest(long id, EffectRecipe recipe, QualityTier tier, int worldId, Pose anchor,
            Playback playback, TimePoint now, long frameOrdinal, long[] viewers) {
        this.id = id;
        this.recipe = recipe;
        this.tier = tier;
        this.worldId = worldId;
        this.anchor = anchor;
        this.playback = playback;
        this.now = now;
        this.frameOrdinal = frameOrdinal;
        this.viewers = viewers;
    }

    public static DeliveryRequest of(long id, EffectRecipe recipe, QualityTier tier, int worldId,
            Pose anchor, Playback playback, TimePoint now, long frameOrdinal, long[] viewerIds) {
        DeliveryChecks.nonnull(recipe, "DeliveryRequest.of: recipe");
        DeliveryChecks.nonnull(tier, "DeliveryRequest.of: tier");
        DeliveryChecks.nonnull(anchor, "DeliveryRequest.of: anchor");
        DeliveryChecks.nonnull(playback, "DeliveryRequest.of: playback");
        DeliveryChecks.nonnull(now, "DeliveryRequest.of: now");
        DeliveryChecks.nonnull(viewerIds, "DeliveryRequest.of: viewerIds");
        if (worldId < 0 || playback.anchor().domain() != recipe.domain() || now.domain() != recipe.domain()) {
            throw new IllegalArgumentException("DeliveryRequest.of: world must be nonnegative and time domains must match");
        }
        if (viewerIds.length > MAX_VIEWERS) {
            throw new IllegalArgumentException("DeliveryRequest.of: viewer count exceeds MAX_VIEWERS");
        }
        long[] copy = viewerIds.clone();
        long previous = -1;
        for (long viewer : copy) {
            if (viewer < 0 || viewer <= previous) {
                throw new IllegalArgumentException("DeliveryRequest.of: viewer ids must be nonnegative and strictly increasing");
            }
            previous = viewer;
        }
        return new DeliveryRequest(id, recipe, tier, worldId, anchor, playback, now, frameOrdinal, copy);
    }

    public long id() { return id; }
    public EffectRecipe recipe() { return recipe; }
    public QualityTier tier() { return tier; }
    public int worldId() { return worldId; }
    public Pose anchor() { return anchor; }
    public Playback playback() { return playback; }
    public TimePoint now() { return now; }
    public long frameOrdinal() { return frameOrdinal; }
    public int viewerCount() { return viewers.length; }
    public long viewer(int index) { return viewers[index]; }
}
