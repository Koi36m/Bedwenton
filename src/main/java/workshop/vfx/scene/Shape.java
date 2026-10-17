package workshop.vfx.scene;

import java.util.List;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.sample.SampleSet;

/** Immutable local points, copied into a packed array when the shape is built (contract section 4). */
public final class Shape {
    public static final int MAX_POINTS = 1 << 20;

    private final double[] xyz;

    private Shape(double[] xyz) {
        this.xyz = xyz;
    }

    public static Shape point(Vec3 point) {
        return points(List.of(SceneChecks.nonnull(point, "Shape.point: point")));
    }

    public static Shape points(List<Vec3> points) {
        SceneChecks.nonnull(points, "Shape.points: points");
        int n = points.size();
        if (n > MAX_POINTS) {
            throw new IllegalArgumentException("Shape.points: more than MAX_POINTS points");
        }
        double[] xyz = new double[3 * n];
        for (int i = 0; i < n; i++) {
            Vec3 p = points.get(i);
            if (p == null || !p.isFinite()) {
                throw new IllegalArgumentException("Shape.points: nonnull points with finite components required");
            }
            xyz[3 * i] = p.x();
            xyz[3 * i + 1] = p.y();
            xyz[3 * i + 2] = p.z();
        }
        return new Shape(xyz);
    }

    public static Shape of(SampleSet samples) {
        return points(SceneChecks.nonnull(samples, "Shape.of: samples").points());
    }

    public int size() {
        return xyz.length / 3;
    }

    public Vec3 point(int index) {
        if (index < 0 || index >= size()) {
            throw new IndexOutOfBoundsException("Shape.point: index " + index + " outside [0, " + size() + ")");
        }
        return new Vec3(xyz[3 * index], xyz[3 * index + 1], xyz[3 * index + 2]);
    }

    double x(int index) {
        return xyz[3 * index];
    }

    double y(int index) {
        return xyz[3 * index + 1];
    }

    double z(int index) {
        return xyz[3 * index + 2];
    }
}
