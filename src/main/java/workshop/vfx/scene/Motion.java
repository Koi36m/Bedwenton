package workshop.vfx.scene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Quat;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.curve.Easing;
import workshop.vfx.math.time.Timing;

/**
 * A shareable, time-dependent local-to-parent pose: an ordered chain of at most {@link #MAX_STEPS} steps, each
 * animated step driven by its own M2 {@link Timing} through an M3 {@link Easing} (contract section 5).
 */
public final class Motion {
    public static final int MAX_STEPS = 16;
    public static final double MAX_TURNS = 1 << 20;
    public static final double MIN_SCALE = 1e-9;
    public static final double MAX_SCALE = 1e9;
    public static final Motion IDENTITY = new Motion(List.of());

    private sealed interface Step permits Fixed, Spin, Scale, Translate { }

    private record Fixed(Pose pose) implements Step { }

    private record Spin(Vec3 unitAxis, double turns, double phaseTurns, Timing drive, Easing easing) implements Step { }

    private record Scale(double from, double to, Timing drive, Easing easing) implements Step { }

    private record Translate(Vec3 from, Vec3 to, Timing drive, Easing easing) implements Step { }

    private final List<Step> steps;

    private Motion(List<Step> steps) {
        this.steps = steps;
    }

    public static Motion fixed(Pose pose) {
        return new Motion(List.of(new Fixed(SceneChecks.nonnull(pose, "Motion.fixed: pose"))));
    }

    public static Motion spin(Vec3 axis, double turns, double phaseTurns, Timing drive, Easing easing) {
        SceneChecks.nonnull(axis, "Motion.spin: axis");
        Vec3 unit = axis.normalizedOr(null);
        if (unit == null) {
            throw new IllegalArgumentException("Motion.spin: finite axis with a component of magnitude at least 1e-12 required");
        }
        if (!Double.isFinite(turns) || Math.abs(turns) > MAX_TURNS || !Double.isFinite(phaseTurns)) {
            throw new IllegalArgumentException("Motion.spin: finite turns with |turns| <= 2^20 and finite phase required");
        }
        return new Motion(List.of(new Spin(unit, turns, phaseTurns,
                SceneChecks.nonnull(drive, "Motion.spin: drive"), SceneChecks.nonnull(easing, "Motion.spin: easing"))));
    }

    public static Motion scale(double from, double to, Timing drive, Easing easing) {
        if (!(from >= MIN_SCALE && from <= MAX_SCALE && to >= MIN_SCALE && to <= MAX_SCALE)) {
            throw new IllegalArgumentException("Motion.scale: scales in [1e-9, 1e9] required");
        }
        return new Motion(List.of(new Scale(from, to,
                SceneChecks.nonnull(drive, "Motion.scale: drive"), SceneChecks.nonnull(easing, "Motion.scale: easing"))));
    }

    public static Motion translate(Vec3 from, Vec3 to, Timing drive, Easing easing) {
        SceneChecks.nonnull(from, "Motion.translate: from");
        SceneChecks.nonnull(to, "Motion.translate: to");
        if (!from.isFinite() || !to.isFinite()) {
            throw new IllegalArgumentException("Motion.translate: finite endpoints required");
        }
        return new Motion(List.of(new Translate(from, to,
                SceneChecks.nonnull(drive, "Motion.translate: drive"), SceneChecks.nonnull(easing, "Motion.translate: easing"))));
    }

    /** The steps of this motion followed by those of {@code child}: the child acts first on a point. */
    public Motion then(Motion child) {
        SceneChecks.nonnull(child, "Motion.then: child");
        if (steps.size() + child.steps.size() > MAX_STEPS) {
            throw new IllegalArgumentException("Motion.then: more than MAX_STEPS steps");
        }
        List<Step> all = new ArrayList<>(steps.size() + child.steps.size());
        all.addAll(steps);
        all.addAll(child.steps);
        return new Motion(Collections.unmodifiableList(all));
    }

    public int steps() {
        return steps.size();
    }

    /** {@code P_0.then(P_1)...then(P_last)}, folded left to right; {@link Pose#IDENTITY} without steps. */
    public Pose poseAt(double localTicks) {
        if (!Double.isFinite(localTicks)) {
            throw new IllegalArgumentException("Motion.poseAt: finite localTicks required");
        }
        Pose pose = null;
        for (Step step : steps) {
            Pose p = stepPose(step, localTicks);
            pose = pose == null ? p : pose.then(p);
        }
        return pose == null ? Pose.IDENTITY : pose;
    }

    private static Pose stepPose(Step step, double localTicks) {
        return switch (step) {
            case Fixed f -> f.pose();
            case Spin s -> spinPose(s, eased(s.drive(), s.easing(), localTicks));
            case Scale s -> {
                double e = eased(s.drive(), s.easing(), localTicks);
                yield new Pose(Vec3.ZERO, Quat.IDENTITY, (1 - e) * s.from() + e * s.to());
            }
            case Translate t -> Pose.ofTranslation(t.from().lerp(t.to(), eased(t.drive(), t.easing(), localTicks)));
        };
    }

    private static double eased(Timing drive, Easing easing, double localTicks) {
        return easing.apply(drive.sample(localTicks).progress());
    }

    /** Contract 5.2: rotation by 2 pi (phase + turns e) about the unit axis, from turn-reduced StrictMath values. */
    private static Pose spinPose(Spin s, double e) {
        double f = fraction(fraction(s.turns() * e) + fraction(s.phaseTurns()));
        double quarters = 2 * f; // the half angle pi f, in quarter turns: [0, 2)
        int q = (int) Math.floor(quarters);
        double x = (quarters - q) * (Math.PI / 2);
        double c = StrictMath.cos(x);
        double sin = StrictMath.sin(x);
        double cw = q == 0 ? c : -sin;
        double sw = q == 0 ? sin : c;
        Vec3 u = s.unitAxis();
        return new Pose(Vec3.ZERO, new Quat(cw, sw * u.x(), sw * u.y(), sw * u.z()), 1);
    }

    /** {@code x % 1.0}, plus 1.0 if negative, with 1.0 mapped to 0.0: a value in [0, 1). */
    private static double fraction(double x) {
        double f = x % 1.0;
        if (f < 0) {
            f += 1.0;
        }
        return f == 1.0 ? 0.0 : f;
    }
}
