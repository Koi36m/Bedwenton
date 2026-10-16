package workshop.vfx.math.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static workshop.vfx.math.sample.SampleTestSupport.LIMITS;
import static workshop.vfx.math.sample.SampleTestSupport.TIGHT;
import static workshop.vfx.math.sample.SampleTestSupport.assertExact;

import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Quat;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.curve.CubicBezier;
import workshop.vfx.math.curve.Curve;
import workshop.vfx.math.curve.Spiral;

/** Contract section 7: immutable results, placement with M1 Pose, limits and tolerance records. */
class SampleSetTest {

    @Test
    @DisplayName("Immutability: parameters() returns a copy; points() is unmodifiable; later changes to returned data do not affect the set")
    void immutability() {
        SampleSet s = Sampler.uniformParameter(Spiral.circle(2), 8, Endpoints.EXCLUDE_END, LIMITS);
        double[] p = s.parameters();
        p[3] = 99;
        assertEquals(3 / 8.0, s.parameter(3));
        assertTrue(s.parameters() != s.parameters(), "a new copy each call");
        List<Vec3> pts = s.points();
        assertThrows(UnsupportedOperationException.class, () -> pts.set(0, Vec3.ZERO));
        assertThrows(UnsupportedOperationException.class, () -> pts.add(Vec3.ZERO));
        assertEquals(8, pts.size());
        assertExact(s.point(5), pts.get(5), "points() matches point(i)");
        assertThrows(IndexOutOfBoundsException.class, () -> s.point(8));
        assertThrows(IndexOutOfBoundsException.class, () -> s.parameter(-1));
    }

    @Test
    @DisplayName("transformed(pose): the same parameters, and points bit for bit equal to curve.transformed(pose).point(t) (sample once, move every tick)")
    void transformedMatchesPosedCurve() {
        SplittableRandom r = new SplittableRandom(431);
        for (int i = 0; i < 500; i++) {
            Curve c = r.nextBoolean() ? Spiral.conicalSpiral(r.nextDouble(0.1, 2), r.nextDouble(0.1, 4), r.nextDouble(-5, 5), r.nextDouble(-6, 6))
                : new CubicBezier(rv(r), rv(r), rv(r), rv(r));
            Pose pose = new Pose(new Vec3(r.nextDouble(-3e7, 3e7), r.nextDouble(-64, 320), r.nextDouble(-3e7, 3e7)),
                new Quat(r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1)), r.nextDouble(0.1, 10));
            ArcLengthTable t = ArcLengthTable.build(c, TIGHT, LIMITS);
            SampleSet local = Sampler.uniformDistance(t, r.nextInt(0, 64), Endpoints.INCLUDE_END, LIMITS);
            SampleSet world = local.transformed(pose);
            Curve posed = c.transformed(pose);
            assertEquals(local.size(), world.size());
            for (int k = 0; k < local.size(); k++) {
                assertEquals(local.parameter(k), world.parameter(k));
                assertExact(posed.point(local.parameter(k)), world.point(k), "posed point " + k);
                assertExact(local.point(k), Sampler.uniformDistance(t, local.size(), Endpoints.INCLUDE_END, LIMITS).point(k), "local set unchanged");
            }
            // relative spacing is invariant: distances scale by pose.scale()
            for (int k = 1; k < local.size(); k++) {
                double dl = local.point(k).distance(local.point(k - 1)), dw = world.point(k).distance(world.point(k - 1));
                assertEquals(pose.scale() * dl, dw, 1e-12 * (1 + dw) + 1e-14 * 3e7);
            }
        }
        assertThrows(IllegalArgumentException.class, () -> Sampler.uniformParameter(Spiral.circle(1), 3, Endpoints.INCLUDE_END, LIMITS).transformed(null));
    }

    private static Vec3 rv(SplittableRandom r) {
        return new Vec3(r.nextDouble(-5, 5), r.nextDouble(-5, 5), r.nextDouble(-5, 5));
    }

    @Test
    @DisplayName("SampleLimits: maxSamples in [1, 2^20], maxEvaluations in [256, 2^24]; ArcLengthTolerance: absolute finite >= 0, relative in [2^-40, 2^-3]")
    void limitsAndTolerances() {
        assertEquals(1 << 20, SampleLimits.MAX_SAMPLES_CEILING);
        assertEquals(256, SampleLimits.MIN_EVALUATIONS);
        assertEquals(1 << 24, SampleLimits.MAX_EVALUATIONS_CEILING);
        new SampleLimits(1, 256);
        new SampleLimits(1 << 20, 1 << 24);
        for (int[] bad : new int[][] {{0, 256}, {-1, 256}, {(1 << 20) + 1, 256}, {1, 255}, {1, (1 << 24) + 1}, {1, -5}}) {
            assertThrows(IllegalArgumentException.class, () -> new SampleLimits(bad[0], bad[1]));
        }
        assertEquals(0x1p-40, ArcLengthTolerance.MIN_RELATIVE);
        assertEquals(0x1p-3, ArcLengthTolerance.MAX_RELATIVE);
        new ArcLengthTolerance(0, 0x1p-40);
        new ArcLengthTolerance(1e300, 0x1p-3);
        for (double[] bad : new double[][] {{-1e-300, 1e-6}, {Double.NaN, 1e-6}, {Double.POSITIVE_INFINITY, 1e-6}, {0, 0}, {0, 0x1p-41}, {0, 0.2}, {0, Double.NaN}}) {
            assertThrows(IllegalArgumentException.class, () -> new ArcLengthTolerance(bad[0], bad[1]));
        }
    }
}
