package workshop.vfx.paper.streams;

/** Experimental Paper extra multipliers, not measured final client velocities. */
public enum StreamSpeed {
    SLOW(.10), NORMAL(.15), FAST(.22);
    private final double value;
    StreamSpeed(double value){this.value=value;}
    public double value(){return value;}
}
