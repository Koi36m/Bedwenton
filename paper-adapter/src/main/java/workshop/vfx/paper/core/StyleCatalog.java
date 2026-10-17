package workshop.vfx.paper.core;

import java.util.Map;
import java.util.Set;
import workshop.vfx.scene.StyleKey;

/**
 * The closed whitelist: exactly these style keys are passed to
 * {@code DeliveryPlanner.plan} as {@code supportedStyles}; every other style is unsupported and never emitted.
 */
public interface StyleCatalog {
    Set<StyleKey> supportedStyles();

    /** The spec of a supported style; never a fallback for an unknown key. */
    ParticleSpec spec(StyleKey style);

    Map<StyleKey, ParticleSpec> asMap();
}
