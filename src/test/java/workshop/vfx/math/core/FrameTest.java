package workshop.vfx.math.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static workshop.vfx.math.core.TestSupport.angleBetween;
import static workshop.vfx.math.core.TestSupport.assertFrameValid;
import static workshop.vfx.math.core.TestSupport.assertVec;
import static workshop.vfx.math.core.TestSupport.randomUnit;
import static workshop.vfx.math.core.TestSupport.randomVec;
import static workshop.vfx.math.core.TestSupport.triple;
import static workshop.vfx.math.core.TestSupport.twist;

import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FrameTest {
    private static final long SEED = 20261014L;
    private static final double TOL = 1e-12;

    private static void assertFrame(Vec3 x, Vec3 y, Vec3 z, Frame actual, String label) {
        assertVec(x, actual.x(), TOL, label + ".x");
        assertVec(y, actual.y(), TOL, label + ".y");
        assertVec(z, actual.z(), TOL, label + ".z");
    }

    // ------------------------------------------------------------ validation

    @Test
    @DisplayName("Constructor accepts right-handed orthonormal triples and rejects everything else")
    void constructorValidation() {
        assertFrameValid(Frame.WORLD, 0.0, "WORLD");
        double s = Math.sqrt(0.5);
        Frame rotated = new Frame(new Vec3(s, s, 0), new Vec3(-s, s, 0), new Vec3(0, 0, 1));
        assertFrameValid(rotated, 1e-12, "45 degrees about Z");
        // left-handed (z flipped)
        assertThrows(IllegalArgumentException.class, () -> new Frame(Vec3.X, Vec3.Y, new Vec3(0, 0, -1)));
        // not unit length
        assertThrows(IllegalArgumentException.class, () -> new Frame(new Vec3(1.001, 0, 0), Vec3.Y, Vec3.Z));
        // not orthogonal
        assertThrows(IllegalArgumentException.class, () -> new Frame(Vec3.X, new Vec3(s, s, 0), Vec3.Z));
        // non-finite
        assertThrows(IllegalArgumentException.class, () -> new Frame(new Vec3(Double.NaN, 0, 0), Vec3.Y, Vec3.Z));
        assertThrows(IllegalArgumentException.class, () -> new Frame(Vec3.X, Vec3.Y, new Vec3(0, 0, Double.POSITIVE_INFINITY)));
        // zero axis
        assertThrows(IllegalArgumentException.class, () -> new Frame(Vec3.ZERO, Vec3.Y, Vec3.Z));
    }

    @Test
    @DisplayName("toWorld / toLocal are inverse and use the frame axes as columns")
    void toWorldToLocal() {
        Frame f = Frame.alignY(new Vec3(0, 0, 1), new Vec3(1, 0, 0));
        // x = (1,0,0), y = (0,0,1), z = (0,-1,0): local (1,2,3) = 1*x + 2*y + 3*z = (1, -3, 2)
        assertVec(new Vec3(1, -3, 2), f.toWorld(new Vec3(1, 2, 3)), TOL, "toWorld");
        assertVec(new Vec3(1, 2, 3), f.toLocal(new Vec3(1, -3, 2)), TOL, "toLocal");
        SplittableRandom r = new SplittableRandom(SEED);
        for (int i = 0; i < 5_000; i++) {
            Frame g = Frame.alignY(randomUnit(r), randomVec(r, 1));
            Vec3 v = randomVec(r, 10);
            assertVec(v, g.toLocal(g.toWorld(v)), 1e-11, "round trip");
            assertEquals(v.length(), g.toWorld(v).length(), 1e-11, "isometry");
        }
    }

    // ------------------------------------------------------------ alignY / alignZ

    @Test
    @DisplayName("alignY hand-derived reference frames")
    void alignYReference() {
        assertFrame(new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1),
                Frame.alignY(new Vec3(0, 1, 0), new Vec3(1, 0, 0)), "up, hint +X");
        assertFrame(new Vec3(1, 0, 0), new Vec3(0, 0, 1), new Vec3(0, -1, 0),
                Frame.alignY(new Vec3(0, 0, 1), new Vec3(1, 0, 0)), "south, hint +X");
        double s = Math.sqrt(0.5);
        assertFrame(new Vec3(-s, s, 0), new Vec3(s, s, 0), new Vec3(0, 0, -1),
                Frame.alignY(new Vec3(2, 2, 0), new Vec3(0, 1, 0)), "diagonal, hint +Y (non-unit input)");
    }

    @Test
    @DisplayName("alignZ hand-derived reference frames")
    void alignZReference() {
        assertFrame(new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1),
                Frame.alignZ(new Vec3(0, 0, 1), new Vec3(0, 1, 0)), "south, hint +Y");
        assertFrame(new Vec3(0, 0, -1), new Vec3(0, 1, 0), new Vec3(1, 0, 0),
                Frame.alignZ(new Vec3(1, 0, 0), new Vec3(0, 1, 0)), "east, hint +Y");
    }

    @Test
    @DisplayName("alignY/alignZ: right-handed orthonormal, axis honoured, hint lies in the x-y plane (10,000 random)")
    void alignProperties() {
        SplittableRandom r = new SplittableRandom(SEED + 1);
        for (int i = 0; i < 10_000; i++) {
            Vec3 axis = randomVec(r, 50);
            Vec3 hint = randomVec(r, 50);
            if (axis.length() < 1e-3 || hint.length() < 1e-3) {
                continue;
            }
            Frame fy = Frame.alignY(axis, hint);
            assertFrameValid(fy, 1e-12, "alignY " + i);
            assertVec(axis.mul(1.0 / axis.length()), fy.y(), 1e-12, "alignY keeps the axis");
            Frame fz = Frame.alignZ(axis, hint);
            assertFrameValid(fz, 1e-12, "alignZ " + i);
            assertVec(axis.mul(1.0 / axis.length()), fz.z(), 1e-12, "alignZ keeps the axis");
            // The hint, projected out of the axis, points along +x (alignY) / +y (alignZ).
            Vec3 yu = fy.y();
            Vec3 projected = hint.sub(yu.mul(hint.dot(yu)));
            if (projected.length() > 1e-6) {
                assertEquals(0.0, angleBetween(projected, fy.x()), 1e-9, "hint direction is +x");
            }
        }
    }

    @Test
    @DisplayName("alignY/alignZ degeneracy: parallel, antiparallel, zero and non-finite hints or axes (M0: robustness)")
    void alignDegenerate() {
        // hint parallel or antiparallel to the axis: fallback is the world axis least aligned with the axis,
        // ties broken in the order X, Y, Z.
        assertFrame(new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1),
                Frame.alignY(new Vec3(0, 1, 0), new Vec3(0, 1, 0)), "hint parallel to +Y");
        assertFrame(new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1),
                Frame.alignY(new Vec3(0, 1, 0), new Vec3(0, -3, 0)), "hint antiparallel to +Y");
        assertFrame(new Vec3(0, 1, 0), new Vec3(1, 0, 0), new Vec3(0, 0, -1),
                Frame.alignY(new Vec3(1, 0, 0), new Vec3(1, 0, 0)), "axis +X, hint parallel (fallback +Y)");
        assertFrame(new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1),
                Frame.alignY(new Vec3(0, 1, 0), Vec3.ZERO), "zero hint");
        assertFrame(new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1),
                Frame.alignY(new Vec3(0, 1, 0), new Vec3(Double.NaN, 0, 0)), "NaN hint");
        // zero or non-finite axis: the documented fallback is the WORLD frame
        assertFrame(Vec3.X, Vec3.Y, Vec3.Z, Frame.alignY(Vec3.ZERO, Vec3.X), "zero axis (alignY)");
        assertFrame(Vec3.X, Vec3.Y, Vec3.Z, Frame.alignZ(new Vec3(1e-300, 0, 0), Vec3.Y), "tiny axis (alignZ)");
        assertFrame(Vec3.X, Vec3.Y, Vec3.Z, Frame.alignY(new Vec3(Double.NaN, 1, 1), Vec3.X), "NaN axis");
        // nearly parallel hints stay valid and finite (twice-orthogonalized)
        for (double e : new double[] {1e-3, 1e-5, 1e-7, 1e-9, 1e-12}) {
            Vec3 hint = new Vec3(Math.sin(e), Math.cos(e), 0);
            assertFrameValid(Frame.alignY(new Vec3(0, 1, 0), hint), 1e-12, "near-parallel hint " + e);
            assertFrameValid(Frame.alignZ(new Vec3(0, 1, 0), hint), 1e-12, "near-parallel hint (alignZ) " + e);
        }
        // The hint threshold is part of the contract: a hint whose component perpendicular to the axis is below 1e-8
        // is treated as parallel (fallback +X for axis +Y); at 1e-5 the hint is honoured.
        Frame belowThreshold = Frame.alignY(new Vec3(0, 1, 0), new Vec3(0, Math.cos(1e-9), Math.sin(1e-9)));
        assertFrame(new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1), belowThreshold, "hint 1e-9 off the axis uses the fallback");
        Frame aboveThreshold = Frame.alignY(new Vec3(0, 1, 0), new Vec3(0, Math.cos(1e-5), Math.sin(1e-5)));
        assertFrame(new Vec3(0, 0, 1), new Vec3(0, 1, 0), new Vec3(-1, 0, 0), aboveThreshold, "hint 1e-5 off the axis is honoured");
        // extreme magnitudes
        assertFrameValid(Frame.alignY(new Vec3(0, 1e200, 0), new Vec3(1e150, 1, 0)), 1e-12, "huge inputs");
        assertFrameValid(Frame.alignY(new Vec3(3e-11, 0, 0), new Vec3(0, 5e-11, 0)), 1e-12, "tiny but above threshold");
    }

    // ------------------------------------------------------------ fromHeading (audit item 2)

    @Test
    @DisplayName("fromHeading: facing each compass direction gives the correct right, up and back axes")
    void fromHeadingCompass() {
        Vec3 up = new Vec3(0, 1, 0);
        // facing south (+Z): right is west (-X)
        assertFrame(new Vec3(-1, 0, 0), up, new Vec3(0, 0, -1), Frame.fromHeading(new Vec3(0, 0, 1), up), "south");
        // facing east (+X): right is south (+Z)
        assertFrame(new Vec3(0, 0, 1), up, new Vec3(-1, 0, 0), Frame.fromHeading(new Vec3(1, 0, 0), up), "east");
        // facing north (-Z): right is east (+X)
        assertFrame(new Vec3(1, 0, 0), up, new Vec3(0, 0, 1), Frame.fromHeading(new Vec3(0, 0, -1), up), "north");
        // facing west (-X): right is north (-Z)
        assertFrame(new Vec3(0, 0, -1), up, new Vec3(1, 0, 0), Frame.fromHeading(new Vec3(-1, 0, 0), up), "west");
        // WORLD corresponds to facing -Z (north) with +Y up
        assertFrame(Vec3.X, Vec3.Y, Vec3.Z, Frame.fromHeading(new Vec3(0, 0, -1), up), "WORLD is facing north");
    }

    @Test
    @DisplayName("M0 audit item 2: Astral's (right, up, forward) basis is left-handed; fromHeading's (right, up, back) is right-handed")
    void leftHandedNegativeControl() {
        Vec3 up = new Vec3(0, 1, 0);
        Vec3[] headings = {new Vec3(0, 0, 1), new Vec3(1, 0, 0), new Vec3(0, 0, -1), new Vec3(-1, 0, 0)};
        for (Vec3 forward : headings) {
            // Negative control: the basis Astral Prism 0.2.0 uses (x -> right = forward x up, y -> up, z -> forward)
            Vec3 right = forward.cross(up);
            assertEquals(-1.0, triple(right, up, forward), 1e-12, "(right, up, forward) is a reflection for " + forward);
            // The engine's convention is the proper rotation (right, up, back)
            Frame f = Frame.fromHeading(forward, up);
            assertEquals(1.0, triple(f.x(), f.y(), f.z()), 1e-12, "(right, up, back) is right-handed for " + forward);
            assertVec(forward.negate(), f.z(), 1e-12, "z is back");
            assertVec(right, f.x(), 1e-12, "x is right = forward x up");
        }
        // Astral's local-to-world map for forward = +Z: right = (-1, 0, 0)
        assertVec(new Vec3(-1, 0, 0), new Vec3(0, 0, 1).cross(up), 0.0, "right for a south heading");
    }

    @Test
    @DisplayName("fromHeading degeneracy: vertical heading, zero heading, zero or parallel up hint")
    void fromHeadingDegenerate() {
        // straight up with an up hint of +Y: fallback up hint is +X (least aligned, ties X, Y, Z)
        assertFrame(new Vec3(0, 0, -1), new Vec3(1, 0, 0), new Vec3(0, -1, 0),
                Frame.fromHeading(new Vec3(0, 1, 0), new Vec3(0, 1, 0)), "looking straight up");
        assertFrameValid(Frame.fromHeading(new Vec3(0, -1, 0), new Vec3(0, 1, 0)), 1e-12, "looking straight down");
        assertFrame(Vec3.X, Vec3.Y, Vec3.Z, Frame.fromHeading(Vec3.ZERO, new Vec3(0, 1, 0)), "zero heading");
        assertFrame(Vec3.X, Vec3.Y, Vec3.Z, Frame.fromHeading(new Vec3(Double.NaN, 0, 1), new Vec3(0, 1, 0)), "NaN heading");
        // zero up hint defaults to +Y
        assertFrame(new Vec3(-1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, -1),
                Frame.fromHeading(new Vec3(0, 0, 1), Vec3.ZERO), "zero up hint");
        // nearly vertical headings
        SplittableRandom r = new SplittableRandom(SEED + 2);
        for (int i = 0; i < 5_000; i++) {
            double tilt = Math.pow(10, -r.nextDouble(1, 12));
            Vec3 forward = new Vec3(Math.sin(tilt) * Math.cos(r.nextDouble(0, 6.28)), Math.cos(tilt), Math.sin(tilt) * Math.sin(r.nextDouble(0, 6.28)));
            assertFrameValid(Frame.fromHeading(forward, new Vec3(0, 1, 0)), 1e-12, "tilt " + tilt);
        }
        // random headings
        for (int i = 0; i < 5_000; i++) {
            Vec3 forward = randomUnit(r);
            Frame f = Frame.fromHeading(forward, randomVec(r, 1));
            assertFrameValid(f, 1e-12, "random heading");
            assertVec(forward.negate(), f.z(), 1e-12, "z is back");
        }
    }

    // ------------------------------------------------------------ transport (audit: no flip through the pole)

    @Test
    @DisplayName("transportY follows a direction sweep without twist; naive alignY flips by about pi through the hint (negative control)")
    void transportAvoidsTwist() {
        Vec3 hint = new Vec3(1, 0, 0);
        // Sweep y = (cos a, sin a, 0) across the hint direction (a = 0). Sample angles never hit exactly 0.
        double start = -0.495;
        double step = 0.01;
        int steps = 100;
        Frame transported = Frame.alignY(new Vec3(Math.cos(start), Math.sin(start), 0), hint);
        Frame naivePrev = transported;
        double worstTransported = 0.0;
        double worstNaive = 0.0;
        for (int i = 1; i <= steps; i++) {
            double a = start + i * step;
            Vec3 y = new Vec3(Math.cos(a), Math.sin(a), 0);
            Frame nextTransported = Frame.transportY(transported, y);
            assertFrameValid(nextTransported, 1e-12, "transport step " + i);
            assertVec(y, nextTransported.y(), 1e-12, "transport honours the new axis");
            worstTransported = Math.max(worstTransported, twist(transported, nextTransported));
            transported = nextTransported;

            Frame naive = Frame.alignY(y, hint);
            worstNaive = Math.max(worstNaive, twist(naivePrev, naive));
            naivePrev = naive;
        }
        System.out.println("[M1 transport report] worst per-step twist: transport = " + worstTransported + " rad, naive alignY = " + worstNaive + " rad");
        assertTrue(worstTransported < 1e-9, "transport twisted by " + worstTransported);
        assertTrue(worstNaive > 3.0, "negative control expected a flip near pi but saw " + worstNaive);
    }

    @Test
    @DisplayName("transportY: random walks stay valid; zero axis keeps the previous frame; antiparallel stays finite")
    void transportRobustness() {
        SplittableRandom r = new SplittableRandom(SEED + 3);
        Frame f = Frame.WORLD;
        for (int i = 0; i < 20_000; i++) {
            Vec3 next = randomUnit(r);
            f = Frame.transportY(f, next);
            assertFrameValid(f, 1e-11, "walk step " + i);
            assertVec(next, f.y(), 1e-11, "walk axis");
        }
        Frame base = Frame.alignY(new Vec3(1, 2, 3), new Vec3(0, 1, 0));
        Frame same = Frame.transportY(base, Vec3.ZERO);
        assertVec(base.x(), same.x(), 0.0, "zero axis keeps x");
        assertVec(base.y(), same.y(), 0.0, "zero axis keeps y");
        Frame flipped = Frame.transportY(base, base.y().negate());
        assertFrameValid(flipped, 1e-11, "antiparallel transport");
        assertVec(base.y().negate(), flipped.y(), 1e-11, "antiparallel axis");
        Frame unchanged = Frame.transportY(base, base.y());
        assertVec(base.x(), unchanged.x(), 1e-12, "same axis keeps x");
    }
}
