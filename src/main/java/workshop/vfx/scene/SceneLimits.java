package workshop.vfx.scene;

/** Hard per-scene ceilings, checked when the scene is built (contract sections 6 and 9). */
public record SceneLimits(int maxLayers, int maxPointsPerFrame) {
    public static final int MAX_LAYERS_CEILING = 4096;
    public static final int MAX_POINTS_CEILING = 1 << 20;

    public SceneLimits {
        if (maxLayers < 1 || maxLayers > MAX_LAYERS_CEILING || maxPointsPerFrame < 1 || maxPointsPerFrame > MAX_POINTS_CEILING) {
            throw new IllegalArgumentException("SceneLimits: 1 <= maxLayers <= 4096 and 1 <= maxPointsPerFrame <= 2^20 required");
        }
    }
}
