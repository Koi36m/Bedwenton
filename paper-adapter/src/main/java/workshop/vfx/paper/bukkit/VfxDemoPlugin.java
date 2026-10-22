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
import workshop.vfx.delivery.DeliveryPlanner;
import workshop.vfx.paper.m8.DynamicEffectRegistry;
import workshop.vfx.paper.m8.M8Config;
import workshop.vfx.paper.m8.AuraSession;
import workshop.vfx.paper.m8.M8Commands;
import workshop.vfx.paper.m8.M8StyleCatalog;
import workshop.vfx.paper.m8.TimedAnchorSource;
import workshop.vfx.paper.reference.ReferenceCommands;
import workshop.vfx.paper.reference.ReferenceSession;
import workshop.vfx.paper.reference.ReferenceStyleCatalog;
import workshop.vfx.paper.streams.*;

/** Ordinary Paper adapter. All platform calls run exclusively on the main thread. */
public final class VfxDemoPlugin extends JavaPlugin implements Listener {
    private static final String USE = "bedwenton.vfxdemo.use", VIEW = "bedwenton.vfxdemo.view", ADMIN = "bedwenton.vfxdemo.admin";
    private final Map<StyleKey, ResolvedParticle> particles = new HashMap<>();
    private ThreadGuard thread;
    private DefaultSessionIds ids;
    private DemoConfig config;
    private M8Config m8Config;
    private DynamicEffectRegistry registry;
    private RingDriver driver;
    private SessionStyleHealth health;
    private DemoCommands commands;
    private M8Commands m8Commands;
    private AuraSession auras;
    private ReferenceSession reference;
    private ReferenceCommands referenceCommands;
    private StreamsSession streams;
    private StreamsCommands streamsCommands;
    private TimedAnchorSource anchorTiming;
    private final Properties provenance = new Properties();
    private long lastDebugTick = Long.MIN_VALUE;
    private long lastStyleReportTick = Long.MIN_VALUE;
    private record ResolvedParticle(Particle particle, Object data) { }

