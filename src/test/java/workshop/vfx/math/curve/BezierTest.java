package workshop.vfx.math.curve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static workshop.vfx.math.curve.CurveTestSupport.assertExact;
import static workshop.vfx.math.curve.CurveTestSupport.assertNear;
import static workshop.vfx.math.curve.CurveTestSupport.maxAbs;
import static workshop.vfx.math.curve.CurveTestSupport.randomVec;

import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Quat;
import workshop.vfx.math.core.Vec3;

/** Contract section 4: line segments and Bezier curves. */
class BezierTest {

    private static double maxAbsOf(Vec3... ps) {
        double m = 0;
        for (Vec3 p : ps) {
            m = Math.max(m, maxAbs(p));
        }
        return m;
    }

    @Test
    @DisplayName("Validation: null or non-finite control points and non-finite parameters are IllegalArgumentExceptions (sections 4, 8)")
    void validation() {
        Vec3 nan = new Vec3(Double.NaN, 0, 0);
        Vec3 inf = new Vec3(0, Double.POSITIVE_INFINITY, 0);
        assertThrows(IllegalArgumentException.class, () -> new LineSegment(null, Vec3.X));
        assertThrows(IllegalArgumentException.class, () -> new LineSegment(Vec3.X, nan));
        assertThrows(IllegalArgumentException.class, () -> new QuadraticBezier(Vec3.X, inf, Vec3.Y));
        assertThrows(IllegalArgumentException.class, () -> new CubicBezier(Vec3.X, Vec3.Y, Vec3.Z, null));
        assertThrows(IllegalArgumentException.class, () -> new CubicBezier(nan, Vec3.Y, Vec3.Z, Vec3.X));
        Curve[] curves = {new LineSegment(Vec3.X, Vec3.Y), new QuadraticBezier(Vec3.X, Vec3.Y, Vec3.Z), new CubicBezier(Vec3.X, Vec3.Y, Vec3.Z, Vec3.ZERO)};
        for (Curve c : curves) {
            for (double t : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
                assertThrows(IllegalArgumentException.class, () -> c.point(t), c + " point " + t);
                assertThrows(IllegalArgumentException.class, () -> c.derivative(t), c + " derivative " + t);
                assertThrows(IllegalArgumentException.class, () -> c.secondDerivative(t), c + " second " + t);
            }
        }
    }

    @Test
    @DisplayName("Endpoints are exact for arbitrary control points: point(0) = first, point(1) = last (section 4)")
    void exactEndpoints() {
        SplittableRandom r = new SplittableRandom(41);
        for (int i = 0; i < 20_000; i++) {
            double mag = Math.pow(10, r.nextInt(-8, 100));
            Vec3 a = randomVec(r, mag), b = randomVec(r, mag), c = randomVec(r, mag), d = randomVec(r, mag);
            assertExact(a, new LineSegment(a, b).point(0), "line start");
            assertExact(b, new LineSegment(a, b).point(1), "line end");
            assertExact(a, new QuadraticBezier(a, b, c).point(0), "quadratic start");
            assertExact(c, new QuadraticBezier(a, b, c).point(1), "quadratic end");
            assertExact(a, new CubicBezier(a, b, c, d).point(0), "cubic start");
            assertExact(d, new CubicBezier(a, b, c, d).point(1), "cubic end");
        }
    }

    @Test
    @DisplayName("End derivatives: B'(0) = n (P1 - P0), B'(1) = n (Pn - Pn-1); joining with mirrored handles is C1 (section 4)")
    void endDerivativesAndC1Join() {
        SplittableRandom r = new SplittableRandom(42);
        for (int i = 0; i < 10_000; i++) {
            Vec3 a = randomVec(r, 100), b = randomVec(r, 100), c = randomVec(r, 100), d = randomVec(r, 100), e = randomVec(r, 100);
            CubicBezier first = new CubicBezier(a, b, c, d);
            Vec3 mirrored = d.add(d.sub(c)); // the next segment's first handle, mirrored through the joint
            CubicBezier second = new CubicBezier(d, mirrored, e, a);
            double tol = 0x1p-46 * maxAbsOf(a, b, c, d, e, mirrored);
            assertNear(b.sub(a).mul(3), first.derivative(0), tol, "B'(0)");
            assertNear(d.sub(c).mul(3), first.derivative(1), tol, "B'(1)");
            assertExact(first.point(1), second.point(0), "C0 join");
            assertNear(first.derivative(1), second.derivative(0), tol, "C1 join");
            QuadraticBezier q = new QuadraticBezier(a, b, c);
            assertNear(b.sub(a).mul(2), q.derivative(0), tol, "quadratic B'(0)");
            assertNear(c.sub(b).mul(2), q.derivative(1), tol, "quadratic B'(1)");
        }
    }

