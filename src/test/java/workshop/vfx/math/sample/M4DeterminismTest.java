package workshop.vfx.math.sample;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.curve.CubicBezier;
import workshop.vfx.math.curve.Curve;
import workshop.vfx.math.curve.Spiral;

/** Contract section 10: results depend only on the arguments; bit-identical on repetition, after JIT and across threads; a table is safely shared. */
class M4DeterminismTest {

    private static long[] fingerprint(Curve c, double t0, double t1, ArcLengthTolerance tol, SampleLimits limits) {
        ArcLengthTable t = ArcLengthTable.build(c, t0, t1, tol, limits);
        SampleSet a = Sampler.uniformDistance(t, 97, Endpoints.INCLUDE_END, limits);
        SampleSet b = Sampler.bySpacing(t, Math.max(t.length() / 50, 1e-6), Endpoints.EXCLUDE_END, limits);
        SampleSet u = Sampler.uniformParameter(c, t0, t1, 33, Endpoints.EXCLUDE_END, limits);
        List<Long> bits = new ArrayList<>();
        for (double d : new double[] {t.length(), t.errorEstimate(), t.effectiveTolerance(), t.evaluations(), t.panelCount(), t.converged() ? 1 : 0,
            t.lengthAt(t0 + (t1 - t0) * 0.3), t.parameterAt(t.length() * 0.7)}) {
            bits.add(Double.doubleToRawLongBits(d));
        }
        for (SampleSet s : new SampleSet[] {a, b, u}) {
            for (int k = 0; k < s.size(); k++) {
                Vec3 p = s.point(k);
                bits.add(Double.doubleToRawLongBits(s.parameter(k)));
                bits.add(Double.doubleToRawLongBits(p.x()));
                bits.add(Double.doubleToRawLongBits(p.y()));
                bits.add(Double.doubleToRawLongBits(p.z()));
            }
        }
        return bits.stream().mapToLong(Long::longValue).toArray();
    }

    private static Curve[] curves() {
        SplittableRandom r = new SplittableRandom(451);
        Curve[] out = new Curve[24];
        for (int i = 0; i < out.length; i++) {
            out[i] = i % 3 == 0 ? Spiral.conicalSpiral(r.nextDouble(0, 3), r.nextDouble(0, 6), r.nextDouble(-5, 5), r.nextDouble(-20, 20))
                : i % 3 == 1 ? new CubicBezier(rv(r), rv(r), rv(r), rv(r)) : SampleTestSupport.wiggle(0.02, r.nextInt(1, 60));
        }
        return out;
    }

    private static Vec3 rv(SplittableRandom r) {
        return new Vec3(r.nextDouble(-9, 9), r.nextDouble(-9, 9), r.nextDouble(-9, 9));
    }

    @Test
    @DisplayName("Repeated evaluation (before and after JIT warm-up) gives bit-identical tables and samples")
    void repeatable() {
        Curve[] cs = curves();
        ArcLengthTolerance tol = new ArcLengthTolerance(0, 1e-8);
        SampleLimits limits = new SampleLimits(4096, 1 << 14);
        long[][] first = new long[cs.length][];
        for (int i = 0; i < cs.length; i++) {
            first[i] = fingerprint(cs[i], -0.25, 1.25, tol, limits);
        }
        for (int rep = 0; rep < 20; rep++) {
            for (int i = 0; i < cs.length; i++) {
                assertArrayEquals(first[i], fingerprint(cs[i], -0.25, 1.25, tol, limits), "curve " + i + " repetition " + rep);
            }
        }
    }

    @Test
    @DisplayName("Concurrent use: 8 threads building and sampling, and 8 threads sharing one table, give the sequential bits")
    void concurrent() throws Exception {
        Curve[] cs = curves();
        ArcLengthTolerance tol = new ArcLengthTolerance(0, 1e-7);
        SampleLimits limits = new SampleLimits(4096, 1 << 13);
        long[][] seq = new long[cs.length][];
        for (int i = 0; i < cs.length; i++) {
            seq[i] = fingerprint(cs[i], 0, 1, tol, limits);
        }
        ArcLengthTable shared = ArcLengthTable.build(cs[1], tol, limits);
        double[] sharedSeq = Sampler.uniformDistance(shared, 400, Endpoints.EXCLUDE_END, limits).parameters();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> fs = new ArrayList<>();
            for (int job = 0; job < 32; job++) {
                int j = job;
                fs.add(pool.submit(() -> {
                    for (int i = 0; i < cs.length; i++) {
                        if (!Arrays.equals(seq[(i + j) % cs.length], fingerprint(cs[(i + j) % cs.length], 0, 1, tol, limits))) {
                            return false;
                        }
                    }
                    return Arrays.equals(sharedSeq, Sampler.uniformDistance(shared, 400, Endpoints.EXCLUDE_END, limits).parameters());
                }));
            }
            for (Future<Boolean> f : fs) {
                assertTrue(f.get(), "concurrent result differs");
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
