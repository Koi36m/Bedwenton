package workshop.vfx.math.curve;

import workshop.vfx.math.core.Vec3;

/** Shared validation; finite extrapolation is deliberately allowed. */
final class CurveChecks {
    private CurveChecks() { }

    static void control(Vec3 value) {
        if (value == null || !value.isFinite()) {
            throw new IllegalArgumentException("Curve: control point must be nonnull and finite");
        }
    }

    static void parameter(double t) {
        if (!Double.isFinite(t)) {
            throw new IllegalArgumentException("Curve evaluation: parameter must be finite");
        }
    }
}
