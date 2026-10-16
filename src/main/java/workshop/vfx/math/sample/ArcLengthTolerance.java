package workshop.vfx.math.sample;

/** Requested absolute and relative contributions to the effective tolerance. */
public record ArcLengthTolerance(double absolute, double relative) {
    public static final double MIN_RELATIVE = 0x1p-40;
    public static final double MAX_RELATIVE = 0x1p-3;

    public ArcLengthTolerance {
        if (!Double.isFinite(absolute) || absolute < 0
                || !(relative >= MIN_RELATIVE && relative <= MAX_RELATIVE)) {
            throw new IllegalArgumentException("Invalid arc-length tolerance");
        }
    }
}
