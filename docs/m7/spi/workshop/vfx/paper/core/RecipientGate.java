package workshop.vfx.paper.core;

import java.util.Optional;

/**
 * M7 R3 SPI PROPOSAL (not production). The Paper layer re-checks a planned recipient immediately before sending,
 * on the main thread, against the request it belongs to. The rule (contract section 7) is owner-aware: the owner
 * ({@code viewerId == context.ownerViewerId()}) needs {@code bedwenton.vfxdemo.use}; any other viewer needs
 * {@code bedwenton.vfxdemo.view}. Both must be online, valid, in {@code context.worldId()} and within the view radius
 * of the anchor. An empty result means the recipient may receive this request's points.
 */
@FunctionalInterface
public interface RecipientGate {
    Optional<SkipReason> check(RequestContext context, long viewerId);
}
