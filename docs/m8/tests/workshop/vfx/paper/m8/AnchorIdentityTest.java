package workshop.vfx.paper.m8;

import static org.junit.jupiter.api.Assertions.*;
import static workshop.vfx.paper.m8.M8Fixture.A;
import static workshop.vfx.paper.m8.M8Fixture.B;
import static workshop.vfx.paper.m8.M8Fixture.WORLD;
import static workshop.vfx.paper.m8.M8Fixture.WORLD2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.delivery.QualityTier;
import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.time.Playback;
import workshop.vfx.math.time.TimePoint;

/**
 * R3 F6: fail-closed anchor identity and freshness. B holds the use permission but not view, stands next to A in the
 * same world, so B may only ever see effects whose owner viewer id is B's. Every malformed snapshot or forged draft
 * below must leave B with zero particles from A's effect.
 */
class AnchorIdentityTest {
    private static AnchorSnapshot with(AnchorSnapshot s, UUID owner, long viewerId, long tick, UUID world, int worldId,
            double y, int minH, int maxH, boolean chunk) {
        return new AnchorSnapshot(tick, owner, viewerId, world, worldId, s.x(), y, s.z(), s.bodyYawDegrees(), s.headYawDegrees(),
                s.pitchDegrees(), minH, maxH, chunk, s.online(), s.valid(), s.dead(), s.discontinuitySerial());
    }
    private static AnchorSnapshot owner(AnchorSnapshot s, UUID o, long id) {
        return with(s, o, id, s.tick(), s.world(), s.worldId(), s.y(), s.minHeight(), s.maxHeight(), s.chunkLoaded());
    }
    private static AnchorSnapshot tick(AnchorSnapshot s, long t) {
        return with(s, s.owner(), s.ownerViewerId(), t, s.world(), s.worldId(), s.y(), s.minHeight(), s.maxHeight(), s.chunkLoaded());
    }
    private static AnchorSnapshot world(AnchorSnapshot s, UUID w, int id) {
        return with(s, s.owner(), s.ownerViewerId(), s.tick(), w, id, s.y(), s.minHeight(), s.maxHeight(), s.chunkLoaded());
    }
    private static AnchorSnapshot height(AnchorSnapshot s, double y, int minH, int maxH) {
        return with(s, s.owner(), s.ownerViewerId(), s.tick(), s.world(), s.worldId(), y, minH, maxH, s.chunkLoaded());
    }

    private AbstractEffect aura;

    /** A with an aura, one honest tick (owner-only delivery), then the tampered snapshot (built from the fixture) for one tick. */
    private M8Fixture honestThen(java.util.function.Function<M8Fixture, UnaryOperator<AnchorSnapshot>> tamper) {
        M8Fixture f = new M8Fixture();
        f.ids.viewerId(A); f.ids.viewerId(B);
        f.aura(A);
        aura = (AbstractEffect) f.registry.effect(A, EffectKind.AURA).orElseThrow();
        f.run();
        assertEquals(27, f.sendsTo(A), "baseline: owner sees the balanced aura");
        assertEquals(0, f.sendsTo(B), "baseline: B (use, no view) sees nothing of A's aura");
        f.tamper = tamper.apply(f);
        f.step();
        return f;
    }

    @Test
    @DisplayName("Snapshot naming another owner (B's UUID and B's viewer id) terminates A's aura; B receives nothing")
    void ownerSwap() {
        M8Fixture f = honestThen(fx -> s -> owner(s, B, fx.ids.viewerId(B)));
        assertEquals(0, f.sendsTo(B));
        assertEquals(27, f.sendsTo(A), "no delivery in the tampered tick");
        assertEquals(0, f.registry.activeCount(), "terminated and disposed");
        assertEquals(1, aura.rejections(AnchorRejection.OWNER_MISMATCH));
        assertTrue(aura.disposed());
    }

