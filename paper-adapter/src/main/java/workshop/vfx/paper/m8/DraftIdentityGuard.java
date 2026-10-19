package workshop.vfx.paper.m8;
import workshop.vfx.math.time.TimeDomain;
import workshop.vfx.paper.core.SessionIds;
/** Defence in depth before a draft becomes a request or an ownership context. */
public final class DraftIdentityGuard {
    private DraftIdentityGuard() { }
    public static boolean accepts(EffectDraft d, SessionIds ids, long tick) {
        if (d == null || d.owner() == null || d.anchor() == null || d.requestId() < DynamicEffectRegistry.FIRST_ID
                || d.kind() == null || d.kind() == EffectKind.RING) return false;
        if (!d.owner().equals(ids.viewer(d.ownerViewerId())) || d.worldId() < 0 || ids.world(d.worldId()) == null) return false;
        if (d.recipe() == null || d.tier() == null || d.playback() == null || d.now() == null || d.frameOrdinal() < 0) return false;
        if (d.recipe().domain() != TimeDomain.GAME || d.playback().anchor().domain() != TimeDomain.GAME
                || d.now().domain() != TimeDomain.GAME || d.now().value() != tick) return false;
        var a = d.anchor().translation();
        return Double.isFinite(a.x()) && Double.isFinite(a.y()) && Double.isFinite(a.z());
    }
}
