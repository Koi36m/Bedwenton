package workshop.vfx.scene;

import java.util.ArrayList;
import java.util.List;
import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Quat;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.curve.Easing;
import workshop.vfx.math.curve.LineSegment;
import workshop.vfx.math.curve.Spiral;
import workshop.vfx.math.sample.ArcLengthTable;
import workshop.vfx.math.sample.ArcLengthTolerance;
import workshop.vfx.math.sample.Endpoints;
import workshop.vfx.math.sample.SampleLimits;
import workshop.vfx.math.sample.SampleSet;
import workshop.vfx.math.sample.Sampler;
import workshop.vfx.math.time.TimeDomain;
import workshop.vfx.math.time.Timing;

/**
 * Shared helpers for the M5 tests: the four example scenes of M5-TEST-SPECIFICATION.md section 4, built only through
 * the proposed public API, and an independent oracle.
 *
 * The oracle maps points with 3x3 rotation matrices (from the pose quaternion, or Rodrigues' formula for spins, with
 * the angle 2 pi (phase + turns e) formed directly, without the contract's turn reduction) and never composes
 * quaternions or calls Pose.then. Progress comes from the accepted M2 Timing.sample and easing from the accepted M3
 * Easing.apply, because M5 is specified to reuse them.
 */
final class M5TestSupport {
    static final SampleLimits SAMPLE_LIMITS = new SampleLimits(1 << 20, 1 << 20);
    static final SceneLimits LIMITS = new SceneLimits(4096, 1 << 20);
    static final StyleKey STYLE = StyleKey.of("test:dot");

    private M5TestSupport() { }

    // ------------------------------------------------------------------ example scenes (specification section 4)

    static Shape circle(double radius, int n) {
        return Shape.of(Sampler.uniformParameter(Spiral.circle(radius), n, Endpoints.EXCLUDE_END, SAMPLE_LIMITS));
    }

    static EffectScene healing() {
        Shape ring = circle(1.0, 24);
        List<Layer> layers = new ArrayList<>();
        for (int k = 0; k < 3; k++) {
            Timing t = Timing.once(16).withDelay(20 * k);
            Motion m = Motion.translate(new Vec3(0, 0.1, 0), new Vec3(0, 1.0, 0), t, Easing.LINEAR).then(Motion.scale(0.5, 3.0, t, Easing.QUAD_OUT));
            layers.add(new Layer("pulse-" + k, t, Visibility.ACTIVE_ONLY, Reveal.ALL, m, ring, StyleKey.of("bedwenton:heal_ring")));
        }
        return EffectScene.of(TimeDomain.GAME, 60, layers, new SceneLimits(8, 256));
    }

    static EffectScene orbit() {
        Shape spark = Shape.point(new Vec3(1.5, 0, 0));
        List<Layer> layers = new ArrayList<>();
        for (int k = 0; k < 4; k++) {
            Motion m = Motion.spin(Vec3.Y, 1, k / 4.0, Timing.loop(40), Easing.LINEAR);
            layers.add(new Layer("spark-" + k, Timing.loop(40), Visibility.ACTIVE_ONLY, Reveal.ALL, m, spark, StyleKey.of("bedwenton:spark")));
        }
        for (int j = 1; j <= 3; j++) {
            Motion m = Motion.spin(Vec3.Y, 1, -3 * j / 100.0, Timing.loop(40), Easing.LINEAR);
            layers.add(new Layer("spark-0-trail-" + j, Timing.loop(40), Visibility.ACTIVE_ONLY, Reveal.ALL, m, spark, StyleKey.of("bedwenton:spark_trail")));
        }
        return EffectScene.of(TimeDomain.COSMETIC, EffectScene.UNBOUNDED, layers, new SceneLimits(16, 64));
    }

    static EffectScene trident() {
        ArcLengthTable table = ArcLengthTable.build(Spiral.helix(0.6, 2.0, 3), new ArcLengthTolerance(0, 0x1p-30), new SampleLimits(64, 4096));
        Shape helix = Shape.of(Sampler.uniformDistance(table, 37, Endpoints.INCLUDE_END, SAMPLE_LIMITS));
        Timing t = Timing.once(30);
        Motion m = Motion.fixed(Pose.ofTranslation(new Vec3(0, -1.0, 0))).then(Motion.spin(Vec3.Y, 2, 0, t, Easing.LINEAR));
        return EffectScene.of(TimeDomain.GAME, 30, List.of(new Layer("water-helix", t, Visibility.ACTIVE_ONLY, Reveal.PREFIX, m, helix, StyleKey.of("bedwenton:water"))), new SceneLimits(4, 64));
    }

    static EffectScene constellation() {
        SampleSet stars = Sampler.uniformParameter(Spiral.circle(2.0), 5, Endpoints.EXCLUDE_END, SAMPLE_LIMITS);
        Motion group = Motion.spin(Vec3.Y, 1, 0, Timing.loop(120), Easing.LINEAR).then(Motion.fixed(new Pose(Vec3.ZERO, new Quat(0.98, 0.2, 0.0, 0.0), 1)));
        List<Layer> layers = new ArrayList<>();
        layers.add(new Layer("stars", Timing.once(120), Visibility.ACTIVE_ONLY, Reveal.ALL, group, Shape.of(stars), StyleKey.of("astral:star")));
        for (int j = 0; j < 5; j++) {
            LineSegment edge = new LineSegment(stars.point(j), stars.point((j + 2) % 5));
            Shape link = Shape.of(Sampler.uniformParameter(edge, 9, Endpoints.INCLUDE_END, SAMPLE_LIMITS));
            layers.add(new Layer("link-" + j, Timing.once(10).withDelay(10 + 12 * j), Visibility.HOLD_AFTER, Reveal.PREFIX, group, link, StyleKey.of("astral:link")));
        }
        return EffectScene.of(TimeDomain.GAME, 120, layers, new SceneLimits(8, 64));
    }