    @Test
    @DisplayName("Each rejection reason is recorded, and the right state is chosen (identity: TERMINATE; freshness/world: SKIP_TICK)")
    void reasons() {
        M8Fixture f = new M8Fixture();
        long a = f.ids.viewerId(A), b = f.ids.viewerId(B);
        int w = f.ids.worldId(WORLD), w2 = f.ids.worldId(WORLD2);
        AnchorBinding bound = new AnchorBinding(A, a);
        AnchorSnapshot ok = f.honest(A, f.tick);
        record Case(AnchorSnapshot s, AnchorState state, AnchorRejection reason) { }
        List<Case> cases = List.of(
                new Case(ok, AnchorState.READY, AnchorRejection.NONE),
                new Case(null, AnchorState.TERMINATE, AnchorRejection.OWNER_UNRESOLVED),
                new Case(owner(ok, B, b), AnchorState.TERMINATE, AnchorRejection.OWNER_MISMATCH),
                new Case(owner(ok, B, a), AnchorState.TERMINATE, AnchorRejection.OWNER_MISMATCH),
                new Case(owner(ok, A, b), AnchorState.TERMINATE, AnchorRejection.VIEWER_ID_MISMATCH),
                new Case(owner(ok, A, 999), AnchorState.TERMINATE, AnchorRejection.VIEWER_ID_MISMATCH),
                new Case(tick(ok, f.tick - 1), AnchorState.SKIP_TICK, AnchorRejection.STALE_OR_FUTURE),
                new Case(tick(ok, f.tick + 1), AnchorState.SKIP_TICK, AnchorRejection.STALE_OR_FUTURE),
                new Case(tick(ok, Long.MIN_VALUE), AnchorState.SKIP_TICK, AnchorRejection.STALE_OR_FUTURE),
                new Case(world(ok, WORLD2, w), AnchorState.SKIP_TICK, AnchorRejection.WORLD_INCONSISTENT),
                new Case(world(ok, WORLD, w2), AnchorState.SKIP_TICK, AnchorRejection.WORLD_INCONSISTENT),
                new Case(world(ok, WORLD, 99), AnchorState.SKIP_TICK, AnchorRejection.WORLD_INCONSISTENT),
                new Case(world(ok, WORLD, -1), AnchorState.SKIP_TICK, AnchorRejection.WORLD_INCONSISTENT),
                new Case(world(ok, null, w), AnchorState.SKIP_TICK, AnchorRejection.WORLD_INCONSISTENT),
                new Case(height(ok, Double.NaN, -64, 320), AnchorState.SKIP_TICK, AnchorRejection.NON_FINITE),
                new Case(height(ok, -64.0001, -64, 320), AnchorState.SKIP_TICK, AnchorRejection.OUTSIDE_WORLD_HEIGHT),
                new Case(height(ok, 320, -64, 320), AnchorState.SKIP_TICK, AnchorRejection.OUTSIDE_WORLD_HEIGHT),
                new Case(height(ok, 64, 320, 320), AnchorState.SKIP_TICK, AnchorRejection.OUTSIDE_WORLD_HEIGHT),
                new Case(height(ok, -64, -64, 320), AnchorState.READY, AnchorRejection.NONE),
                new Case(with(ok, A, a, f.tick, WORLD, w, 64, -64, 320, false), AnchorState.SKIP_TICK, AnchorRejection.CHUNK_NOT_LOADED));
        for (Case c : cases) {
            AnchorVerdict v = f.rules.validate(c.s(), bound, f.tick);
            assertEquals(c.state(), v.state(), String.valueOf(c.s()));
            assertEquals(c.reason(), v.reason(), String.valueOf(c.s()));
        }
    }

    @Test
    @DisplayName("Snapshot with A's UUID but B's viewer id (owner-viewer redirection) terminates the aura; B receives nothing")
    void viewerIdSwap() {
        M8Fixture f = honestThen(fx -> s -> owner(s, A, fx.ids.viewerId(B)));
        assertEquals(0, f.sendsTo(B));
        assertEquals(0, f.registry.activeCount());
        assertEquals(1, aura.rejections(AnchorRejection.VIEWER_ID_MISMATCH));
    }

    @Test
    @DisplayName("Stale and future snapshots contribute nothing that tick and keep the effect; 20 in a row terminate it")
    void staleAndFuture() {
        for (long offset : new long[] {-1, +1, -100}) {
            M8Fixture f = honestThen(fx -> s -> tick(s, s.tick() + offset));
            assertEquals(1, f.plans, "no request in the stale tick, so no planner call");
            assertEquals(1, f.registry.activeCount());
            assertEquals(1, aura.rejections(AnchorRejection.STALE_OR_FUTURE));
            f.tamper = null;
            f.step();
            assertEquals(2 * 27, f.sendsTo(A), "fresh snapshot draws again");
            assertEquals(0, f.sendsTo(B));
            f.tamper = s -> tick(s, s.tick() + offset);
            for (int i = 0; i < 20; i++) f.step();
            assertEquals(0, f.registry.activeCount(), "20 consecutive stale snapshots terminate");
        }
    }

