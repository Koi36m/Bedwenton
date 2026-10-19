package workshop.vfx.paper.m8;

import static org.junit.jupiter.api.Assertions.*;
import static workshop.vfx.paper.m8.K0Fixture.A;
import static workshop.vfx.paper.m8.K0Fixture.B;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.delivery.DeliveryRequest;
import workshop.vfx.delivery.QualityTier;

/** R3 F5: DynamicEffectRegistry.start is transactional; injected failures never destroy a valid effect or leak a task. */
class K0TransactionalStartTest {
    /** Wraps the shared driver; {@code failEnsure} makes the next ensureRunning throw (injected scheduler failure). */
    static final class FailingScheduler implements EffectScheduler {
        final EffectScheduler delegate; boolean failEnsure; int ensureCalls;
        FailingScheduler(EffectScheduler delegate) { this.delegate = delegate; }
        public boolean hasCapacityFor(boolean replacing) { return delegate.hasCapacityFor(replacing); }
        public void ensureRunning() {
            ensureCalls++;
            if (failEnsure) { failEnsure = false; throw new IllegalStateException("injected: ensureRunning failed"); }
            delegate.ensureRunning();
        }
        public void effectsChanged() { delegate.effectsChanged(); }
    }
    /** Records every candidate the factory builds so the test can check which ones were disposed. */
    static final class Recording implements EffectFactory {
        final K0Fixture f; final List<K0Fixture.FakeEffect> built = new ArrayList<>();
        Recording(K0Fixture f) { this.f = f; }
        public DynamicEffect create(long id, AnchorBinding b) {
            K0Fixture.FakeEffect e = new K0Fixture.FakeEffect(id, b, f.tick, 1200, EffectKind.AURA); built.add(e); return e;
        }
    }
    /** An effect whose dispose throws (contract violation), to show the registry contains it after the commit point. */
    static final class DisposeBomb extends K0Fixture.FakeEffect {
        DisposeBomb(long id, AnchorBinding b, long start) { super(id, b, start, 1200, EffectKind.AURA); }
        @Override public EffectKind kind() { return EffectKind.AURA; }
        @Override protected Optional<EffectDraft> draft(long tick, AnchorSnapshot s) { return Optional.empty(); }
        @Override protected void onDispose() { throw new IllegalStateException("injected: dispose failed"); }
    }
    private static List<Long> plannedIds(K0Fixture f) { return f.lastRequests.stream().map(DeliveryRequest::id).toList(); }

    @Test
    @DisplayName("First registration, scheduler refuses: exception propagates, nothing registered, candidate disposed, no task; a retry works with a fresh id")
    void firstRegistrationScheduleFailure() {
        K0Fixture f = new K0Fixture();
        Recording factory = new Recording(f);
        f.failNextSchedule = true;
        assertThrows(IllegalStateException.class, () -> f.registry.start(A, EffectKind.AURA, factory));
        assertEquals(0, f.registry.activeCount());
        assertTrue(f.registry.effect(A, EffectKind.AURA).isEmpty());
        assertTrue(factory.built.getFirst().disposed(), "candidate disposed");
        assertFalse(f.driver.running(), "no task owned after the failure");
        assertEquals(1, f.scheduleFailures);
        assertEquals(0, f.schedules);
        assertEquals(0, f.cancels);
        assertEquals(1, f.registry.failedStarts());
        long failedId = factory.built.getFirst().requestId();
        long id = f.registry.start(A, EffectKind.AURA, factory);
        assertTrue(id > failedId, "ids are never reused");
        assertEquals(1, f.schedules);
        assertTrue(f.driver.running());
        f.run();
        assertEquals(List.of(id), plannedIds(f));
    }

    @Test
    @DisplayName("Replacement, ensureRunning throws: the existing effect stays registered, undisposed and planned; only the candidate is disposed")
    void replacementEnsureRunningFailure() {
        K0Fixture f = new K0Fixture();
        FailingScheduler scheduler = new FailingScheduler(f.driver);
        f.registry.bind(scheduler);
        Recording factory = new Recording(f);
        long oldId = f.registry.start(A, EffectKind.AURA, factory);
        K0Fixture.FakeEffect old = factory.built.getFirst();
        f.run();
        assertEquals(List.of(oldId), plannedIds(f));
        scheduler.failEnsure = true;
        assertThrows(IllegalStateException.class, () -> f.registry.start(A, EffectKind.AURA, factory));
        K0Fixture.FakeEffect candidate = factory.built.get(1);
        assertSame(old, f.registry.effect(A, EffectKind.AURA).orElseThrow(), "old effect still registered");
        assertFalse(old.disposed(), "old effect not disposed");
        assertTrue(candidate.disposed(), "candidate disposed");
        assertEquals(1, f.registry.activeCount());
        assertTrue(f.driver.running(), "shared task untouched");
        assertEquals(1, f.schedules);
        assertEquals(0, f.cancels);
        f.step();
        assertEquals(List.of(oldId), plannedIds(f), "the old effect keeps contributing");
        assertEquals(2 * 27, f.sendsTo(A), "old test effect delivers 27 balanced points on each of the two ticks");
    }

