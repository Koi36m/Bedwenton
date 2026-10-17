package workshop.vfx.scene;

import workshop.vfx.math.time.Timing;

/** One clip of a scene: what is shown (shape, style), where (motion), and when (timing, visibility, reveal). */
public record Layer(String name, Timing timing, Visibility visibility, Reveal reveal, Motion motion, Shape shape,
        StyleKey style) {
    public Layer {
        if (name == null || !SceneChecks.isLayerName(name)) {
            throw new IllegalArgumentException("Layer: name [a-z0-9_.-]{1,64} required");
        }
        SceneChecks.nonnull(timing, "Layer: timing");
        SceneChecks.nonnull(visibility, "Layer: visibility");
        SceneChecks.nonnull(reveal, "Layer: reveal");
        SceneChecks.nonnull(motion, "Layer: motion");
        SceneChecks.nonnull(shape, "Layer: shape");
        SceneChecks.nonnull(style, "Layer: style");
    }
}
