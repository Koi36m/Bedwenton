package workshop.vfx.math.curve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static workshop.vfx.math.curve.CurveTestSupport.assertNear;
import static workshop.vfx.math.curve.CurveTestSupport.randomVec;

import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.math.core.Frame;
import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Quat;
import workshop.vfx.math.core.Vec3;

/** Contract section 3: placing a locally defined curve with an M1 Pose. */
class PosedCurveTest {

    private static Quat randomRotation(SplittableRandom r) {
        return new Quat(r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1) + 1e-3).normalized();
    }

    @Test
    @DisplayName("Validation: curve and pose are required; transformed(pose) is a PosedCurve (section 3)")
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> new PosedCurve(null, Pose.IDENTITY));
        assertThrows(IllegalArgumentException.class, () -> new PosedCurve(Spiral.circle(1), null));
        assertThrows(IllegalArgumentException.class, () -> Spiral.circle(1).transformed(null));
        Curve c = Spiral.circle(1).transformed(Pose.IDENTITY);
        assertEquals(new PosedCurve(Spiral.circle(1), Pose.IDENTITY), c);
        assertThrows(IllegalArgumentException.class, () -> c.point(Double.NaN), "parameter checks still apply");
    }

    @Test
    @DisplayName("A placed circle lies in the plane normal to the rotated +Y, at distance scale * r from the translation, and runs counter-clockwise about that normal (sections 3, 5)")
    void placedCircleGeometry() {
        SplittableRandom r = new SplittableRandom(61);
        for (int i = 0; i < 10_000; i++) {
            Vec3 center = randomVec(r, 3e7);
            Quat q = randomRotation(r);
            double s = r.nextDouble(0.01, 100);
            double radius = r.nextDouble(0.1, 30);
            Curve c = Spiral.circle(radius).transformed(new Pose(center, q, s));
            Vec3 normal = q.rotate(Vec3.Y);
            double t = r.nextDouble();
            Vec3 p = c.point(t).sub(center);
            double tol = 1e-9 * (1 + 3e7 * 1e-7) * s * radius + 1e-8;
            assertEquals(0.0, p.dot(normal), tol, "in plane");
            assertEquals(s * radius, p.length(), tol, "radius");
            assertEquals(1.0, p.cross(c.derivative(t)).normalizedOr(Vec3.ZERO).dot(normal), 1e-9, "counter-clockwise about the normal");
        }
    }

    @Test
    @DisplayName("Composition: c.transformed(a).transformed(b) equals c.transformed(b.then(a)) (M1 Pose.then) (section 3)")
    void composition() {
        SplittableRandom r = new SplittableRandom(62);
        for (int i = 0; i < 10_000; i++) {
            Pose a = new Pose(randomVec(r, 100), randomRotation(r), r.nextDouble(0.1, 10));
            Pose b = new Pose(randomVec(r, 100), randomRotation(r), r.nextDouble(0.1, 10));
            Curve base = new CubicBezier(randomVec(r, 5), randomVec(r, 5), randomVec(r, 5), randomVec(r, 5));
            double t = r.nextDouble(-0.5, 1.5);
            double scale = (100 + 10 * (100 + 10 * 5 * 8)) * 1e-12;
            assertNear(base.transformed(b.then(a)).point(t), base.transformed(a).transformed(b).point(t), scale, "point");
            assertNear(base.transformed(b.then(a)).derivative(t), base.transformed(a).transformed(b).derivative(t), 1e-10 * 100 * 8, "derivative");
        }
    }

    @Test
    @DisplayName("Derivatives transform as directions scaled by the pose scale: they match finite differences of the placed points (section 3)")
    void derivativesAreScaledDirections() {
        SplittableRandom r = new SplittableRandom(63);
        for (int i = 0; i < 5_000; i++) {
            Pose pose = new Pose(randomVec(r, 50), randomRotation(r), r.nextDouble(0.1, 10));
            Curve c = new Spiral(r.nextDouble(0, 5), r.nextDouble(0, 5), r.nextDouble(-5, 5), r.nextDouble(-3, 3), 0).transformed(pose);
            double t = r.nextDouble();
            double h = 1e-6;
            assertNear(c.point(t + h).sub(c.point(t - h)).mul(1 / (2 * h)), c.derivative(t), 1e-5 * (1 + c.derivative(t).length()), "derivative");
            assertNear(c.derivative(t + h).sub(c.derivative(t - h)).mul(1 / (2 * h)), c.secondDerivative(t), 1e-4 * (1 + c.secondDerivative(t).length()), "second derivative");
        }
    }

    @Test
    @DisplayName("Orientation by Frame: Quat.fromFrame(Frame.fromHeading(...)) places a circle facing a heading, without new orientation types (sections 3, 9)")
    void orientationViaM1Frames() {
        Frame facingEast = Frame.fromHeading(Vec3.X, Vec3.Y); // right = south, up = +Y, back = west
        Curve ring = Spiral.circle(2).transformed(new Pose(new Vec3(10, 64, 10), Quat.fromFrame(facingEast), 1));
        assertNear(new Vec3(10, 64, 10).add(facingEast.x().mul(2)), ring.point(0), 1e-12, "starts on the frame's local +X (right)");
        assertNear(new Vec3(10, 64, 10).add(facingEast.z().mul(-2)), ring.point(0.25), 1e-12, "quarter turn reaches local -Z (forward)");
    }
}
