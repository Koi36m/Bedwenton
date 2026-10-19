package workshop.vfx.paper.m8;

import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Vec3;

/**
 * M8 R3 SPI PROPOSAL (not production). Pure anchor rules (anchor spec sections 3-5). Production: {@code DefaultAnchorRules}.
 * <ul>
 *   <li>{@code validate} (R3 F6, fail closed, checks in this order, first failure wins): null gives TERMINATE
 *       OWNER_UNRESOLVED; snapshot owner != bound owner gives TERMINATE OWNER_MISMATCH; snapshot viewer id != bound id,
 *       or the session no longer maps the bound id to the owner, gives TERMINATE VIEWER_ID_MISMATCH; offline, invalid or
 *       dead gives TERMINATE OWNER_GONE; snapshot tick != planning tick gives SKIP_TICK STALE_OR_FUTURE; null world,
 *       unknown world id or a world id not mapping back to the world UUID gives SKIP_TICK WORLD_INCONSISTENT;
 *       non-finite position or yaw gives SKIP_TICK NON_FINITE; y outside [minHeight, maxHeight) gives SKIP_TICK
 *       OUTSIDE_WORLD_HEIGHT; chunk not loaded gives SKIP_TICK CHUNK_NOT_LOADED.</li>
 *   <li>{@code pose}: translation = feet position + world-space {@code liftY}; rotation = rotation about +Y by
 *       {@code -yaw} computed with StrictMath (never Quat.axisAngle); scale 1. Local frame: +X left, +Y up, +Z forward
 *       (Minecraft's caret convention ^left ^up ^forward). Only called on a READY snapshot.</li>
 * </ul>
 */
public interface AnchorRules {
    AnchorVerdict validate(AnchorSnapshot snapshot, AnchorBinding expected, long tick);

    Pose pose(AnchorSnapshot snapshot, OrientationMode mode, double liftY);

    /** World-space position of a local offset under {@link #pose}; used by tests and by the trail's local frame. */
    Vec3 toWorld(AnchorSnapshot snapshot, OrientationMode mode, double liftY, Vec3 local);
}
