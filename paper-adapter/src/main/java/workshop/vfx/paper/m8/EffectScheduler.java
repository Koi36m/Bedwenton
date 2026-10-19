package workshop.vfx.paper.m8;

/**
 * K0 foundation. What the single shared driver offers the M8 registry (architecture section 4):
 * one capacity rule for rings and M8 effects together, and the one shared task. Main thread only.
 */
public interface EffectScheduler {
    /** True when one more effect fits: {@code replacing || rings + m8Effects < maxActiveEffects} (F1). */
    boolean hasCapacityFor(boolean replacing);

    /** Starts the one shared repeating task if it is not running (idempotent). */
    void ensureRunning();

    /** Cancels the shared task when no ring and no M8 effect remains. */
    void effectsChanged();
}
