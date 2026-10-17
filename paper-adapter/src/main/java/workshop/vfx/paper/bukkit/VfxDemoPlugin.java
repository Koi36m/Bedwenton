package workshop.vfx.paper.bukkit;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import workshop.vfx.paper.core.*;
import workshop.vfx.scene.StyleKey;

/** Ordinary Paper adapter. All platform calls run exclusively on the main thread. */
public final class VfxDemoPlugin extends JavaPlugin implements Listener {
    private static final String USE = "bedwenton.vfxdemo.use", VIEW = "bedwenton.vfxdemo.view", ADMIN = "bedwenton.vfxdemo.admin";
    private final Map<StyleKey, ResolvedParticle> particles = new HashMap<>();
    private ThreadGuard thread;
    private DefaultSessionIds ids;
    private DemoConfig config;
    private RingDriver driver;
    private SessionStyleHealth health;
    private DemoCommands commands;
    private final Properties provenance = new Properties();
    private long lastDebugTick = Long.MIN_VALUE;
    private long lastStyleReportTick = Long.MIN_VALUE;
    private record ResolvedParticle(Particle particle, Object data) { }

    @Override public void onEnable() {
        thread = new ThreadGuard(Bukkit::isPrimaryThread); thread.check();
        try {
            saveDefaultConfig();
            config = DemoConfig.from(getConfig().getValues(false));
            try (InputStream in = getResource("META-INF/bedwenton-build.properties")) {
                if (in == null) throw new IllegalArgumentException("Build provenance is missing");
                provenance.load(in);
            }
            ids = new DefaultSessionIds(thread);
            resolveCatalogue();
            health = new SessionStyleHealth(particles.keySet(),thread);
            driver = new RingDriver(thread,new PaperHost(),config,ids,health,this::validatePoint,this::send);
            commands = new DemoCommands(thread,config.defaultTier());
            registerCommand("vfxdemo","Opt-in static VFX ring",List.of(),new PaperCommand());
            Bukkit.getPluginManager().registerEvents(this,this);
            getLogger().info("Bedwenton VFX " + getPluginMeta().getVersion() + "; engine tree=" + provenance.getProperty("engineTree")
                + "; paper-api=" + provenance.getProperty("paperApiVersion") + "; server=" + Bukkit.getVersion()
                + "; Java=" + Runtime.version() + "; idle; /reload unsupported");
        } catch (IllegalArgumentException | IOException error) {
            getLogger().severe("VFX enable refused: " + error.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }
    private void resolveCatalogue() {
        for (var entry : DemoStyleCatalog.whitelist().entrySet()) {
            try {
                Particle particle; Object data;
                if (entry.getValue() instanceof ParticleSpec.Plain) {
                    particle = Particle.END_ROD; data = null;
                    if (particle.getDataType() != Void.class) throw new IllegalArgumentException("Plain data type mismatch");
                } else if (entry.getValue() instanceof ParticleSpec.Dust dust) {
                    particle = Particle.DUST;
                    if (particle.getDataType() != Particle.DustOptions.class) throw new IllegalArgumentException("Dust data type mismatch");
                    data = new Particle.DustOptions(Color.fromRGB(dust.rgb()),dust.size());
                } else throw new IllegalArgumentException("Unknown style specification");
                particles.put(entry.getKey(),new ResolvedParticle(particle,data));
            } catch (RuntimeException error) {
                getLogger().warning("Particle style unavailable: " + entry.getKey());
            }
        }
    }
    @Override public void onDisable() {
        if (thread == null) return;
        thread.check();
        if (driver != null) driver.close();
        Bukkit.getScheduler().cancelTasks(this);
        if (ids != null) ids.clear();
        particles.clear(); health = null; commands = null;
        long pending = Bukkit.getScheduler().getPendingTasks().stream().filter(t -> t.getOwner() == this && !t.isCancelled()).count();
        getLogger().info("VFX disabled; remaining owned tasks=" + pending);
    }
    private boolean inside(World world, double x, double y, double z) {
        return world != null && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
            && y >= world.getMinHeight() && y < world.getMaxHeight()
            && world.getWorldBorder().isInside(new Location(world,x,y,z));
    }
    private Optional<SkipReason> validatePoint(int worldId, double x, double y, double z) {
        thread.check();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return Optional.of(SkipReason.POINT_NOT_FINITE);
        UUID uuid = ids.world(worldId); World world = uuid == null ? null : Bukkit.getWorld(uuid);
        if (world == null) return Optional.of(SkipReason.POINT_OUTSIDE_WORLD);
        return WorldPointRules.check(x,y,z,world.getMinHeight(),world.getMaxHeight(),
            () -> world.getWorldBorder().isInside(new Location(world,x,y,z)));
    }
    private void send(long viewerId, int worldId, StyleKey style, double x, double y, double z) {
        thread.check();
        UUID uuid = ids.viewer(viewerId); Player player = uuid == null ? null : Bukkit.getPlayer(uuid);
        UUID world = ids.world(worldId); ResolvedParticle resolved = particles.get(style);
        if (player == null || !player.isOnline() || !player.isValid() || !player.getWorld().getUID().equals(world) || resolved == null)
            throw new IllegalStateException("Particle recipient or style unavailable");
        player.spawnParticle(resolved.particle(),x,y,z,0,0.0,0.0,0.0,0.0,resolved.data(),false);
    }
    private final class PaperHost implements RingDriver.Host {
        @Override public long tick() { thread.check(); return Bukkit.getCurrentTick(); }
        @Override public long nanoTime() { thread.check(); return System.nanoTime(); }
        @Override public boolean worldReady(UUID uuid, double x, double y, double z) {
            thread.check(); World world = Bukkit.getWorld(uuid);
            if (!inside(world,x,y,z)) return false;
            return world.isChunkLoaded(((int)Math.floor(x)) >> 4,((int)Math.floor(z)) >> 4);
        }
        @Override public ViewerFacts facts(UUID uuid) {
            thread.check(); Player player = uuid == null ? null : Bukkit.getPlayer(uuid);
            if (player == null) return null;
            Location p = player.getLocation();
            return new ViewerFacts(player.isOnline(),player.isValid(),ids.worldId(player.getWorld().getUID()),
                p.getX(),p.getY(),p.getZ(),player.hasPermission(USE),player.hasPermission(VIEW));
        }
        @Override public List<UUID> players() {
            thread.check(); return Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList();
        }
        @Override public Runnable schedule(Runnable body) {
            thread.check();
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(VfxDemoPlugin.this,() -> {
                thread.check(); body.run();
                EmissionReport report = driver.lastReport();
                if (report != null) {
                    if (lastStyleReportTick != report.tickOrdinal()) {
                        lastStyleReportTick = report.tickOrdinal();
                        for (StyleKey style : report.stylesDisabledThisTick()) getLogger().warning("Particle style disabled for this session: " + style);
                    }
                    if (config.debug() && report.tickOrdinal() != lastDebugTick && Math.floorMod(report.tickOrdinal(),100) == 0) {
                        lastDebugTick = report.tickOrdinal(); getLogger().info("VFX tick: " + report);
                    }
                }
            },1L,1L);
            return task::cancel;
        }
    }
    private final class PaperCommand implements BasicCommand {
        @Override public String permission() { return USE; }
        @Override public void execute(CommandSourceStack source, String[] args) {
            thread.check();
            if (commands == null || driver == null) return;
            Player player = source.getExecutor() instanceof Player p ? p : null;
            commands.execute(new DemoCommands.Endpoint() {
                @Override public boolean permitted() { return source.getSender().hasPermission(USE); }
                @Override public boolean admin() { return source.getSender().hasPermission(ADMIN); }
                @Override public boolean player() { return player != null; }
                @Override public void start(workshop.vfx.delivery.QualityTier tier) {
                    Location p = player.getLocation();
                    driver.start(player.getUniqueId(),player.getWorld().getUID(),p.getBlockX()+0.5,p.getBlockY()+0.1,p.getBlockZ()+0.5,tier);
                }
                @Override public void stop() { driver.stop(player.getUniqueId()); }
                @Override public void stopAll() { driver.stopAll(); }
                @Override public String status() { return statusText(player,admin()); }
                @Override public void reply(String text) { source.getSender().sendMessage(text); }
            },args);
        }
        @Override public Collection<String> suggest(CommandSourceStack source, String[] args) {
            thread.check(); return commands == null ? List.of() : commands.suggest(args,source.getSender().hasPermission(ADMIN));
        }
    }
    private String statusText(Player player, boolean admin) {
        int count = admin ? driver.activeCount() : player != null && driver.ring(player.getUniqueId()) != null ? 1 : 0;
        return "VFX " + getPluginMeta().getVersion() + "; engine=0.1.0-m1 tree=" + provenance.getProperty("engineTree")
            + "; paper-api=" + provenance.getProperty("paperApiVersion") + "; server=" + Bukkit.getVersion()
            + "; rings=" + count + "; task=" + (driver.running() ? "running" : "idle")
            + "; effective policy=" + config.policy() + "; disabled styles="
            + particles.keySet().stream().filter(health::isDisabled).toList()
            + "; last tick=" + driver.lastReport() + "; /reload unsupported";
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        thread.check(); if (driver == null) return; driver.stop(event.getPlayer().getUniqueId()); ids.release(event.getPlayer().getUniqueId());
    }
    @EventHandler public void changedWorld(PlayerChangedWorldEvent event) {
        thread.check(); if (driver != null) driver.stop(event.getPlayer().getUniqueId());
    }
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true) public void teleport(PlayerTeleportEvent event) {
        thread.check(); if (driver == null) return;
        Location to=event.getTo();
        if (to != null && to.getWorld() != null && driver.leavesAnchor(event.getPlayer().getUniqueId(),to.getWorld().getUID(),to.getX(),to.getY(),to.getZ()))
            driver.stop(event.getPlayer().getUniqueId());
    }
    @EventHandler public void death(PlayerDeathEvent event) {
        thread.check(); if (driver != null) driver.stop(event.getEntity().getUniqueId());
    }
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true) public void unload(WorldUnloadEvent event) {
        thread.check(); if (driver == null) return; UUID world=event.getWorld().getUID(); driver.stopWorld(world); ids.forgetWorld(world);
    }
}
