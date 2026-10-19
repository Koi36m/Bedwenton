package workshop.vfx.paper.bukkit;

import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import workshop.vfx.paper.core.DefaultSessionIds;
import workshop.vfx.paper.core.ThreadGuard;
import workshop.vfx.paper.m8.AnchorSnapshot;
import workshop.vfx.paper.m8.AnchorSource;

/** Player-only facts for the K0 identity guard. No attachment, geometry, chunk loading or scheduling. */
public final class PaperAnchorSource implements AnchorSource {
    private final ThreadGuard thread;
    private final DefaultSessionIds ids;
    public PaperAnchorSource(ThreadGuard thread,DefaultSessionIds ids) {this.thread=Objects.requireNonNull(thread);this.ids=Objects.requireNonNull(ids);}
    @Override public AnchorSnapshot snapshot(UUID owner,long planningTick) {
        thread.check();Player player=Bukkit.getPlayer(owner);
        if(player==null)return null;
        Location p=player.getLocation();World world=player.getWorld();UUID actualOwner=player.getUniqueId();
        // Snapshot reads never recreate a released owner mapping; the guard rejects the absent id.
        long viewer=ids.existingViewerId(actualOwner).orElse(-1L);
        boolean chunkLoaded=Double.isFinite(p.getX()) && Double.isFinite(p.getZ())
            && world.isChunkLoaded(((int)Math.floor(p.getX()))>>4,((int)Math.floor(p.getZ()))>>4);
        return new AnchorSnapshot(Bukkit.getCurrentTick(),actualOwner,viewer,world.getUID(),ids.worldId(world.getUID()),
            p.getX(),p.getY(),p.getZ(),player.getBodyYaw(),p.getYaw(),p.getPitch(),world.getMinHeight(),world.getMaxHeight(),
            chunkLoaded,player.isOnline(),player.isValid(),player.isDead(),0L);
        // Discontinuity histories/listeners belong to later visual checkpoints; K0 has none.
    }
}
