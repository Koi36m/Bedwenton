package workshop.vfx.scene;

/**
 * An abstract visual style, {@code namespace:path}, resolved only by a platform adapter (contract section 3).
 * {@link #toString()} returns the raw value.
 */
public record StyleKey(String value) {
    public StyleKey {
        if (value == null || !SceneChecks.isStyleKey(value)) {
            throw new IllegalArgumentException(
                    "StyleKey: [a-z0-9_.-]{1,32}:[a-z0-9_./-]{1,64} required");
        }
    }

    public static StyleKey of(String value) {
        return new StyleKey(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
