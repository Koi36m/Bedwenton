package workshop.vfx.math.curve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SplittableRandom;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Quat;
import workshop.vfx.math.core.Vec3;

/** Contract section 7.1: determinism within and across runs of an implementation. */
class M3DeterminismTest {

    private static long bits(Vec3 v) {
        return Double.doubleToRawLongBits(v.x()) * 31 + Double.doubleToRawLongBits(v.y()) * 17 + Double.doubleToRawLongBits(v.z());
    }

    @Test
    @DisplayName("Determinism: curves are values; equal curves give bit-identical results, before and after JIT compilation (section 7.1)")
    void bitIdenticalBeforeAndAfterJit() {
        SplittableRandom r = new SplittableRandom(71);
        int n = 1_000;
        Curve[] curves = new Curve[n];
        Curve[] copies = new Curve[n];
        double[] ts = new double[n];
        for (int i = 0; i < n; i++) {
            double r0 = r.nextDouble(0, 10), r1 = r.nextDouble(0, 10), rise = r.nextDouble(-5, 5), turns = r.nextDouble(-50, 50), st = r.nextDouble(-2, 2);
            Pose pose = new Pose(CurveTestSupport.randomVec(r, 3e7), new Quat(r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextDouble() + 0.1), r.nextDouble(0.5, 2));
            curves[i] = new Spiral(r0, r1, rise, turns, st).transformed(pose);
            copies[i] = new Spiral(r0, r1, rise, turns, st).transformed(pose);
            ts[i] = r.nextDouble(-1, 2);
            assertEquals(curves[i], copies[i], "value equality");
            assertEquals(curves[i].hashCode(), copies[i].hashCode());
        }
        long[] before = new long[n];
        for (int i = 0; i < n; i++) {
            before[i] = bits(curves[i].point(ts[i])) ^ bits(curves[i].derivative(ts[i])) ^ Double.doubleToRawLongBits(Easing.SMOOTHERSTEP.apply(ts[i]));
        }
        long sink = 0;
        for (int rep = 0; rep < 2_000; rep++) {
            for (int i = 0; i < n; i++) {
                sink += bits(curves[i].point(ts[i]));
            }
        }
        for (int i = 0; i < n; i++) {
            assertEquals(before[i], bits(copies[i].point(ts[i])) ^ bits(copies[i].derivative(ts[i])) ^ Double.doubleToRawLongBits(Easing.SMOOTHERSTEP.apply(ts[i])), "case " + i);
        }
        assertTrue(sink != 1);
    }

    @Test
    @DisplayName("Evaluation is path independent: the value at t does not depend on which parameters were evaluated before (section 7.1)")
    void pathIndependence() {
        Curve c = new CubicBezier(new Vec3(0, 0, 0), new Vec3(1, 2, 0), new Vec3(3, -1, 2), new Vec3(4, 0, 1));
        Curve s = Spiral.helix(2, 3, 5.5);
        double[] forward = new double[257];
        double[] spiral = new double[257];
        for (int i = 0; i <= 256; i++) {
            forward[i] = c.point(i / 256.0).y();
            spiral[i] = s.point(i / 256.0).z();
        }
        for (int i = 256; i >= 0; i -= 3) {
            assertEquals(Double.doubleToRawLongBits(forward[i]), Double.doubleToRawLongBits(c.point(i / 256.0).y()), "bezier " + i);
            assertEquals(Double.doubleToRawLongBits(spiral[i]), Double.doubleToRawLongBits(s.point(i / 256.0).z()), "spiral " + i);
        }
    }
}
