package workshop.vfx.paper.core;

/**
 * M7 R3 SPI PROPOSAL (not production). What the Paper gate observed about one viewer immediately before sending:
 * online and valid state, current world id and position, and the two demo permissions. Gathered on the main thread.
 */
public record ViewerFacts(boolean online, boolean valid, int worldId, double x, double y, double z,
        boolean hasUsePermission, boolean hasViewPermission) { }
