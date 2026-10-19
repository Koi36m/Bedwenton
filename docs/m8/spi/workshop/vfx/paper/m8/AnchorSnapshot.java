package workshop.vfx.paper.m8;

import java.util.UUID;

/**
 * M8 R3 SPI PROPOSAL (not production). One owner's state as observed on the main thread during the tick's snapshot
 * phase, before planning (anchor spec section 3). Plain, untrusted carrier: {@link AnchorRules#validate} re-checks every
 * identity and freshness invariant against the effect's {@link AnchorBinding} before any field is used (R3 F6).
 *
 * @param tick            Bukkit.getCurrentTick() of the observation; must equal the planning tick
 * @param owner           Player#getUniqueId of the resolved player; must equal the bound owner
 * @param ownerViewerId   M7 SessionIds viewer id of the owner; must equal the id bound at start
 * @param world           the owner's current world UUID (World#getUID)
 * @param worldId         M7 SessionIds world id of {@code world}; must map back to {@code world}
 * @param x               feet position (Entity#getLocation); finite when valid
 * @param bodyYawDegrees  LivingEntity#getBodyYaw, any finite float, normalised by AnchorRules
 * @param headYawDegrees  Location#getYaw
 * @param pitchDegrees    Location#getPitch, -90 (up) to 90 (down); recorded, never used to orient showcases
 * @param minHeight       World#getMinHeight (inclusive)
 * @param maxHeight       World#getMaxHeight (exclusive), as in M7 WorldPointRules
 * @param chunkLoaded     World#isChunkLoaded(blockX &gt;&gt; 4, blockZ &gt;&gt; 4) at the feet position; never loads a chunk
 * @param online          Player#isOnline
 * @param valid           Entity#isValid
 * @param dead            Entity#isDead
 * @param discontinuitySerial incremented by the Paper layer on every teleport, world change and respawn of the owner
 */
public record AnchorSnapshot(long tick, UUID owner, long ownerViewerId, UUID world, int worldId,
        double x, double y, double z, float bodyYawDegrees, float headYawDegrees, float pitchDegrees,
        int minHeight, int maxHeight, boolean chunkLoaded,
        boolean online, boolean valid, boolean dead, long discontinuitySerial) { }
