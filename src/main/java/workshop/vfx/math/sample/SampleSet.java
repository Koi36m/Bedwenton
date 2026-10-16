package workshop.vfx.math.sample;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Vec3;

/** Eager immutable parameters and the points returned by the curve. */
public final class SampleSet {
    private final double[] parameters;
    private final List<Vec3> points;

    SampleSet(double[] parameters, Vec3[] points) {
        this.parameters = parameters.clone();
        this.points = Collections.unmodifiableList(Arrays.asList(points.clone()));
    }

    public int size() { return parameters.length; }
    public double parameter(int index) { return parameters[index]; }
    public Vec3 point(int index) { return points.get(index); }
    public double[] parameters() { return parameters.clone(); }
    public List<Vec3> points() { return points; }

    public SampleSet transformed(Pose pose) {
        SampleChecks.nonnull(pose);
        Vec3[] transformed = new Vec3[size()];
        for (int i = 0; i < transformed.length; i++) {
            transformed[i] = pose.toWorld(points.get(i));
        }
        return new SampleSet(parameters, transformed);
    }
}
