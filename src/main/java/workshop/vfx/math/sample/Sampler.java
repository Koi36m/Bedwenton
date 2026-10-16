package workshop.vfx.math.sample;

import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.curve.Curve;

/** Deterministic sampling with explicit endpoint and allocation policies. */
public final class Sampler {
    private Sampler() { }

    public static SampleSet uniformParameter(Curve curve, int count, Endpoints endpoints, SampleLimits limits) {
        return uniformParameter(curve, 0, 1, count, endpoints, limits);
    }

    public static SampleSet uniformParameter(Curve curve, double t0, double t1, int count,
            Endpoints endpoints, SampleLimits limits) {
        SampleChecks.nonnull(curve);
        SampleChecks.nonnull(endpoints);
        SampleChecks.range(t0, t1);
        SampleChecks.count(count, limits);
        double[] parameters = new double[count];
        Vec3[] points = new Vec3[count];
        int den = endpoints == Endpoints.INCLUDE_END ? count - 1 : count;
        for (int k = 0; k < count; k++) {
            double t;
            if (k == 0) {
                t = t0;
            } else if (endpoints == Endpoints.INCLUDE_END && k == count - 1) {
                t = t1;
            } else {
                t = t0 + (t1 - t0) * ((double) k / den);
            }
            parameters[k] = t;
            points[k] = curve.point(t);
        }
        return new SampleSet(parameters, points);
    }

    public static SampleSet uniformDistance(ArcLengthTable table, int count, Endpoints endpoints,
            SampleLimits limits) {
        SampleChecks.nonnull(table);
        SampleChecks.nonnull(endpoints);
        SampleChecks.count(count, limits);
        if (table.length() == 0) {
            return uniformParameter(table.curve(), table.startParameter(), table.endParameter(),
                    count, endpoints, limits);
        }
        double[] parameters = new double[count];
        Vec3[] points = new Vec3[count];
        int den = endpoints == Endpoints.INCLUDE_END ? count - 1 : count;
        for (int k = 0; k < count; k++) {
            double t;
            if (k == 0) {
                t = table.startParameter();
            } else if (endpoints == Endpoints.INCLUDE_END && k == count - 1) {
                t = table.endParameter();
            } else {
                t = Math.max(parameters[k - 1], table.parameterAt(table.length() * ((double) k / den)));
            }
            parameters[k] = t;
            points[k] = table.curve().point(t);
        }
        return new SampleSet(parameters, points);
    }

    public static SampleSet bySpacing(ArcLengthTable table, double spacing, Endpoints endpoints,
            SampleLimits limits) {
        SampleChecks.nonnull(table);
        SampleChecks.nonnull(limits);
        long count = countForSpacing(table.length(), spacing, endpoints);
        if (count > limits.maxSamples()) {
            throw new IllegalArgumentException("Spacing requires more than the selected sample limit");
        }
        return uniformDistance(table, (int) count, endpoints, limits);
    }

    public static long countForSpacing(double length, double spacing, Endpoints endpoints) {
        SampleChecks.nonnull(endpoints);
        if (!Double.isFinite(length) || length < 0 || !Double.isFinite(spacing) || spacing <= 0) {
            throw new IllegalArgumentException("Length must be finite and nonnegative; spacing finite and positive");
        }
        double q = length / spacing;
        if (q >= 0x1p62) {
            return Long.MAX_VALUE;
        }
        long m = (long) Math.floor(q);
        if (q - m >= 0.5) {
            m = m + 1;
        }
        return endpoints == Endpoints.INCLUDE_END ? m + 1 : Math.max(1, m);
    }
}
