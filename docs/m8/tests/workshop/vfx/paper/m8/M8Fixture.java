package workshop.vfx.paper.m8;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import workshop.vfx.delivery.DeliveryPlan;
import workshop.vfx.delivery.DeliveryPlanner;
import workshop.vfx.delivery.DeliveryRequest;
import workshop.vfx.delivery.QualityTier;
import workshop.vfx.paper.core.*;
import workshop.vfx.scene.StyleKey;

/** M8 R2/R3 proposed test support: the hooked reference driver with fake host, anchors, planner, sink and failure injection. */
final class M8Fixture {
    static final UUID A = new UUID(0, 1), B = new UUID(0, 2), WORLD = new UUID(0, 3), WORLD2 = new UUID(0, 4), C = new UUID(0, 5);
    static final Set<StyleKey> STYLES = Set.of(M8Recipes.AURA, M8Recipes.HALO, M8Recipes.TRAIL, M8Recipes.TRAIL_DIM,
            M8Recipes.FLASH, M8Recipes.ROD, M8Recipes.RED);
    static final TrailRules BALANCED_TRAIL = new TrailRules(24, 0.35, 8, 2, 30);

    final ThreadGuard thread = new ThreadGuard();
    final DefaultSessionIds ids = new DefaultSessionIds(thread);
    final Map<UUID, double[]> positions = new HashMap<>();      // x, y, z, bodyYaw
    final Map<UUID, Long> serials = new HashMap<>();
    final Map<UUID, UUID> worlds = new HashMap<>();
    final Set<UUID> offline = new java.util.HashSet<>();
    final Set<UUID> unloadedChunk = new java.util.HashSet<>();
    final List<UUID> players = new ArrayList<>(List.of(A, B));
    /** R3 F5 failure injection: the next host.schedule call throws. */
    boolean failNextSchedule;
    /** R3 F6 adversarial hook: rewrites the snapshot the Paper layer would return (null = honest adapter). */
    java.util.function.UnaryOperator<AnchorSnapshot> tamper;
    long tick = 100, nanos;
    int schedules, cancels, plans, scheduleFailures;
    Runnable callback;
    List<DeliveryRequest> lastRequests = List.of();
    DeliveryPlan lastPlan;
    final List<long[]> sends = new ArrayList<>();               // viewerId, worldId
    final DynamicEffectRegistry registry;
    final RingDriverWithHook driver;
    final AnchorRules rules;

    M8Fixture(Map<String, Object> settings, int maxPerPlayer) { this(settings, maxPerPlayer, null); }

    /** {@code contributor} replaces the registry as the driver's hook (R3 F6 driver-level adversarial tests); null = registry. */
    M8Fixture(Map<String, Object> settings, int maxPerPlayer, TickContributor contributor) {
        for (UUID p : List.of(A, B, C)) { positions.put(p, new double[] {0.5, 64, 0.5, 0}); worlds.put(p, WORLD); serials.put(p, 0L); }
        rules = new DefaultAnchorRules(ids);
        registry = new DynamicEffectRegistry(this::snapshot, ids, maxPerPlayer);
        RingDriverWithHook.Host host = new RingDriverWithHook.Host() {
            public long tick() { return tick; }
            public long nanoTime() { return nanos += 10; }
            public boolean worldReady(UUID world, double x, double y, double z) { return true; }
            public ViewerFacts facts(UUID p) {
                if (offline.contains(p) || !positions.containsKey(p)) return null;
                double[] q = positions.get(p);
                return new ViewerFacts(true, true, ids.worldId(worlds.get(p)), q[0], q[1], q[2], true, false);
            }
            public List<UUID> players() { return List.copyOf(players); }
            public Runnable schedule(Runnable body) {
                if (failNextSchedule) { failNextSchedule = false; scheduleFailures++; throw new IllegalStateException("injected: scheduler refused"); }
                callback = body; schedules++; return () -> cancels++;
            }
        };
        driver = new RingDriverWithHook(thread, host, DemoConfig.from(settings), ids, new SessionStyleHealth(STYLES, thread),
                (w, x, y, z) -> Optional.empty(), (viewer, world, style, x, y, z) -> sends.add(new long[] {viewer, world}),
                (t, requests, styles, policy) -> { plans++; lastRequests = requests; lastPlan = DeliveryPlanner.plan(t, requests, styles, policy); return lastPlan; },
                contributor == null ? registry : contributor);
        registry.bind(driver);
    }
    M8Fixture() { this(Map.of(), 2); }

    /** The honest Paper snapshot adapter (anchor spec 3.2), optionally rewritten by {@link #tamper}. */
    AnchorSnapshot snapshot(UUID owner, long t) {
        AnchorSnapshot s = honest(owner, t);
        return tamper == null ? s : tamper.apply(s);
    }
    AnchorSnapshot honest(UUID owner, long t) {
        if (offline.contains(owner)) return null;
        double[] q = positions.get(owner);
        UUID w = worlds.get(owner);
        return new AnchorSnapshot(t, owner, ids.viewerId(owner), w, ids.worldId(w), q[0], q[1], q[2], (float) q[3], (float) q[3], 0f,
                -64, 320, !unloadedChunk.contains(owner), true, true, false, serials.get(owner));
    }
    void ring(UUID owner) { driver.start(owner, WORLD, 0.5, 64.1, 0.5, QualityTier.BALANCED); }
    long aura(UUID owner) { return registry.start(owner, EffectKind.AURA, (id, b) -> new AuraEffect(id, b, tick, 1200, QualityTier.BALANCED, rules)); }
    long trail(UUID owner, int lifetime) {
        return registry.start(owner, EffectKind.TRAIL, (id, b) -> new TrailEffect(id, b, tick, lifetime, QualityTier.BALANCED, BALANCED_TRAIL, rules));
    }
    long trail(UUID owner) { return trail(owner, 1200); }
    long activation(UUID owner) {
        return registry.start(owner, EffectKind.ACTIVATION, (id, b) -> new AbstractEffectProbe(id, b, tick, rules));
    }
    void run() { callback.run(); }
    long sendsTo(UUID viewer) { long id = ids.viewerId(viewer); return sends.stream().filter(x -> x[0] == id).count(); }
    void step() { tick++; callback.run(); }

    /** Minimal activation-like effect: static recipe, 80-tick lifetime (enough for capacity and lifecycle tests). */
    static final class AbstractEffectProbe extends AbstractEffect {
        private final workshop.vfx.delivery.EffectRecipe recipe = M8Recipes.activation();
        AbstractEffectProbe(long id, AnchorBinding binding, long start, AnchorRules rules) { super(id, binding, start, 80, rules, 20); }
        @Override public EffectKind kind() { return EffectKind.ACTIVATION; }
        @Override protected Optional<EffectDraft> draft(long t, AnchorSnapshot s) {
            return Optional.of(new EffectDraft(EffectKind.ACTIVATION, requestId, owner, binding.ownerViewerId(), s.worldId(),
                    workshop.vfx.math.core.Pose.ofTranslation(new workshop.vfx.math.core.Vec3(0.5, 64, 0.5)), recipe, QualityTier.BALANCED,
                    workshop.vfx.math.time.Playback.start(workshop.vfx.math.time.TimePoint.gameTick(startTick)),
                    workshop.vfx.math.time.TimePoint.gameTick(t), age(t)));
        }
    }
}
