package workshop.vfx.delivery;

/** Shared input validation, with no evaluation or proportional allocation. */
final class DeliveryChecks {
    private DeliveryChecks() { }

    static <T> T nonnull(T value, String rule) {
        if (value == null) {
            throw new IllegalArgumentException(rule + " must be nonnull");
        }
        return value;
    }

    static boolean name(String name) {
        if (name == null || name.isEmpty() || name.length() > 64) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '.' || c == '-')) {
                return false;
            }
        }
        return true;
    }

    static boolean budget(long value) {
        return value >= 0 && value <= DeliveryPolicy.MAX_BUDGET;
    }
}
