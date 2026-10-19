package workshop.vfx.paper.m8;

import java.util.UUID;
import workshop.vfx.delivery.EffectRecipe;
import workshop.vfx.delivery.QualityTier;
import workshop.vfx.math.core.Pose;
import workshop.vfx.math.time.Playback;
import workshop.vfx.math.time.TimePoint;

/**
 * M8 R2 SPI PROPOSAL (not production). One effect's contribution to a tick, before viewer selection (R2: replaces the
 * R1 TickRequest). The driver selects viewers with the same M7 owner-first rule it uses for rings, then builds the M6
 * DeliveryRequest and the RequestContext from these fields, so both share one request id and one anchor.
 *
 * @param owner        the effect's bound owner ({@link AnchorBinding}); never copied from a snapshot (R3 F6)
 * @param ownerViewerId the bound owner viewer id; the registry and the driver drop a draft whose id does not match
 * @param playback     the scene clock (for the trail: restarted every tick; anchor spec section 5.1)
 * @param frameOrdinal the effect clock: tick - effect start tick (drives M6 thinning phase); never the scene clock
 */
public record EffectDraft(EffectKind kind, long requestId, UUID owner, long ownerViewerId, int worldId, Pose anchor,
        EffectRecipe recipe, QualityTier tier, Playback playback, TimePoint now, long frameOrdinal) { }
