package workshop.vfx.paper.m8;

import java.util.Objects;

/** K0 foundation. Result of {@link AnchorIdentityGuard#validate}: the state and the first failed check. */
public record AnchorVerdict(AnchorState state, AnchorRejection reason) {
    public static final AnchorVerdict READY = new AnchorVerdict(AnchorState.READY, AnchorRejection.NONE);

    public AnchorVerdict {
        Objects.requireNonNull(state); Objects.requireNonNull(reason);
        if ((state == AnchorState.READY) != (reason == AnchorRejection.NONE))
            throw new IllegalArgumentException("READY iff reason NONE");
    }

    public static AnchorVerdict skip(AnchorRejection reason) { return new AnchorVerdict(AnchorState.SKIP_TICK, reason); }

    public static AnchorVerdict terminate(AnchorRejection reason) { return new AnchorVerdict(AnchorState.TERMINATE, reason); }
}
