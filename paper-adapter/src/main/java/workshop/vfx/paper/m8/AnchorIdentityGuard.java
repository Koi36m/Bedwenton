package workshop.vfx.paper.m8;

import java.util.Objects;
import java.util.UUID;
import workshop.vfx.paper.core.SessionIds;

/** Ordered R3 identity and freshness guard; reads mappings without creating or repairing identities. */
public final class AnchorIdentityGuard {
    private final workshop.vfx.paper.core.ThreadGuard thread;
    private final SessionIds ids;
    public AnchorIdentityGuard(SessionIds ids, workshop.vfx.paper.core.ThreadGuard thread) { this.ids = Objects.requireNonNull(ids); this.thread = Objects.requireNonNull(thread); }

    public AnchorVerdict validate(AnchorSnapshot s, AnchorBinding expected, long tick) {
        thread.check(); Objects.requireNonNull(expected);
        if (s == null) return AnchorVerdict.terminate(AnchorRejection.OWNER_UNRESOLVED);
        if (!expected.owner().equals(s.owner())) return AnchorVerdict.terminate(AnchorRejection.OWNER_MISMATCH);
        if (s.ownerViewerId() != expected.ownerViewerId() || !expected.owner().equals(ids.viewer(expected.ownerViewerId())))
            return AnchorVerdict.terminate(AnchorRejection.VIEWER_ID_MISMATCH);
        if (!s.online() || !s.valid() || s.dead()) return AnchorVerdict.terminate(AnchorRejection.OWNER_GONE);
        if (s.tick() != tick) return AnchorVerdict.skip(AnchorRejection.STALE_OR_FUTURE);
        UUID mapped = s.worldId() < 0 ? null : ids.world(s.worldId());
        if (s.world() == null || !s.world().equals(mapped)) return AnchorVerdict.skip(AnchorRejection.WORLD_INCONSISTENT);
        if (!Double.isFinite(s.x()) || !Double.isFinite(s.y()) || !Double.isFinite(s.z())
                || !Float.isFinite(s.bodyYawDegrees()) || !Float.isFinite(s.headYawDegrees()))
            return AnchorVerdict.skip(AnchorRejection.NON_FINITE);
        if (s.minHeight() >= s.maxHeight() || s.y() < s.minHeight() || s.y() >= s.maxHeight())
            return AnchorVerdict.skip(AnchorRejection.OUTSIDE_WORLD_HEIGHT);
        if (!s.chunkLoaded()) return AnchorVerdict.skip(AnchorRejection.CHUNK_NOT_LOADED);
        return AnchorVerdict.READY;
    }
}
