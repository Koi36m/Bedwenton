package workshop.vfx.paper.m8;

import java.util.Objects;
import java.util.UUID;

/**
 * K0 foundation. The identity an effect is bound to when it starts (R3 F6): the owner UUID and
 * the owner's M7 SessionIds viewer id, both fixed for the effect's lifetime. Drafts carry these bound values, never the
 * snapshot's, so a malformed snapshot cannot change whose effect it is or who is treated as the owner viewer.
 */
public record AnchorBinding(UUID owner, long ownerViewerId) {
    public AnchorBinding {
        Objects.requireNonNull(owner, "owner");
        if (ownerViewerId < 0) throw new IllegalArgumentException("ownerViewerId must be a SessionIds id (>= 0)");
    }
}
