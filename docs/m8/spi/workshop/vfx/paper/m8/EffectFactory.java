package workshop.vfx.paper.m8;

/**
 * M8 R3 SPI PROPOSAL (not production). Builds a candidate effect for {@code DynamicEffectRegistry.start} (R3 F5). The
 * registry passes the request id and the binding it computed; the candidate must report exactly that id, the bound owner
 * and the requested kind, or the start fails and nothing changes. Must not register, schedule or touch Paper objects.
 */
@FunctionalInterface
public interface EffectFactory {
    DynamicEffect create(long requestId, AnchorBinding binding);
}
