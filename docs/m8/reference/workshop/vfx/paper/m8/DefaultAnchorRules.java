package workshop.vfx.paper.m8;

import java.util.Objects;
import java.util.UUID;
import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Quat;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.paper.core.SessionIds;

/** M8 R3 THROWAWAY REFERENCE (design verification only; never production). Fail-closed validation order: AnchorRules. */
public final class DefaultAnchorRules implements AnchorRules {
    private final SessionIds ids;
    public DefaultAnchorRules(SessionIds ids) { this.ids = Objects.requireNonNull(ids); }

    @Override public AnchorVerdict validate(AnchorSnapshot s, AnchorBinding expected, long tick) {
        Objects.requireNonNull(expected);
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
    static Quat yaw(double degrees) {
        double half = -StrictMath.toRadians(degrees) / 2;
        return new Quat(StrictMath.cos(half), 0, StrictMath.sin(half), 0);
    }
    @Override public Pose pose(AnchorSnapshot s, OrientationMode mode, double liftY) {
        double yaw = switch (mode) { case WORLD_FIXED -> 0; case BODY_YAW -> s.bodyYawDegrees(); case HEAD_YAW -> s.headYawDegrees(); };
        return new Pose(new Vec3(s.x(), s.y() + liftY, s.z()), yaw(yaw), 1);
    }
    @Override public Vec3 toWorld(AnchorSnapshot s, OrientationMode mode, double liftY, Vec3 local) {
        return pose(s, mode, liftY).toWorld(local);
    }
}