    @Override public void onEnable() {
        thread = new ThreadGuard(Bukkit::isPrimaryThread); thread.check();
        try {
            saveDefaultConfig();
            config = DemoConfig.from(getConfig().getValues(false));
            if (getConfig().contains("m8") && !getConfig().isConfigurationSection("m8"))
                throw new IllegalArgumentException("m8 must be a configuration section");
            var section = getConfig().getConfigurationSection("m8");
            m8Config = M8Config.from(section == null ? Map.of() : section.getValues(false));
            try (InputStream in = getResource("META-INF/bedwenton-build.properties")) {
                if (in == null) throw new IllegalArgumentException("Build provenance is missing");
                provenance.load(in);
            }
            ids = new DefaultSessionIds(thread);
            resolveCatalogue();
            health = new SessionStyleHealth(particles.keySet(),thread);
            if (m8Config.enabled()) {
                anchorTiming = new TimedAnchorSource(thread,new PaperAnchorSource(thread,ids),System::nanoTime);
                registry = new DynamicEffectRegistry(thread,anchorTiming,ids,
                    m8Config.maxEffectsPerPlayer(),m8Config.invalidAnchorTicksToTerminate());
                driver = new RingDriver(thread,new PaperHost(),config,ids,health,this::validatePoint,particleSink(),DeliveryPlanner::plan,registry);
                registry.bind(driver);
                auras = new AuraSession(thread,ids,anchorTiming,registry,m8Config);
                reference = new ReferenceSession(thread,ids,anchorTiming,registry,m8Config);
                streams = new StreamsSession(thread,ids,anchorTiming,registry,m8Config);
            } else {
                registry = null;
                auras = null; anchorTiming = null;
                reference = null;
                streams = null;
                driver = new RingDriver(thread,new PaperHost(),config,ids,health,this::validatePoint,this::send);
            }
            commands = new DemoCommands(thread,config.defaultTier());
            m8Commands = new M8Commands(thread,m8Config);
            referenceCommands = new ReferenceCommands(thread,m8Config);
            streamsCommands = new StreamsCommands(thread,m8Config);
            registerCommand("vfxdemo","Opt-in static ring and Arcane Halo aura",List.of(),new PaperCommand());
            registerCommand("vfxreference","Experimental ten-second native particle reference prototype",List.of(),new ReferenceCommand());
            registerCommand("vfxstreams","Experimental directional END_ROD pair",List.of(),new StreamsCommand());
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
        var catalogue = m8Config.enabled() ? StreamsStyleCatalog.whitelist() : DemoStyleCatalog.whitelist();
        for (var entry : catalogue.entrySet()) {
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
        registry = null;
        auras = null; anchorTiming = null; m8Commands = null;
        reference = null; referenceCommands = null;
        streams = null; streamsCommands = null;
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
    private ParticleSink particleSink() {
        return new ParticleSink() {
            @Override public void send(long viewer,int world,StyleKey style,double x,double y,double z) {
                VfxDemoPlugin.this.send(viewer,world,style,x,y,z);
            }
            @Override public void sendDirectional(long viewer,int world,StyleKey style,double x,double y,double z,DirectionalEmission motion) {
                thread.check();
                if(!DirectionalEmission.STYLES.contains(style)||motion==null||!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))
                    throw new IllegalArgumentException("Invalid directional placement");
                UUID uuid=ids.viewer(viewer);Player player=uuid==null?null:Bukkit.getPlayer(uuid);
                UUID target=ids.world(world);ResolvedParticle resolved=particles.get(style);
                if(player==null||!player.isOnline()||!player.isValid()||!player.getWorld().getUID().equals(target)
                        ||resolved==null||resolved.particle()!=Particle.END_ROD)
                    throw new IllegalStateException("Directional recipient or END_ROD unavailable");
                var direction=motion.direction();
                player.spawnParticle(Particle.END_ROD,x,y,z,0,direction.x(),direction.y(),direction.z(),motion.speed(),null,false);
            }
        };
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
                        lastDebugTick = report.tickOrdinal(); getLogger().info("VFX tick: " + report
                            + (anchorTiming == null ? "" : "; snapshot tick=" + anchorTiming.tick()
                                + "; snapshotNanos=" + anchorTiming.snapshotNanos() + "; snapshotReads=" + anchorTiming.reads()));
                    }
                }
            },1L,1L);
            return task::cancel;
        }
    }
    private final class StreamsCommand implements BasicCommand {
        @Override public String permission(){return USE;}
        @Override public void execute(CommandSourceStack source,String[] args){
            thread.check();if(streamsCommands==null||driver==null)return;
            Player player=source.getExecutor() instanceof Player p?p:null;
            streamsCommands.execute(new StreamsCommands.Endpoint(){
                public boolean permitted(){return source.getSender().hasPermission(USE);}
                public boolean player(){return player!=null;}
                public void start(StreamSpeed speed){streams.start(player.getUniqueId(),speed,Bukkit.getCurrentTick());}
                public void stop(){streams.stop(player.getUniqueId());}
                public String status(){return streams.status(player.getUniqueId());}
                public void reply(String text){source.getSender().sendMessage(text);}
            },args);
        }
        @Override public Collection<String> suggest(CommandSourceStack source,String[] args){
            thread.check();return streamsCommands==null||!source.getSender().hasPermission(USE)?List.of():streamsCommands.suggest(args);
        }
    }
    private final class ReferenceCommand implements BasicCommand {
        @Override public String permission() { return USE; }
        @Override public void execute(CommandSourceStack source, String[] args) {
            thread.check();if(referenceCommands==null||driver==null)return;
            Player player=source.getExecutor() instanceof Player p?p:null;
            referenceCommands.execute(new ReferenceCommands.Endpoint() {
                @Override public boolean permitted(){return source.getSender().hasPermission(USE);}
                @Override public boolean player(){return player!=null;}
                @Override public void start(workshop.vfx.delivery.QualityTier tier){reference.start(player.getUniqueId(),tier,Bukkit.getCurrentTick());}
                @Override public void stop(){reference.stop(player.getUniqueId());}
                @Override public String status(){return reference.status(player.getUniqueId());}
                @Override public void reply(String text){source.getSender().sendMessage(text);}
            },args);
        }
        @Override public Collection<String> suggest(CommandSourceStack source,String[] args) {
            thread.check();return referenceCommands==null||!source.getSender().hasPermission(USE)?List.of():referenceCommands.suggest(args);
        }
    }
    private final class PaperCommand implements BasicCommand {
        @Override public String permission() { return USE; }
        @Override public void execute(CommandSourceStack source, String[] args) {
            thread.check();
            if (commands == null || driver == null) return;
            Player player = source.getExecutor() instanceof Player p ? p : null;
            if (m8Commands.handles(args)) {
                m8Commands.execute(new M8Commands.Endpoint() {
                    @Override public boolean permitted() { return source.getSender().hasPermission(USE); }
                    @Override public boolean player() { return player != null; }
                    @Override public void start(workshop.vfx.delivery.QualityTier tier) {
                        auras.start(player.getUniqueId(),tier,Bukkit.getCurrentTick());
                    }
                    @Override public void stopAura() { auras.stop(player.getUniqueId()); }
                    @Override public void reply(String text) { source.getSender().sendMessage(text); }
                },args);
                return;
            }
            commands.execute(new DemoCommands.Endpoint() {
                @Override public boolean permitted() { return source.getSender().hasPermission(USE); }
                @Override public boolean admin() { return source.getSender().hasPermission(ADMIN); }
                @Override public boolean player() { return player != null; }
                @Override public void start(workshop.vfx.delivery.QualityTier tier) {
                    Location p = player.getLocation();
                    driver.start(player.getUniqueId(),player.getWorld().getUID(),p.getBlockX()+0.5,p.getBlockY()+0.1,p.getBlockZ()+0.5,tier);
                }
                @Override public void stop() {
                    driver.stop(player.getUniqueId());
                    if (registry != null) registry.stopOwner(player.getUniqueId());
                    if (registry != null) source.getSender().sendMessage("Your M8 effects stopped.");
                }
                @Override public void stopAll() {
                    driver.stopAll();
                    if (registry != null) { registry.stopAll(); source.getSender().sendMessage("All M8 effects stopped."); }
                }
                @Override public String status() { return statusText(player,admin()); }
                @Override public void reply(String text) { source.getSender().sendMessage(text); }
            },args);
        }
        @Override public Collection<String> suggest(CommandSourceStack source, String[] args) {
            thread.check();
            if (commands == null) return List.of();
            var original = commands.suggest(args,source.getSender().hasPermission(ADMIN));
            if (!source.getSender().hasPermission(USE)) return original;
            return java.util.stream.Stream.concat(original.stream(),m8Commands.suggest(args).stream()).distinct().toList();
        }
    }
    private String statusText(Player player, boolean admin) {
        String status = m7StatusText(player,admin);
        if (registry == null) return status;
        int own = admin ? registry.activeCount() : player == null ? 0 : registry.countOwner(player.getUniqueId());
        return status + "; M8 registrations=" + own
            + (player == null ? "" : "; " + auras.status(player.getUniqueId()))
            + "; aura default=" + m8Config.defaultTier() + "; heavyAllowed=" + m8Config.heavyAllowed()
            + "; snapshot tick=" + anchorTiming.tick() + "; snapshotNanos=" + anchorTiming.snapshotNanos()
            + "; snapshotReads=" + anchorTiming.reads();
    }
    private String m7StatusText(Player player, boolean admin) {
        int count = admin ? driver.activeCount() : player != null && driver.ring(player.getUniqueId()) != null ? 1 : 0;
        return "VFX " + getPluginMeta().getVersion() + "; engine=0.1.0-m1 tree=" + provenance.getProperty("engineTree")
            + "; paper-api=" + provenance.getProperty("paperApiVersion") + "; server=" + Bukkit.getVersion()
            + "; rings=" + count + "; task=" + (driver.running() ? "running" : "idle")
            + "; effective policy=" + config.policy() + "; disabled styles="
            + particles.keySet().stream().filter(health::isDisabled).toList()
            + "; last tick=" + driver.lastReport() + "; /reload unsupported";
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        thread.check(); if (driver == null) return; driver.stop(event.getPlayer().getUniqueId());
        if (registry != null) registry.stopOwner(event.getPlayer().getUniqueId());
        ids.release(event.getPlayer().getUniqueId());
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
        if (registry != null) registry.stopOwner(event.getEntity().getUniqueId());
    }
    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true) public void unload(WorldUnloadEvent event) {
        thread.check(); if (driver == null) return; UUID world=event.getWorld().getUID(); driver.stopWorld(world); ids.forgetWorld(world);
    }
}
