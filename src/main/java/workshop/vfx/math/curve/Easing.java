package workshop.vfx.math.curve;

/** Polynomial easing on [0, 1], with clamped inputs and outputs. */
public enum Easing {
    LINEAR, SMOOTHSTEP, SMOOTHERSTEP,
    QUAD_IN, QUAD_OUT, QUAD_IN_OUT,
    CUBIC_IN, CUBIC_OUT, CUBIC_IN_OUT,
    QUART_IN, QUART_OUT, QUART_IN_OUT,
    QUINT_IN, QUINT_OUT, QUINT_IN_OUT;

    public double apply(double progress) {
        if (Double.isNaN(progress)) {
            throw new IllegalArgumentException("Easing.apply: progress must not be NaN");
        }
        if (progress <= 0) return 0.0;
        if (progress >= 1) return 1.0;
        double t = progress;
        double result = switch (this) {
            case LINEAR -> t;
            case SMOOTHSTEP -> t <= 0.5 ? smooth(t) : 1 - smooth(1 - t);
            case SMOOTHERSTEP -> t <= 0.5 ? smoother(t) : 1 - smoother(1 - t);
            case QUAD_IN -> power(t, 2);
            case QUAD_OUT -> 1 - power(1 - t, 2);
            case QUAD_IN_OUT -> inOut(t, 2);
            case CUBIC_IN -> power(t, 3);
            case CUBIC_OUT -> 1 - power(1 - t, 3);
            case CUBIC_IN_OUT -> inOut(t, 3);
            case QUART_IN -> power(t, 4);
            case QUART_OUT -> 1 - power(1 - t, 4);
            case QUART_IN_OUT -> inOut(t, 4);
            case QUINT_IN -> power(t, 5);
            case QUINT_OUT -> 1 - power(1 - t, 5);
            case QUINT_IN_OUT -> inOut(t, 5);
        };
        return Math.max(0.0, Math.min(1.0, result));
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    private static double smoother(double t) {
        return t * t * t * (t * (6 * t - 15) + 10);
    }

    private static double power(double t, int n) {
        double result = t;
        for (int i = 1; i < n; i++) result *= t;
        return result;
    }

    private static double inOut(double t, int n) {
        return t < 0.5 ? (1 << (n - 1)) * power(t, n) : 1 - power(2 - 2 * t, n) / 2;
    }
}