    @Test
    @DisplayName("Inconsistent world identity, out-of-height feet and unloaded chunks skip the tick; no request reaches the planner")
    void worldHeightChunk() {
        Map<AnchorRejection, UnaryOperator<AnchorSnapshot>> tampers = new java.util.LinkedHashMap<>();
        tampers.put(AnchorRejection.WORLD_INCONSISTENT, s -> world(s, WORLD2, s.worldId()));
        tampers.put(AnchorRejection.OUTSIDE_WORLD_HEIGHT, s -> height(s, 400, -64, 320));
        tampers.put(AnchorRejection.CHUNK_NOT_LOADED,
                s -> with(s, s.owner(), s.ownerViewerId(), s.tick(), s.world(), s.worldId(), s.y(), s.minHeight(), s.maxHeight(), false));
        for (Map.Entry<AnchorRejection, UnaryOperator<AnchorSnapshot>> t : tampers.entrySet()) {
            M8Fixture f = honestThen(fx -> t.getValue());
            assertEquals(1, f.plans, t.getKey() + ": no request reaches the planner");
            assertEquals(0, f.sendsTo(B));
            assertEquals(1, f.registry.activeCount(), t.getKey() + ": skipped, not terminated");
            assertEquals(1, aura.rejections(t.getKey()));
        }
        M8Fixture f = honestThen(fx -> null);
        f.unloadedChunk.add(A);
        f.step();
        assertEquals(1, aura.rejections(AnchorRejection.CHUNK_NOT_LOADED), "the honest adapter reports the unloaded chunk");
        assertEquals(2, f.plans, "the honest ticks planned, the unloaded-chunk tick did not");
    }

    @Test
    @DisplayName("A session id released on quit and reassigned on rejoin no longer matches the binding: the effect terminates")
    void releasedViewerId() {
        M8Fixture f = new M8Fixture();
        f.aura(A);
        f.run();
        f.ids.release(A);
        f.step();
        assertEquals(0, f.registry.activeCount());
    }

    @Test
    @DisplayName("A cached snapshot that still carries the released (bound) viewer id after quit is rejected: the session no longer maps it")
    void releasedIdReplayed() {
        M8Fixture f = new M8Fixture();
        f.aura(A);
        long bound = f.ids.viewerId(A);
        AbstractEffect e = (AbstractEffect) f.registry.effect(A, EffectKind.AURA).orElseThrow();
        f.run();
        f.ids.release(A);
        f.tamper = s -> owner(s, A, bound);          // adapter replays the old id instead of asking SessionIds
        f.step();
        assertEquals(1, e.rejections(AnchorRejection.VIEWER_ID_MISMATCH));
        assertEquals(0, f.registry.activeCount());
    }

    @Test
    @DisplayName("Drafts carry the bound owner viewer id even if a (defective) rule accepted a snapshot with another id")
    void draftsUseBindingNotSnapshot() {
        M8Fixture f = new M8Fixture();
        AnchorRules lax = new AnchorRules() {          // simulates a validation defect: everything READY
            public AnchorVerdict validate(AnchorSnapshot s, AnchorBinding expected, long tick) { return AnchorVerdict.READY; }
            public Pose pose(AnchorSnapshot s, OrientationMode m, double lift) { return f.rules.pose(s, m, lift); }
            public Vec3 toWorld(AnchorSnapshot s, OrientationMode m, double lift, Vec3 local) { return f.rules.toWorld(s, m, lift, local); }
        };
        f.ids.viewerId(A); f.ids.viewerId(B);
        f.registry.start(A, EffectKind.AURA, (id, b) -> new AuraEffect(id, b, f.tick, 1200, QualityTier.BALANCED, lax));
        f.tamper = s -> owner(s, A, f.ids.viewerId(B));
        f.run();
        assertEquals(0, f.registry.identityViolations(), "the draft was built from the binding");
        assertEquals(27, f.sendsTo(A));
        assertEquals(0, f.sendsTo(B));
    }

