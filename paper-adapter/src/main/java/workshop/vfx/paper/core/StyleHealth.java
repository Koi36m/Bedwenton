package workshop.vfx.paper.core;

import java.util.Set;
import workshop.vfx.scene.StyleKey;

/**
 * Session-wide particle-style health (contract section 7.1). A style's
 * consecutive-failure streak counts {@link RuntimeException}s from {@link ParticleSink#send} for that style across
 * recipients and ticks; any successful send of that style resets it to 0. The third consecutive failure disables the
 * style for the rest of the plugin session (until disable/enable); disabling is permanent within the session.
 * Main thread only.
 */
public interface StyleHealth {
    /** The catalogue's styles minus the disabled ones: exactly what the next {@code DeliveryPlanner.plan} receives. */
    Set<StyleKey> enabledStyles();

    boolean isDisabled(StyleKey style);

    void recordSuccess(StyleKey style);

    /** Records a failure; returns true exactly once, when this failure disables the style. */
    boolean recordFailure(StyleKey style);

    int streak(StyleKey style);

    int DISABLE_AFTER_CONSECUTIVE_FAILURES = 3;
}
