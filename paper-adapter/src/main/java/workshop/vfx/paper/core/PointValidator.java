package workshop.vfx.paper.core;

import java.util.Optional;

/**
 * Pre-send point check (contract section 7, step 3): finite coordinates, y within
 * the world's height range, inside the world border. The Paper layer supplies world limits; tests inject a pure one.
 */
@FunctionalInterface
public interface PointValidator {
    Optional<SkipReason> check(int worldId, double x, double y, double z);
}
