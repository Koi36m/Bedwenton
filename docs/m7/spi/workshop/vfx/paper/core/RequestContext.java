package workshop.vfx.paper.core;

/**
 * M7 R3 SPI PROPOSAL (not production). Everything the emitter and gate need to know about one planned request,
 * captured on the main thread at planning time and keyed by {@code requestId} (= the M6 {@code DeliveryRequest.id()}).
 * {@code ownerViewerId} is the session viewer id of the player who started the effect. The record is a plain carrier;
 * consumers validate it (contract section 7): a delivery is never emitted when its context is missing
 * ({@link SkipReason#MISSING_REQUEST_CONTEXT}), carries another request id ({@link SkipReason#MISMATCHED_REQUEST_CONTEXT}),
 * or has a non-finite anchor or a negative owner or world id ({@link SkipReason#INVALID_REQUEST_CONTEXT}).
 * Nothing is substituted, reconstructed or inferred.
 */
public record RequestContext(long requestId, long ownerViewerId, int worldId, double anchorX, double anchorY, double anchorZ) { }
