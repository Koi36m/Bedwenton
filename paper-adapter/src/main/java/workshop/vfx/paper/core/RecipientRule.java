package workshop.vfx.paper.core;

import java.util.Optional;

/**
 * The pure, owner-aware authorization rule behind {@link RecipientGate}
 * (contract section 7). Production: {@code OwnerAwareRecipientRule(viewRadius)}, whose constructor rejects a radius
 * that is not finite or is negative. Checks, in order; the first failure is the result, and every failure denies:
 * <ol>
 *   <li>facts present, online and valid, else {@code VIEWER_OFFLINE};</li>
 *   <li>same world as the request, else {@code VIEWER_WORLD_CHANGED};</li>
 *   <li>permission: the owner needs the use permission, any other viewer the view permission, else
 *       {@code VIEWER_PERMISSION_REVOKED};</li>
 *   <li>range, failing closed: a non-finite anchor coordinate gives {@code INVALID_REQUEST_CONTEXT}; a non-finite viewer
 *       coordinate gives {@code VIEWER_POSITION_INVALID}; otherwise the squared distance must satisfy
 *       {@code d2 <= radius * radius} (written so that NaN denies), else {@code VIEWER_OUT_OF_RANGE}.</li>
 * </ol>
 */
@FunctionalInterface
public interface RecipientRule {
    Optional<SkipReason> decide(RequestContext context, long viewerId, ViewerFacts facts);
}
