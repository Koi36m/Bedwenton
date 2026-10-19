package workshop.vfx.paper.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import workshop.vfx.delivery.*;
import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.time.Playback;
import workshop.vfx.math.time.TimePoint;
import workshop.vfx.paper.m8.EffectDraft;
import workshop.vfx.paper.m8.EffectScheduler;
import workshop.vfx.paper.m8.TickContributor;

/**
 * M8 R3 THROWAWAY REFERENCE (design verification only; never production). A copy of the accepted M7 RingDriver with
 * exactly the changes proposed in M8-ARCHITECTURE-AND-CONTRACT section 4: an optional TickContributor, one shared global
 * capacity rule, M8-only scheduling, the corrected tick order and zero-request handling; R3 adds the transactional ring
 * start (F5) and the draft identity check (F6). `diff` against
 * paper-adapter/.../RingDriver.java shows the complete proposed production delta (validation/ringdriver-proposed.diff).
 */
public final class RingDriverWithHook implements EffectScheduler {
    public interface Host {
        long tick();
        long nanoTime();
        boolean worldReady(UUID world, double x, double y, double z);
        ViewerFacts facts(UUID player);
        List<UUID> players();
        Runnable schedule(Runnable tickBody);
    }
    @FunctionalInterface public interface Planner {
        DeliveryPlan plan(long tick, List<DeliveryRequest> requests, Set<workshop.vfx.scene.StyleKey> styles, DeliveryPolicy policy);
    }
    public record Ring(long id, UUID owner, long ownerId, UUID world, int worldId,
                       double x, double y, double z, long startTick, QualityTier tier) { }
    private final ThreadGuard thread;
    private final Host host;
    private final DemoConfig config;
    private final DefaultSessionIds ids;
    private final StyleHealth health;
    private final RecipientRule rule;
    private final PlanEmitter emitter;
    private final ParticleSink sink;
    private final PointValidator points;
    private final Planner planner;
    private final EffectRecipe recipe;
    private final Map<UUID, Ring> rings = new HashMap<>();
    private Runnable cancellation;
    private boolean running;
    private boolean closed;
    private long generation;
    private long nextRing;
    private long lastPlannedTick = Long.MIN_VALUE;
    private int rejectedDrafts;
    /** M8 request ids start here (DynamicEffectRegistry.FIRST_ID); ring ids count up from 0 (2^62 ring starts are unreachable). */
    static final long M8_FIRST_REQUEST_ID = 1L << 62;
    private EmissionReport lastReport;
    private final TickContributor contributor;

