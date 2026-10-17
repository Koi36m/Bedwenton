package workshop.vfx.paper.core;
import java.util.Objects;
import java.util.function.BooleanSupplier;
/** All mutable adapter state belongs to one server thread. */
public final class ThreadGuard {
    private final Thread owner = Thread.currentThread();
    private final BooleanSupplier primary;
    public ThreadGuard() { this(() -> true); }
    public ThreadGuard(BooleanSupplier primary) { this.primary = Objects.requireNonNull(primary); }
    public void check() {
        if (Thread.currentThread() != owner || !primary.getAsBoolean())
            throw new IllegalStateException("VFX adapter requires its primary thread");
    }
}
