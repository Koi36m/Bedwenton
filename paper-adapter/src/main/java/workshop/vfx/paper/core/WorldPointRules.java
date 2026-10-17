package workshop.vfx.paper.core;
import java.util.Optional;
import java.util.function.BooleanSupplier;
/** Testable world-boundary rule. The platform supplies a border query with no chunk loading. */
public final class WorldPointRules {
    private WorldPointRules() { }
    public static Optional<SkipReason> check(double x, double y, double z, int minHeight, int maxHeight, BooleanSupplier borderInside) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return Optional.of(SkipReason.POINT_NOT_FINITE);
        if (y < minHeight || y >= maxHeight || !borderInside.getAsBoolean()) return Optional.of(SkipReason.POINT_OUTSIDE_WORLD);
        return Optional.empty();
    }
}