    @Test
    @DisplayName("Degree elevation: a line and a quadratic written as higher-degree Beziers give the same points (section 4, metamorphic)")
    void degreeElevation() {
        SplittableRandom r = new SplittableRandom(43);
        for (int i = 0; i < 10_000; i++) {
            Vec3 a = randomVec(r, 50), b = randomVec(r, 50), c = randomVec(r, 50);
            double t = r.nextDouble(-0.5, 1.5);
            LineSegment line = new LineSegment(a, c);
            QuadraticBezier lineAsQuad = new QuadraticBezier(a, a.add(c).mul(0.5), c);
            double tol = 0x1p-46 * maxAbsOf(a, b, c) * 4;
            assertNear(line.point(t), lineAsQuad.point(t), tol, "line as quadratic");
            QuadraticBezier q = new QuadraticBezier(a, b, c);
            CubicBezier qAsCubic = new CubicBezier(a, a.add(b.mul(2)).mul(1.0 / 3), b.mul(2).add(c).mul(1.0 / 3), c);
            assertNear(q.point(t), qAsCubic.point(t), tol, "quadratic as cubic");
            assertNear(q.derivative(t), qAsCubic.derivative(t), 8 * tol, "derivative as cubic");
        }
    }

    @Test
    @DisplayName("Affine invariance and reversal: Bezier of posed control points = posed Bezier; reversed control points at 1 - t give the same point (section 4)")
    void affineInvarianceAndReversal() {
        SplittableRandom r = new SplittableRandom(44);
        for (int i = 0; i < 10_000; i++) {
            Vec3 a = randomVec(r, 20), b = randomVec(r, 20), c = randomVec(r, 20), d = randomVec(r, 20);
            Pose pose = new Pose(randomVec(r, 1000), new Quat(r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1) + 1e-3), r.nextDouble(0.1, 10));
            double t = r.nextDouble(-1, 2);
            CubicBezier cb = new CubicBezier(a, b, c, d);
            CubicBezier posed = new CubicBezier(pose.toWorld(a), pose.toWorld(b), pose.toWorld(c), pose.toWorld(d));
            double scale = 1000 + 20 * pose.scale() * 2;
            assertNear(pose.toWorld(cb.point(t)), posed.point(t), 1e-12 * scale * Math.pow(Math.abs(1 - t) + Math.abs(t), 3), "affine invariance");
            assertNear(cb.point(t), new CubicBezier(d, c, b, a).point(1 - t), 1e-13 * 20 * Math.pow(Math.abs(1 - t) + Math.abs(t), 3) * 8, "reversal");
        }
    }

    @Test
    @DisplayName("Derivatives agree with central finite differences of the points (independent of the derivative formulas) (section 4)")
    void derivativesMatchFiniteDifferences() {
        SplittableRandom r = new SplittableRandom(45);
        for (int i = 0; i < 5_000; i++) {
            Vec3 a = randomVec(r, 10), b = randomVec(r, 10), c = randomVec(r, 10), d = randomVec(r, 10);
            double t = r.nextDouble(-0.5, 1.5);
            double h = 1e-5;
            Curve[] curves = {new LineSegment(a, b), new QuadraticBezier(a, b, c), new CubicBezier(a, b, c, d)};
            for (Curve cv : curves) {
                Vec3 fd = cv.point(t + h).sub(cv.point(t - h)).mul(1 / (2 * h));
                assertNear(fd, cv.derivative(t), 1e-6, cv.getClass().getSimpleName() + " derivative");
                Vec3 fd2 = cv.derivative(t + h).sub(cv.derivative(t - h)).mul(1 / (2 * h));
                assertNear(fd2, cv.secondDerivative(t), 1e-5, cv.getClass().getSimpleName() + " second derivative");
            }
        }
        assertExact(Vec3.ZERO, new LineSegment(Vec3.X, Vec3.Y).secondDerivative(0.3), "a line has no curvature");
    }

    @Test
    @DisplayName("Degenerate geometry: coincident control points give a constant point and a zero derivative, without exceptions (sections 4, 8)")
    void degenerateGeometry() {
        Vec3 p = new Vec3(3, -2, 7);
        CubicBezier dot = new CubicBezier(p, p, p, p);
        for (double t : new double[] {-3, 0, 0.3, 1, 5}) {
            // exact only at the ends: (1 - t) p + t p rounds (DM3-8 trades constancy for exact endpoints)
            double tol = CurveTestSupport.bezierPointTol(3, t, 7);
            assertNear(p, dot.point(t), tol, "constant point at " + t);
            assertExact(Vec3.ZERO, dot.derivative(t), "zero derivative at " + t);
        }
        assertExact(p, dot.point(0), "exact at t = 0");
        assertExact(p, dot.point(1), "exact at t = 1");
        CubicBezier cusp = new CubicBezier(Vec3.ZERO, Vec3.ZERO, Vec3.X, Vec3.Y);
        assertExact(Vec3.ZERO, cusp.derivative(0), "zero end tangent: caller uses normalizedOr (M1) for a direction");
        assertEquals(Vec3.Y, cusp.derivative(0).normalizedOr(Vec3.Y), "M1 fallback returns the caller's fallback");
    }
}