    @Test
    @DisplayName("Factory throws: on first registration nothing is registered and no task starts; on replacement the old effect is untouched")
    void factoryFailure() {
        K0Fixture f = new K0Fixture();
        EffectFactory broken = (id, b) -> { throw new IllegalStateException("injected: factory failed"); };
        assertThrows(IllegalStateException.class, () -> f.registry.start(A, EffectKind.AURA, broken));
        assertEquals(0, f.registry.activeCount());
        assertEquals(0, f.schedules);
        assertFalse(f.driver.running());
        long oldId = f.aura(A);
        DynamicEffect old = f.registry.effect(A, EffectKind.AURA).orElseThrow();
        assertThrows(IllegalStateException.class, () -> f.registry.start(A, EffectKind.AURA, broken));
        assertSame(old, f.registry.effect(A, EffectKind.AURA).orElseThrow());
        assertFalse(((K0Fixture.FakeEffect) old).disposed());
        assertEquals(oldId, old.requestId());
        assertEquals(2, f.registry.failedStarts());
    }

    @Test
    @DisplayName("A candidate with the wrong id, owner or kind, or null, is rejected before the commit; the old effect is untouched")
    void mismatchedCandidate() {
        K0Fixture f = new K0Fixture();
        f.aura(A);
        DynamicEffect old = f.registry.effect(A, EffectKind.AURA).orElseThrow();
        List<K0Fixture.FakeEffect> made = new ArrayList<>();
        List<EffectFactory> bad = List.of(
                (id, b) -> { K0Fixture.FakeEffect e = new K0Fixture.FakeEffect(id + 1, b, f.tick, 1200, EffectKind.AURA); made.add(e); return e; },
                (id, b) -> { K0Fixture.FakeEffect e = new K0Fixture.FakeEffect(id, new AnchorBinding(B, f.ids.viewerId(B)), f.tick, 1200, EffectKind.AURA); made.add(e); return e; },
                (id, b) -> { K0Fixture.FakeEffect e = new K0Fixture.FakeEffect(id, b, f.tick, 1200, EffectKind.TRAIL); made.add(e); return e; },
                (id, b) -> null);
        for (EffectFactory factory : bad) {
            assertThrows(IllegalStateException.class, () -> f.registry.start(A, EffectKind.AURA, factory));
            assertSame(old, f.registry.effect(A, EffectKind.AURA).orElseThrow());
        }
        assertEquals(3, made.size());
        made.forEach(e -> assertTrue(e.disposed(), "rejected candidate disposed"));
        assertFalse(((K0Fixture.FakeEffect) old).disposed());
        assertEquals(1, f.registry.activeCount());
        assertTrue(f.registry.effect(B, EffectKind.AURA).isEmpty());
    }

    @Test
    @DisplayName("Successful replacement: committed, old disposed exactly once after the commit, count and task unchanged")
    void successfulReplacement() {
        K0Fixture f = new K0Fixture();
        Recording factory = new Recording(f);
        long oldId = f.registry.start(A, EffectKind.AURA, factory);
        f.run();
        long newId = f.registry.start(A, EffectKind.AURA, factory);
        assertTrue(factory.built.getFirst().disposed());
        assertFalse(factory.built.get(1).disposed());
        assertEquals(1, f.registry.activeCount());
        assertEquals(1, f.schedules);
        assertEquals(0, f.cancels);
        f.step();
        assertEquals(List.of(newId), plannedIds(f));
        assertNotEquals(oldId, newId);
    }

    @Test
    @DisplayName("A throwing dispose of the replaced effect is contained after the commit: the new effect is registered and counted")
    void throwingDisposeAfterCommit() {
        K0Fixture f = new K0Fixture();
        f.registry.start(A, EffectKind.AURA, (id, b) -> new DisposeBomb(id, b, f.tick));
        long id = f.aura(A);
        assertEquals(id, f.registry.effect(A, EffectKind.AURA).orElseThrow().requestId());
        assertEquals(1, f.registry.disposeFailures());
        assertEquals(1, f.registry.activeCount());
    }

    @Test
    @DisplayName("Refused admission (per-player or global cap) never calls the factory or the scheduler")
    void refusedAdmissionHasNoSideEffects() {
        K0Fixture f = new K0Fixture(Map.of("maxActiveEffects", 2), 2);
        FailingScheduler scheduler = new FailingScheduler(f.driver);
        f.registry.bind(scheduler);
        f.aura(A); f.trail(A);
        int[] calls = {0};
        EffectFactory counting = (id, b) -> { calls[0]++; return null; };
        assertThrows(IllegalArgumentException.class, () -> f.registry.start(A, EffectKind.ACTIVATION, counting));
        assertThrows(IllegalArgumentException.class, () -> f.registry.start(B, EffectKind.AURA, counting));
        assertEquals(0, calls[0]);
        assertEquals(2, scheduler.ensureCalls);
        assertEquals(0, f.registry.failedStarts());
    }

    @Test
    @DisplayName("Ring start in the hooked driver: a first-ring scheduling failure leaves no ring and no task; an aura start afterwards owns the one task")
    void ringStartFailure() {
        K0Fixture f = new K0Fixture();
        f.failNextSchedule = true;
        assertThrows(IllegalStateException.class, () -> f.ring(A));
        assertNull(f.driver.ring(A));
        assertFalse(f.driver.running());
        f.aura(A);
        f.ring(A);
        assertEquals(1, f.schedules);
        assertNotNull(f.driver.ring(A));
    }
}