    /** An effect whose draft forges B as owner (B's UUID and B's viewer id) although it was started for A. */
    static final class Forger extends AbstractEffect {
        final long forgedViewer; final UUID forgedOwner;
        Forger(long id, AnchorBinding b, long start, AnchorRules rules, UUID forgedOwner, long forgedViewer) {
            super(id, b, start, 1200, rules, 20); this.forgedOwner = forgedOwner; this.forgedViewer = forgedViewer;
        }
        @Override public EffectKind kind() { return EffectKind.AURA; }
        @Override protected Optional<EffectDraft> draft(long t, AnchorSnapshot s) {
            return Optional.of(new EffectDraft(EffectKind.AURA, requestId, forgedOwner, forgedViewer, s.worldId(),
                    Pose.ofTranslation(new Vec3(s.x(), s.y(), s.z())), M8Recipes.aura(1200), QualityTier.BALANCED,
                    Playback.start(TimePoint.gameTick(startTick)), TimePoint.gameTick(t), age(t)));
        }
    }

    @Test
    @DisplayName("Registry: a draft whose owner or owner viewer id differs from the binding is dropped and the effect stopped")
    void registryDropsForgedDrafts() {
        // forged owner UUID, forged owner viewer id (as the session id of A or B)
        Object[][] forgeries = {{B, B}, {A, B}, {B, A}};
        for (Object[] forged : forgeries) {
            M8Fixture f = new M8Fixture();
            long viewer = f.ids.viewerId((UUID) forged[1]);
            f.ids.viewerId(A); f.ids.viewerId(B);
            f.registry.start(A, EffectKind.AURA, (id, b) -> new Forger(id, b, f.tick, f.rules, (UUID) forged[0], viewer));
            f.run();
            assertEquals(0, f.plans, "forged draft never reaches the planner");
            assertEquals(0, f.sendsTo(B));
            assertEquals(1, f.registry.identityViolations());
            assertEquals(0, f.registry.activeCount());
        }
    }

    @Test
    @DisplayName("Driver (defence in depth): drafts with a mismatched owner/viewer id, a ring-range id or an unknown world are dropped")
    void driverDropsForgedDrafts() {
        List<EffectDraft> next = new ArrayList<>();
        TickContributor rogue = new TickContributor() {
            public List<EffectDraft> drafts(long tick) { return List.copyOf(next); }
            public boolean active() { return true; }
            public int activeCount() { return 1; }
            public void close() { }
        };
        M8Fixture f = new M8Fixture(Map.of(), 2, rogue);
        long a = f.ids.viewerId(A), b = f.ids.viewerId(B);
        int w = f.ids.worldId(WORLD);
        long base = DynamicEffectRegistry.FIRST_ID;
        java.util.function.Function<Object[], EffectDraft> d = p -> new EffectDraft(EffectKind.AURA, (Long) p[0], (UUID) p[1],
                (Long) p[2], (Integer) p[3], Pose.ofTranslation((Vec3) p[4]), M8Recipes.aura(1200), QualityTier.BALANCED,
                Playback.start(TimePoint.gameTick(f.tick)), TimePoint.gameTick(f.tick), 0);
        Vec3 at = new Vec3(0.5, 64, 0.5);
        next.add(d.apply(new Object[] {base, A, b, w, at}));                                  // A's UUID, B's viewer id
        next.add(d.apply(new Object[] {base + 1, B, a, w, at}));                              // B's UUID, A's viewer id
        next.add(d.apply(new Object[] {0L, A, a, w, at}));                                    // ring-range request id
        next.add(d.apply(new Object[] {base + 2, A, a, 99, at}));                             // unknown world id
        next.add(d.apply(new Object[] {base + 4, A, a, w, at}));                              // control: valid
        f.driver.ensureRunning();
        f.run();
        // A non-finite anchor cannot even be expressed: the engine's Pose constructor rejects it (checked here too).
        assertThrows(IllegalArgumentException.class, () -> Pose.ofTranslation(new Vec3(Double.NaN, 64, 0.5)));
        assertEquals(4, f.driver.rejectedDrafts());
        assertEquals(List.of(base + 4), f.lastRequests.stream().map(r -> r.id()).toList());
        assertEquals(27, f.sendsTo(A));
        assertEquals(0, f.sendsTo(B), "B never treated as the owner viewer");
    }
}
