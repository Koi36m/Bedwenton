package workshop.vfx.scene;

/** Shared argument validation for the scene package (contract section 3). */
final class SceneChecks {
    private SceneChecks() { }

    static <T> T nonnull(T value, String what) {
        if (value == null) {
            throw new IllegalArgumentException(what + " must not be null");
        }
        return value;
    }

    /** {@code [a-z0-9_.-]{1,32} ':' [a-z0-9_./-]{1,64}}: exactly one colon; the slash only in the path. */
    static boolean isStyleKey(String s) {
        int colon = s.indexOf(':');
        if (colon < 1 || colon > 32 || s.indexOf(':', colon + 1) >= 0) {
            return false;
        }
        int pathLength = s.length() - colon - 1;
        if (pathLength < 1 || pathLength > 64) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (i != colon && !(isNameChar(s.charAt(i)) || (i > colon && s.charAt(i) == '/'))) {
                return false;
            }
        }
        return true;
    }

    /** {@code [a-z0-9_.-]{1,64}}. */
    static boolean isLayerName(String s) {
        if (s.isEmpty() || s.length() > 64) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!isNameChar(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isNameChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-';
    }
}
