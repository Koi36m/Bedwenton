package workshop.vfx.scene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.time.Playback;
import workshop.vfx.math.time.TimeDomain;
import workshop.vfx.math.time.TimePoint;
import workshop.vfx.math.time.TimingSample;

/**
 * An immutable, validated and preflighted effect scene (contract section 6). {@link #evaluate} is a pure function of
 * its arguments: it reads no clock, keeps no state and calls no curve (contract sections 7 to 9).
 */
public final class EffectScene {
    public static final double UNBOUNDED = Double.POSITIVE_INFINITY;

    private final TimeDomain domain;
    private final double lifetimeTicks;
    private final List<Layer> layers;
    private final SceneLimits limits;
    private final int maxPointsPerFrame;

    private EffectScene(TimeDomain domain, double lifetimeTicks, List<Layer> layers, SceneLimits limits, int maxPointsPerFrame) {
        this.domain = domain;
        this.lifetimeTicks = lifetimeTicks;
        this.layers = layers;
        this.limits = limits;
        this.maxPointsPerFrame = maxPointsPerFrame;
    }

    public static EffectScene of(TimeDomain domain, double lifetimeTicks, List<Layer> layers, SceneLimits limits) {
        SceneChecks.nonnull(domain, "EffectScene.of: domain");
        SceneChecks.nonnull(layers, "EffectScene.of: layers");
        SceneChecks.nonnull(limits, "EffectScene.of: limits");
        if (!(lifetimeTicks > 0)) {
            throw new IllegalArgumentException("EffectScene.of: lifetimeTicks must be positive (finite or UNBOUNDED)");
        }
        // Size before copying: the copy is proportional to the list (binding addendum, correction 5).
        if (layers.size() > limits.maxLayers()) {
            throw new IllegalArgumentException("EffectScene.of: more layers than limits.maxLayers()");
        }
        List<Layer> copy = new ArrayList<>(layers);
        if (copy.size() > limits.maxLayers()) {
            throw new IllegalArgumentException("EffectScene.of: more layers than limits.maxLayers()");
        }
        Set<String> names = new HashSet<>();
        long points = 0;
        for (Layer layer : copy) {
            SceneChecks.nonnull(layer, "EffectScene.of: layer");
            if (!names.add(layer.name())) {
                throw new IllegalArgumentException("EffectScene.of: duplicate layer name " + layer.name());
            }
            points += layer.shape().size();
        }
        if (points > limits.maxPointsPerFrame()) {
            throw new IllegalArgumentException("EffectScene.of: total shape points exceed limits.maxPointsPerFrame()");
        }
        return new EffectScene(domain, lifetimeTicks, Collections.unmodifiableList(copy), limits, (int) points);
    }

    public TimeDomain domain() {
        return domain;
    }

    public double lifetimeTicks() {
        return lifetimeTicks;
    }

    public List<Layer> layers() {
        return layers;
    }

    public SceneLimits limits() {
        return limits;
    }

    /** The sum of all shape sizes: no frame of this scene can contain more points. */
    public int maxPointsPerFrame() {
        return maxPointsPerFrame;
    }

    /** Contract sections 7 and 8, in their exact order. Returns a complete frame or throws. */
    public EffectFrame evaluate(Pose anchor, Playback playback, TimePoint now) {
        SceneChecks.nonnull(anchor, "EffectScene.evaluate: anchor");
        SceneChecks.nonnull(playback, "EffectScene.evaluate: playback");
        SceneChecks.nonnull(now, "EffectScene.evaluate: now");
        if (playback.anchor().domain() != domain || now.domain() != domain) {
            throw new IllegalArgumentException("EffectScene.evaluate: playback and instant must use the scene's time domain");
        }
        double local = playback.localTicks(now);
        if (!Double.isFinite(local)) {
            throw new ArithmeticException("EffectScene.evaluate: local ticks are not finite");
        }
        if (local == 0.0) {
            local = 0.0; // -0.0 is the scene start
        }
        if (local < 0.0) {
            return new EffectFrame(TimingSample.Stage.BEFORE, local, List.of(), 0);
        }
        if (local >= lifetimeTicks) {
            return new EffectFrame(TimingSample.Stage.AFTER, local, List.of(), 0);
        }
        List<FrameBatch> batches = new ArrayList<>(layers.size());
        int total = 0;
        for (int index = 0; index < layers.size(); index++) {
            Layer layer = layers.get(index);
            TimingSample sample = layer.timing().sample(local);
            boolean visible = sample.stage() == TimingSample.Stage.ACTIVE
                    || (sample.stage() == TimingSample.Stage.AFTER && layer.visibility() == Visibility.HOLD_AFTER);
            if (!visible) {
                continue;
            }
            Shape shape = layer.shape();
            int n = shape.size();
            int k = layer.reveal() == Reveal.ALL ? n : (int) Math.floor(sample.progress() * n);
            if (k == 0) {
                continue;
            }
            Pose pose = anchor.then(layer.motion().poseAt(local));
            double[] xyz = new double[3 * k]; // k <= n, and the shape sizes sum to at most maxPointsPerFrame
            for (int j = 0; j < k; j++) {
                Vec3 w = pose.toWorld(new Vec3(shape.x(j), shape.y(j), shape.z(j)));
                if (!w.isFinite()) {
                    throw new ArithmeticException("EffectScene.evaluate: non-finite position in layer " + layer.name());
                }
                xyz[3 * j] = w.x();
                xyz[3 * j + 1] = w.y();
                xyz[3 * j + 2] = w.z();
            }
            batches.add(new FrameBatch(index, layer.name(), layer.style(), sample, pose, xyz));
            total += k;
        }
        return new EffectFrame(TimingSample.Stage.ACTIVE, local, Collections.unmodifiableList(batches), total);
    }
}
