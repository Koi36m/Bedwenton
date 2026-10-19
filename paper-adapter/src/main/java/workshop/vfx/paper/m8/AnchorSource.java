package workshop.vfx.paper.m8;

import java.util.UUID;

/** K0 foundation. Paper-layer snapshot reader (main thread); null when the owner cannot be resolved. */
@FunctionalInterface
public interface AnchorSource {
    AnchorSnapshot snapshot(UUID owner, long tick);
}
