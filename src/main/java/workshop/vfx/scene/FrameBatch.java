package workshop.vfx.scene;

import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.time.TimingSample;

/** One visible, non-empty layer of a frame: identity, style, M2 sample, world pose and packed positions. */
public final class FrameBatch {
    private final int layerIndex;
    private final String layer;
    private final StyleKey style;
    private final TimingSample timing;
    private final Pose pose;
    private final double[] xyz;

    /** Takes ownership of {@code xyz}, which the caller must not retain. */
    FrameBatch(int layerIndex, String layer, StyleKey style, TimingSample timing, Pose pose, double[] xyz) {
        this.layerIndex = layerIndex;
        this.layer = layer;
        this.style = style;
        this.timing = timing;
        this.pose = pose;
        this.xyz = xyz;
    }

    public int layerIndex() {
        return layerIndex;
    }

    public String layer() {
        return layer;
    }

    public StyleKey style() {
        return style;
    }

    public TimingSample timing() {
        return timing;
    }

    public Pose pose() {
        return pose;
    }

    public int size() {
        return xyz.length / 3;
    }

    public double x(int i) {
        return xyz[3 * checked(i)];
    }

    public double y(int i) {
        return xyz[3 * checked(i) + 1];
    }

    public double z(int i) {
        return xyz[3 * checked(i) + 2];
    }

    public Vec3 point(int i) {
        int k = 3 * checked(i);
        return new Vec3(xyz[k], xyz[k + 1], xyz[k + 2]);
    }

    /** A new copy: x0, y0, z0, x1, ... */
    public double[] coordinates() {
        return xyz.clone();
    }

    private int checked(int i) {
        if (i < 0 || i >= size()) {
            throw new IndexOutOfBoundsException("FrameBatch: index " + i + " outside [0, " + size() + ")");
        }
        return i;
    }
}