    public RingDriverWithHook(ThreadGuard thread, Host host, DemoConfig config, DefaultSessionIds ids, StyleHealth health,
                      PointValidator points, ParticleSink sink) {
        this(thread, host, config, ids, health, points, sink, DeliveryPlanner::plan);
    }
    public RingDriverWithHook(ThreadGuard thread, Host host, DemoConfig config, DefaultSessionIds ids, StyleHealth health,
                      PointValidator points, ParticleSink sink, Planner planner) {
        this(thread, host, config, ids, health, points, sink, planner, TickContributor.NONE);
    }
    public RingDriverWithHook(ThreadGuard thread, Host host, DemoConfig config, DefaultSessionIds ids, StyleHealth health,
                      PointValidator points, ParticleSink sink, Planner planner, TickContributor contributor) {
        this.contributor = Objects.requireNonNull(contributor);
        this.thread = Objects.requireNonNull(thread); this.host = Objects.requireNonNull(host);
        this.config = Objects.requireNonNull(config); this.ids = Objects.requireNonNull(ids);
        this.health = Objects.requireNonNull(health); this.points = Objects.requireNonNull(points);
        this.sink = Objects.requireNonNull(sink); this.planner = Objects.requireNonNull(planner);
        // Config has already enforced a finite radius in [4,64].
        rule = new OwnerAwareRecipientRule(config.viewRadius()); emitter = new DefaultPlanEmitter(thread);
        recipe = DemoRecipe.create(config.ringLifetimeTicks());
    }
    /** Position is captured once by the caller at the approved block-centre anchor. */
    public void start(UUID owner, UUID world, double x, double y, double z, QualityTier tier) {
        thread.check();
        if (closed) throw new IllegalStateException("VFX session is disabled");
        Objects.requireNonNull(owner); Objects.requireNonNull(world); Objects.requireNonNull(tier);
        if (!OwnerAwareRecipientRule.finite(x,y,z) || !host.worldReady(world,x,y,z))
            throw new IllegalArgumentException("Anchor is not in a loaded, valid world location");
        if (!hasCapacityFor(rings.containsKey(owner)))
            throw new IllegalArgumentException("Active ring cap reached");
        if (nextRing == Long.MAX_VALUE) throw new IllegalStateException("Ring session ids exhausted");
        Ring ring = new Ring(nextRing++, owner, ids.viewerId(owner), world, ids.worldId(world), x,y,z,host.tick(),tier);
        ensureRunning(); // R3 F5: before the commit, so a failure never removes or replaces an existing ring
        rings.put(owner,ring);
    }
    /** F1: one capacity rule for rings and M8 effects, whatever the order in which they were started. */
    @Override public boolean hasCapacityFor(boolean replacing) {
        thread.check();
        return replacing || rings.size() + contributor.activeCount() < config.maxActiveEffects();
    }
    /** F2: the one shared task, started by the first ring or the first M8 effect. */
    @Override public void ensureRunning() {
        thread.check();
        if (closed) throw new IllegalStateException("VFX session is disabled");
        if (running) return;
        running = true; long token = ++generation;
        try { cancellation = Objects.requireNonNull(host.schedule(() -> { thread.check(); if (running && token == generation) tick(); })); }
        catch (RuntimeException e) { running = false; generation++; throw e; }
    }
    @Override public void effectsChanged() { thread.check(); cancelIfEmpty(); }
    public void stop(UUID owner) { thread.check(); rings.remove(owner); cancelIfEmpty(); }
    public void stopAll() { thread.check(); rings.clear(); cancelIfEmpty(); }
    public void stopWorld(UUID world) { thread.check(); rings.values().removeIf(r -> r.world().equals(world)); cancelIfEmpty(); }
    public void close() { thread.check(); closed = true; contributor.close(); stopAll(); }
    private void cancelIfEmpty() {
        if (!rings.isEmpty() || contributor.active()) return;
        running = false; generation++;
        Runnable cancel = cancellation; cancellation = null;
        if (cancel != null) cancel.run();
    }
    /**
     * R3 F6: a draft may only name an M8 request id, an owner whose bound session viewer id maps back to that owner, and a
     * known world id with a finite anchor; otherwise no request and no context are built, so owner-aware visibility cannot
     * be redirected to another player.
     */
    private boolean acceptDraft(EffectDraft d) {
        if (d == null || d.owner() == null || d.anchor() == null || d.requestId() < M8_FIRST_REQUEST_ID) return false;
        if (!d.owner().equals(ids.viewer(d.ownerViewerId())) || d.worldId() < 0 || ids.world(d.worldId()) == null) return false;
        Vec3 a = d.anchor().translation();
        return OwnerAwareRecipientRule.finite(a.x(),a.y(),a.z());
    }
    public int rejectedDrafts() { thread.check(); return rejectedDrafts; }
    public Ring ring(UUID owner) { thread.check(); return rings.get(owner); }
    public int activeCount() { thread.check(); return rings.size(); }
    public boolean running() { thread.check(); return running; }
    public EmissionReport lastReport() { thread.check(); return lastReport; }
    public boolean leavesAnchor(UUID owner, UUID world, double x, double y, double z) {
        thread.check(); Ring r = rings.get(owner); if (r == null) return false;
        if (!r.world().equals(world) || !OwnerAwareRecipientRule.finite(x,y,z)) return true;
        double dx=x-r.x(), dy=y-r.y(), dz=z-r.z();
        return !(dx*dx+dy*dy+dz*dz <= config.viewRadius()*config.viewRadius());
    }
    public void tick() {
        thread.check(); if (!running || closed) return;
        long tick = host.tick();
        if (tick == lastPlannedTick) return; // Even stale callbacks cannot reset this tick's budgets.
        rings.values().removeIf(r -> {
            ViewerFacts owner = host.facts(r.owner());
            return owner == null || !owner.online() || owner.worldId() != r.worldId()
                || tick-r.startTick() >= config.ringLifetimeTicks() || tick < r.startTick()
                || !host.worldReady(r.world(),r.x(),r.y(),r.z());
        });
        // F2: M8 effects contribute in the same tick, after ring pruning, also when no ring is active.
        List<EffectDraft> drafts = contributor.drafts(tick);
        if (rings.isEmpty() && !contributor.active()) { cancelIfEmpty(); return; }
        List<DeliveryRequest> requests = new ArrayList<>(); Map<Long,RequestContext> contexts = new HashMap<>();
        List<Ring> ordered = rings.values().stream().sorted(Comparator.comparingLong(Ring::id)).toList();
        // Assign new identities once in deterministic UUID order; sort and deduplicate again by session id below.
        List<UUID> players = host.players().stream().distinct().sorted().toList();
        for (UUID player : players) ids.viewerId(player);
        for (Ring r : ordered) {
            RequestContext context = new RequestContext(r.id(),r.ownerId(),r.worldId(),r.x(),r.y(),r.z());
            boolean ownerEligible = rule.decide(context,r.ownerId(),host.facts(r.owner())).isEmpty();
            TreeSet<Long> others = new TreeSet<>();
            for (UUID player : players) {
                long id = ids.viewerId(player);
                if (id != r.ownerId() && rule.decide(context,id,host.facts(player)).isEmpty()) others.add(id);
            }
            long[] selected = new OwnerFirstViewerSelection().select(r.ownerId(),ownerEligible,
                others.stream().mapToLong(Long::longValue).toArray(),config.maxViewersPerEffect());
            requests.add(DeliveryRequest.of(r.id(),recipe,r.tier(),r.worldId(),Pose.ofTranslation(new Vec3(r.x(),r.y(),r.z())),
                Playback.start(TimePoint.gameTick(r.startTick())),TimePoint.gameTick(tick),tick-r.startTick(),selected));
            contexts.put(r.id(),context);
        }
        for (EffectDraft d : drafts) {
            if (!acceptDraft(d)) { rejectedDrafts++; continue; } // R3 F6: defence in depth below the registry
            Vec3 a = d.anchor().translation();
            RequestContext context = new RequestContext(d.requestId(),d.ownerViewerId(),d.worldId(),a.x(),a.y(),a.z());
            boolean ownerEligible = rule.decide(context,d.ownerViewerId(),host.facts(d.owner())).isEmpty();
            TreeSet<Long> others = new TreeSet<>();
            for (UUID player : players) {
                long id = ids.viewerId(player);
                if (id != d.ownerViewerId() && rule.decide(context,id,host.facts(player)).isEmpty()) others.add(id);
            }
            long[] selected = new OwnerFirstViewerSelection().select(d.ownerViewerId(),ownerEligible,
                others.stream().mapToLong(Long::longValue).toArray(),config.maxViewersPerEffect());
            requests.add(DeliveryRequest.of(d.requestId(),d.recipe(),d.tier(),d.worldId(),d.anchor(),d.playback(),d.now(),
                d.frameOrdinal(),selected));
            contexts.put(d.requestId(),context);
        }
        lastPlannedTick = tick;
        // Zero requests (for example only a stationary trail): no planner call; the task stays alive while effects exist.
        if (requests.isEmpty()) {
            lastReport = new EmissionReport(tick,0,0,0,0,Map.of(),Map.of(),Map.of(),0,0,Set.of(),0,0);
            return;
        }
        long start = host.nanoTime();
        DeliveryPlan plan = planner.plan(tick,List.copyOf(requests),health.enabledStyles(),config.policy());
        long planned = host.nanoTime()-start; start = host.nanoTime();
        RecipientGate gate = (context,viewer) -> rule.decide(context,viewer,host.facts(ids.viewer(viewer)));
        EmissionReport report = emitter.emit(plan,contexts,health,gate,points,sink,config.emissionLimits());
        lastReport = report.withTimings(planned,host.nanoTime()-start);
    }
}
