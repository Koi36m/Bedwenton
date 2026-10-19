package workshop.vfx.paper.m8;

/** M8 R1 SPI PROPOSAL (not production). How an attached effect's local frame is oriented (anchor spec section 4). */
public enum OrientationMode {
    /** Local axes equal world axes; the owner's facing is ignored. */
    WORLD_FIXED,
    /** Local +Z follows the owner's body yaw (LivingEntity#getBodyYaw); pitch and roll are ignored. Default. */
    BODY_YAW,
    /** Local +Z follows the head yaw (Location#getYaw); pitch ignored. Not used by the showcases. */
    HEAD_YAW
}
