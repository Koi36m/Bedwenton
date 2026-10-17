package workshop.vfx.paper.core;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.UUID;
/** Sequential, collision-free identifiers. Released numbers are never reused. */
public final class DefaultSessionIds implements SessionIds {
    private final ThreadGuard thread;
    private final Map<UUID, Long> players = new HashMap<>();
    private final Map<Long, UUID> viewers = new HashMap<>();
    private final Map<UUID, Integer> worlds = new HashMap<>();
    private final Map<Integer, UUID> worldIds = new HashMap<>();
    private long nextViewer;
    private long nextWorld;
    public DefaultSessionIds(ThreadGuard thread) { this.thread = Objects.requireNonNull(thread); }
    @Override public long viewerId(UUID player) {
        thread.check(); Objects.requireNonNull(player);
        Long id = players.get(player);
        if (id != null) return id;
        if (nextViewer == Long.MAX_VALUE) throw new IllegalStateException("Viewer session ids exhausted");
        long value = nextViewer++; players.put(player, value); viewers.put(value, player); return value;
    }
    @Override public OptionalLong existingViewerId(UUID player) {
        thread.check(); Long id = players.get(player); return id == null ? OptionalLong.empty() : OptionalLong.of(id);
    }
    @Override public UUID viewer(long id) { thread.check(); return viewers.get(id); }
    @Override public int worldId(UUID world) {
        thread.check(); Objects.requireNonNull(world);
        Integer id = worlds.get(world); if (id != null) return id;
        if (nextWorld > Integer.MAX_VALUE) throw new IllegalStateException("World session ids exhausted");
        int value = (int) nextWorld++; worlds.put(world, value); worldIds.put(value, world); return value;
    }
    @Override public UUID world(int id) { thread.check(); return worldIds.get(id); }
    @Override public void release(UUID player) { thread.check(); Long id = players.remove(player); if (id != null) viewers.remove(id); }
    public void forgetWorld(UUID world) { thread.check(); Integer id = worlds.remove(world); if (id != null) worldIds.remove(id); }
    public void clear() { thread.check(); players.clear(); viewers.clear(); worlds.clear(); worldIds.clear(); }
}