    // ------------------------------------------------------------------ independent oracle

    /** One motion step, mirrored on the test side so the oracle can evaluate it without the implementation. */
    sealed interface Spec permits FixedSpec, SpinSpec, ScaleSpec, TranslateSpec {
        Motion motion();
    }

    record FixedSpec(Pose pose) implements Spec {
        public Motion motion() { return Motion.fixed(pose); }
    }

    record SpinSpec(Vec3 axis, double turns, double phase, Timing drive, Easing easing) implements Spec {
        public Motion motion() { return Motion.spin(axis, turns, phase, drive, easing); }
    }

    record ScaleSpec(double from, double to, Timing drive, Easing easing) implements Spec {
        public Motion motion() { return Motion.scale(from, to, drive, easing); }
    }

    record TranslateSpec(Vec3 from, Vec3 to, Timing drive, Easing easing) implements Spec {
        public Motion motion() { return Motion.translate(from, to, drive, easing); }
    }

    static Motion motionOf(List<Spec> specs) {
        Motion m = Motion.IDENTITY;
        for (Spec s : specs) {
            m = m.then(s.motion());
        }
        return m;
    }

    /** Row-major 3x3 matrix of a (not necessarily unit) quaternion, normalised first. */
    static double[] matrix(Quat q) {
        double n = Math.sqrt(q.w() * q.w() + q.x() * q.x() + q.y() * q.y() + q.z() * q.z());
        double w = q.w() / n, x = q.x() / n, y = q.y() / n, z = q.z() / n;
        return new double[] {1 - 2 * (y * y + z * z), 2 * (x * y - w * z), 2 * (x * z + w * y),
            2 * (x * y + w * z), 1 - 2 * (x * x + z * z), 2 * (y * z - w * x),
            2 * (x * z - w * y), 2 * (y * z + w * x), 1 - 2 * (x * x + y * y)};
    }

    /** Rodrigues' rotation matrix about {@code axis} by {@code turns} full turns (angle formed directly). */
    static double[] rodrigues(Vec3 axis, double turns) {
        double n = Math.sqrt(axis.x() * axis.x() + axis.y() * axis.y() + axis.z() * axis.z());
        double kx = axis.x() / n, ky = axis.y() / n, kz = axis.z() / n;
        double a = 2 * Math.PI * turns, c = StrictMath.cos(a), s = StrictMath.sin(a), v = 1 - c;
        return new double[] {c + kx * kx * v, kx * ky * v - kz * s, kx * kz * v + ky * s,
            ky * kx * v + kz * s, c + ky * ky * v, ky * kz * v - kx * s,
            kz * kx * v - ky * s, kz * ky * v + kx * s, c + kz * kz * v};
    }

    static double[] map(double[] t, double[] r, double s, double[] p) {
        return new double[] {t[0] + s * (r[0] * p[0] + r[1] * p[1] + r[2] * p[2]),
            t[1] + s * (r[3] * p[0] + r[4] * p[1] + r[5] * p[2]),
            t[2] + s * (r[6] * p[0] + r[7] * p[1] + r[8] * p[2])};
    }

    static final double[] I3 = {1, 0, 0, 0, 1, 0, 0, 0, 1};

    /** World position of a local point: anchor(m_0(m_1(...(p)))), the innermost step applied first. */
    static double[] oracle(Pose anchor, List<Spec> specs, double localTicks, Vec3 local) {
        double[] p = {local.x(), local.y(), local.z()};
        for (int i = specs.size() - 1; i >= 0; i--) {
            Spec s = specs.get(i);
            switch (s) {
                case FixedSpec f -> p = map(arr(f.pose().translation()), matrix(f.pose().rotation()), f.pose().scale(), p);
                case SpinSpec sp -> {
                    double e = sp.easing().apply(sp.drive().sample(localTicks).progress());
                    p = map(new double[3], rodrigues(sp.axis(), sp.phase() + sp.turns() * e), 1, p);
                }
                case ScaleSpec sc -> {
                    double e = sc.easing().apply(sc.drive().sample(localTicks).progress());
                    p = map(new double[3], I3, sc.from() + (sc.to() - sc.from()) * e, p);
                }
                case TranslateSpec tr -> {
                    double e = tr.easing().apply(tr.drive().sample(localTicks).progress());
                    Vec3 a = tr.from(), b = tr.to();
                    p = map(new double[] {a.x() + (b.x() - a.x()) * e, a.y() + (b.y() - a.y()) * e, a.z() + (b.z() - a.z()) * e}, I3, 1, p);
                }
            }
        }
        return map(arr(anchor.translation()), matrix(anchor.rotation()), anchor.scale(), p);
    }

    static double[] arr(Vec3 v) {
        return new double[] {v.x(), v.y(), v.z()};
    }

    /**
     * Validated tolerance for one coordinate (contract 10.3): 2^-40 relative to the magnitudes involved, plus the angle
     * error of a spin of {@code turns} full turns. The derived rounding bound is far smaller (about 1e-14 relative).
     */
    static double tolerance(double magnitude, double turns) {
        return 0x1p-40 * (1 + magnitude) * (1 + Math.abs(turns));
    }

    static double magnitude(double... v) {
        double m = 0;
        for (double x : v) {
            m = Math.max(m, Math.abs(x));
        }
        return m;
    }
}
