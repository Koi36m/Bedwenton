package workshop.vfx.scene;

import java.util.List;
import workshop.vfx.math.time.TimingSample;

/** An immutable frame snapshot: scene stage, local ticks and the visible batches in layer order. */
public final class EffectFrame {
    private final TimingSample.Stage stage;
    private final double localTicks;
    private final List<FrameBatch> batches;
    private final int pointCount;

    /** {@code batches} must be unmodifiable. */
    EffectFrame(TimingSample.Stage stage, double localTicks, List<FrameBatch> batches, int pointCount) {
        this.stage = stage;
        this.localTicks = localTicks;
        this.batches = batches;
        this.pointCount = pointCount;
    }

    public TimingSample.Stage stage() {
        return stage;
    }

    public double localTicks() {
        return localTicks;
    }

    public List<FrameBatch> batches() {
        return batches;
    }

    public int pointCount() {
        return pointCount;
    }

    public boolean isEmpty() {
        return batches.isEmpty();
    }
}
