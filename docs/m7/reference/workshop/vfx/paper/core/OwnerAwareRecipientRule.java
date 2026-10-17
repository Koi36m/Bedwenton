package workshop.vfx.paper.core;

import java.util.Optional;

/**
 * M7 R3 THROWAWAY REFERENCE (design verification only; not production). Owner-aware recipient rule, failing closed on
 * invalid coordinates (contract section 7, step 1).
 */
public final class OwnerAwareRecipientRule implements RecipientRule {
    private final double radiusSquared;

    public OwnerAwareRecipientRule(double radius) {
        if (!Double.isFinite(radius) || radius < 0) {
            throw new IllegalArgumentException("view radius must be finite and >= 0: " + radius);
        }
        this.radiusSquared = radius * radius;
    }

    @Override
    public Optional<SkipReason> decide(RequestContext c, long viewerId, ViewerFacts f) {
        if (f == null || !f.online() || !f.valid()) return Optional.of(SkipReason.VIEWER_OFFLINE);
        if (f.worldId() != c.worldId()) return Optional.of(SkipReason.VIEWER_WORLD_CHANGED);
        boolean allowed = viewerId == c.ownerViewerId() ? f.hasUsePermission() : f.hasViewPermission();
        if (!allowed) return Optional.of(SkipReason.VIEWER_PERMISSION_REVOKED);
        if (!finite(c.anchorX(), c.anchorY(), c.anchorZ())) return Optional.of(SkipReason.INVALID_REQUEST_CONTEXT);
        if (!finite(f.x(), f.y(), f.z())) return Optional.of(SkipReason.VIEWER_POSITION_INVALID);
        double dx = f.x() - c.anchorX(), dy = f.y() - c.anchorY(), dz = f.z() - c.anchorZ();
        double d2 = dx * dx + dy * dy + dz * dz;
        if (!(d2 <= radiusSquared)) return Optional.of(SkipReason.VIEWER_OUT_OF_RANGE); // NaN denies
        return Optional.empty();
    }

    static boolean finite(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }
}
