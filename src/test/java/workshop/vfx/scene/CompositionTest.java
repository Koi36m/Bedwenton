package workshop.vfx.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static workshop.vfx.scene.M5TestSupport.LIMITS;
import static workshop.vfx.scene.M5TestSupport.STYLE;

import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Quat;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.curve.Easing;
import workshop.vfx.math.time.Playback;
import workshop.vfx.math.time.TimeDomain;
import workshop.vfx.math.time.TimePoint;
import workshop.vfx.math.time.Timing;
import workshop.vfx.scene.M5TestSupport.FixedSpec;
import workshop.vfx.scene.M5TestSupport.ScaleSpec;
import workshop.vfx.scene.M5TestSupport.Spec;
import workshop.vfx.scene.M5TestSupport.SpinSpec;
import workshop.vfx.scene.M5TestSupport.TranslateSpec;

/** Contract section 5 (motion) and 8.3 (composition): parent-first composition, exact endpoints, finite results. */
class CompositionTest {
    private static Playback paused(double local) {
        return new Playback(TimePoint.gameTick(0), local, 1, true);
    }

    private static EffectFrame frame(Pose anchor, Motion m, Shape shape, double local) {
        EffectScene s = EffectScene.of(TimeDomain.GAME, EffectScene.UNBOUNDED,
            List.of(new Layer("a", Timing.once(1e9), Visibility.ACTIVE_ONLY, Reveal.ALL, m, shape, STYLE)), LIMITS);
        return s.evaluate(anchor, paused(local), TimePoint.gameTick(0));
    }

    private static void near(double[] expected, FrameBatch b, int i, double turns, String label) {
        double tol = M5TestSupport.tolerance(M5TestSupport.magnitude(expected[0], expected[1], expected[2], b.x(i), b.y(i), b.z(i)), turns);
        assertEquals(expected[0], b.x(i), tol, label + " x");
        assertEquals(expected[1], b.y(i), tol, label + " y");
        assertEquals(expected[2], b.z(i), tol, label + " z");
    }

    @Test
    @DisplayName("Composition order: m.then(child) applies the child first (M1 Pose.then); translate-then-spin differs from spin-then-translate; both match the independent matrix oracle")
    void order() {
        Timing drive = Timing.once(10);
        List<Spec> ts = List.of(new TranslateSpec(Vec3.ZERO, new Vec3(2, 0, 0), drive, Easing.LINEAR), new SpinSpec(Vec3.Y, 0.25, 0, drive, Easing.LINEAR));
        List<Spec> st = List.of(ts.get(1), ts.get(0));
        Shape p = Shape.point(new Vec3(1, 0, 0));
        Pose anchor = new Pose(new Vec3(5, 6, 7), new Quat(0.9, 0.1, -0.3, 0.2), 1.5);
        for (double local : new double[] {0, 2.5, 5, 10}) {
            FrameBatch a = frame(anchor, M5TestSupport.motionOf(ts), p, local).batches().get(0);
            FrameBatch b = frame(anchor, M5TestSupport.motionOf(st), p, local).batches().get(0);
            near(M5TestSupport.oracle(anchor, ts, local, new Vec3(1, 0, 0)), a, 0, 0.25, "translate.then(spin) at " + local);
            near(M5TestSupport.oracle(anchor, st, local, new Vec3(1, 0, 0)), b, 0, 0.25, "spin.then(translate) at " + local);
            if (local > 0) {
                assertNotEquals(a.x(0), b.x(0), "order matters at " + local);
            }
        }
    }

    @Test
    @DisplayName("Spin: right-handed about the axis by 2 pi (phase + turns e); a quarter turn about +Y maps +X to -Z; whole turns and negative turns; equals the M1 Quat.axisAngle rotation within the tolerance")
    void spin() {
        Shape x = Shape.point(Vec3.X);
        Timing drive = Timing.once(4);
        FrameBatch q = frame(Pose.IDENTITY, Motion.spin(Vec3.Y, 1, 0, drive, Easing.LINEAR), x, 1).batches().get(0);
        assertEquals(0, q.x(0), 1e-15);
        assertEquals(0, q.y(0), 0);
        assertEquals(-1, q.z(0), 1e-15);
        FrameBatch whole = frame(Pose.IDENTITY, Motion.spin(Vec3.Y, 7, 0, drive, Easing.LINEAR), x, 4).batches().get(0);
        assertEquals(1, whole.x(0), 1e-15);
        assertEquals(0, whole.z(0), 1e-15);
        FrameBatch neg = frame(Pose.IDENTITY, Motion.spin(Vec3.Y, -1, 0, drive, Easing.LINEAR), x, 1).batches().get(0);
        assertEquals(1, neg.z(0), 1e-15);
        FrameBatch phase = frame(Pose.IDENTITY, Motion.spin(Vec3.Z, 0, 0.5, drive, Easing.LINEAR), x, 3).batches().get(0);
        assertEquals(-1, phase.x(0), 1e-15);
        SplittableRandom r = new SplittableRandom(51);
        for (int i = 0; i < 2_000; i++) {
            Vec3 axis = new Vec3(r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1));
            if (axis.length() < 1e-3) {
                continue;
            }
            double turns = r.nextDouble(-50, 50), phaseTurns = r.nextDouble(-3, 3), local = r.nextDouble(0, 4);
            Vec3 p = new Vec3(r.nextDouble(-5, 5), r.nextDouble(-5, 5), r.nextDouble(-5, 5));
            FrameBatch b = frame(Pose.IDENTITY, Motion.spin(axis, turns, phaseTurns, drive, Easing.SMOOTHSTEP), Shape.point(p), local).batches().get(0);
            double e = Easing.SMOOTHSTEP.apply(drive.sample(local).progress());
            Vec3 m1 = Quat.axisAngle(axis, 2 * Math.PI * (phaseTurns + turns * e)).rotate(p);
            double tol = 1e-12 * (1 + Math.abs(turns)) * (1 + p.length());
            assertEquals(m1.x(), b.x(0), tol);
            assertEquals(m1.y(), b.y(0), tol);
            assertEquals(m1.z(), b.z(0), tol);
        }
    }

    @Test
    @DisplayName("Scale and translate: exact 'from' at progress 0 and exact 'to' at progress 1 (and after a ONCE drive); easing is applied to the drive's progress")
    void endpoints() {
        Timing drive = Timing.once(8);
        Shape p = Shape.point(new Vec3(0.1, 0.2, 0.3));
        Motion scale = Motion.scale(0.37, 2.9, drive, Easing.QUAD_OUT);
        assertEquals(0.37, frame(Pose.IDENTITY, scale, p, 0).batches().get(0).pose().scale());
        assertEquals(2.9, frame(Pose.IDENTITY, scale, p, 8).batches().get(0).pose().scale());
        assertEquals(2.9, frame(Pose.IDENTITY, scale, p, 1e6).batches().get(0).pose().scale());
        double e = Easing.QUAD_OUT.apply(0.25);
        assertEquals((1 - e) * 0.37 + e * 2.9, frame(Pose.IDENTITY, scale, p, 2).batches().get(0).pose().scale());
        Vec3 from = new Vec3(-1.1, 2.2, 3.3), to = new Vec3(4.4, -5.5, 6.6);
        Motion tr = Motion.translate(from, to, drive, Easing.SMOOTHERSTEP);
        assertEquals(from, tr.poseAt(0).translation());
        assertEquals(to, tr.poseAt(8).translation());
        assertEquals(from, tr.poseAt(-3).translation(), "BEFORE: progress 0");
        assertEquals(from.lerp(to, Easing.SMOOTHERSTEP.apply(0.375)), tr.poseAt(3).translation());
    }

    @Test
    @DisplayName("The frame's pose is anchor.then(motion.poseAt(local)) and every point is pose.toWorld(local point), bit for bit (normative order)")
    void normativeOrder() {
        SplittableRandom r = new SplittableRandom(52);
        for (int i = 0; i < 300; i++) {
            Timing drive = Timing.pingPong(r.nextDouble(1, 20)).withDelay(r.nextDouble(-5, 5));
            Motion m = Motion.translate(new Vec3(r.nextDouble(), r.nextDouble(), r.nextDouble()), new Vec3(r.nextDouble(), 2, 3), drive, Easing.CUBIC_IN_OUT)
                .then(Motion.spin(new Vec3(r.nextDouble(-1, 1), 1, r.nextDouble(-1, 1)), r.nextDouble(-4, 4), r.nextDouble(), drive, Easing.LINEAR))
                .then(Motion.scale(r.nextDouble(0.1, 3), r.nextDouble(0.1, 3), drive, Easing.SMOOTHSTEP))
                .then(Motion.fixed(new Pose(new Vec3(1, -1, 0.5), new Quat(r.nextDouble(-1, 1), r.nextDouble(-1, 1), 0.3, 0.1), r.nextDouble(0.5, 2))));
            Pose anchor = new Pose(new Vec3(r.nextDouble(-1e4, 1e4), r.nextDouble(0, 300), r.nextDouble(-1e4, 1e4)), new Quat(r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1), 0.5), r.nextDouble(0.25, 4));
            Shape shape = M5TestSupport.circle(r.nextDouble(0.1, 5), 12);
            double local = r.nextDouble(0, 100);
            FrameBatch b = frame(anchor, m, shape, local).batches().get(0);
            Pose expected = anchor.then(m.poseAt(local));
            assertEquals(expected, b.pose());
            for (int k = 0; k < shape.size(); k++) {
                Vec3 w = expected.toWorld(shape.point(k));
                assertEquals(w, b.point(k));
                assertEquals(w.x(), b.x(k));
                assertEquals(w.y(), b.y(k));
                assertEquals(w.z(), b.z(k));
            }
        }
    }

    @Test
    @DisplayName("Shared motions: one Motion value reused by layers with different visibility timings gives the same pose in every layer (transform grouping)")
    void sharedMotion() {
        Motion group = Motion.spin(Vec3.Y, 1, 0, Timing.loop(30), Easing.LINEAR).then(Motion.fixed(Pose.ofTranslation(new Vec3(0, 1, 0))));
        Shape dot = Shape.point(new Vec3(2, 0, 0));
        EffectScene s = EffectScene.of(TimeDomain.GAME, 100, List.of(
            new Layer("a", Timing.once(100), Visibility.ACTIVE_ONLY, Reveal.ALL, group, dot, STYLE),
            new Layer("b", Timing.once(5).withDelay(20), Visibility.HOLD_AFTER, Reveal.ALL, group, dot, STYLE),
            new Layer("c", Timing.pingPong(3), Visibility.ACTIVE_ONLY, Reveal.ALL, group, dot, STYLE)), LIMITS);
        Pose anchor = new Pose(new Vec3(1, 2, 3), new Quat(1, 0, 0.5, 0), 2);
        EffectFrame f = s.evaluate(anchor, paused(47.5), TimePoint.gameTick(0));
        assertEquals(3, f.batches().size());
        assertEquals(f.batches().get(0).pose(), f.batches().get(1).pose());
        assertEquals(f.batches().get(0).pose(), f.batches().get(2).pose());
        assertEquals(f.batches().get(0).point(0), f.batches().get(2).point(0));
    }

    @Test
    @DisplayName("Inverted transforms: fixed(p).then(fixed(p.inverse())) is the identity within the tolerance; an inverse anchor maps world points back to local ones")
    void inverses() {
        SplittableRandom r = new SplittableRandom(53);
        for (int i = 0; i < 500; i++) {
            Pose p = new Pose(new Vec3(r.nextDouble(-100, 100), r.nextDouble(-100, 100), r.nextDouble(-100, 100)),
                new Quat(r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1) + 0.01), r.nextDouble(0.01, 100));
            Vec3 local = new Vec3(r.nextDouble(-10, 10), r.nextDouble(-10, 10), r.nextDouble(-10, 10));
            FrameBatch round = frame(Pose.IDENTITY, Motion.fixed(p).then(Motion.fixed(p.inverse())), Shape.point(local), 0).batches().get(0);
            double tol = 1e-9 * (1 + p.translation().length() / p.scale() + local.length());
            assertEquals(local.x(), round.x(0), tol);
            assertEquals(local.y(), round.y(0), tol);
            assertEquals(local.z(), round.z(0), tol);
            Vec3 world = p.toWorld(local);
            FrameBatch back = frame(p.inverse(), Motion.IDENTITY, Shape.point(world), 0).batches().get(0);
            assertEquals(local.x(), back.x(0), tol * (1 + world.length()));
            assertEquals(local.y(), back.y(0), tol * (1 + world.length()));
            assertEquals(local.z(), back.z(0), tol * (1 + world.length()));
        }
    }

    @Test
    @DisplayName("Finite results: an unrepresentable composed scale or translation (M1 Pose.then) or a non-finite world point gives ArithmeticException, and no frame is returned")
    void finiteResults() {
        Shape dot = Shape.point(new Vec3(1, 0, 0));
        Pose huge = new Pose(Vec3.ZERO, Quat.IDENTITY, 1e300);
        Motion grow = Motion.scale(1e9, 1e9, Timing.once(1), Easing.LINEAR);
        assertThrows(ArithmeticException.class, () -> frame(huge, grow.then(grow), dot, 0));
        Pose far = new Pose(new Vec3(1e308, 0, 0), Quat.IDENTITY, 1);
        assertThrows(ArithmeticException.class, () -> frame(far, Motion.fixed(Pose.ofTranslation(new Vec3(1e308, 0, 0))), dot, 0));
        Shape big = Shape.point(new Vec3(1e300, 0, 0));
        assertThrows(ArithmeticException.class, () -> frame(new Pose(new Vec3(1e308, 0, 0), Quat.IDENTITY, 1e8), Motion.IDENTITY, big, 0));
        assertTrue(Double.isFinite(frame(new Pose(new Vec3(-1e308, 0, 0), Quat.IDENTITY, 1), Motion.IDENTITY, big, 0).batches().get(0).x(0)));
    }

    @Test
    @DisplayName("Random compositions of up to 6 steps (fixed, spin, scale, translate) on random anchors match the independent matrix oracle within the tolerance")
    void randomCompositions() {
        SplittableRandom r = new SplittableRandom(54);
        Easing[] easings = Easing.values();
        for (int i = 0; i < 1_500; i++) {
            Timing drive = switch (r.nextInt(3)) {
                case 0 -> Timing.once(r.nextDouble(0.5, 50)).withDelay(r.nextDouble(-10, 10));
                case 1 -> Timing.loop(r.nextDouble(0.5, 50));
                default -> Timing.pingPong(r.nextDouble(0.5, 50)).withCycles(r.nextLong(1, 6));
            };
            List<Spec> specs = new java.util.ArrayList<>();
            double maxTurns = 0;
            for (int k = r.nextInt(1, 7); k > 0; k--) {
                Easing e = easings[r.nextInt(easings.length)];
                switch (r.nextInt(4)) {
                    case 0 -> specs.add(new FixedSpec(new Pose(new Vec3(r.nextDouble(-3, 3), r.nextDouble(-3, 3), r.nextDouble(-3, 3)),
                        new Quat(r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1), 0.2), r.nextDouble(0.5, 2))));
                    case 1 -> {
                        double turns = r.nextDouble(-20, 20);
                        maxTurns += Math.abs(turns);
                        specs.add(new SpinSpec(new Vec3(r.nextDouble(-1, 1), r.nextDouble(-1, 1), 0.3), turns, r.nextDouble(-2, 2), drive, e));
                    }
                    case 2 -> specs.add(new ScaleSpec(r.nextDouble(0.2, 3), r.nextDouble(0.2, 3), drive, e));
                    default -> specs.add(new TranslateSpec(new Vec3(r.nextDouble(-5, 5), 0, 1), new Vec3(r.nextDouble(-5, 5), 2, -1), drive, e));
                }
            }
            Pose anchor = new Pose(new Vec3(r.nextDouble(-3e4, 3e4), r.nextDouble(-64, 320), r.nextDouble(-3e4, 3e4)),
                new Quat(r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1) + 0.01), r.nextDouble(0.1, 10));
            Vec3 local = new Vec3(r.nextDouble(-4, 4), r.nextDouble(-4, 4), r.nextDouble(-4, 4));
            double t = r.nextDouble(0, 400); // the scene window starts at 0; drives with delays still see BEFORE
            FrameBatch b = frame(anchor, M5TestSupport.motionOf(specs), Shape.point(local), t).batches().get(0);
            near(M5TestSupport.oracle(anchor, specs, t, local), b, 0, maxTurns, "case " + i);
        }
    }
}
