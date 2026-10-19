package workshop.vfx.paper.m8;

import static org.junit.jupiter.api.Assertions.*;
import static workshop.vfx.paper.m8.M8Fixture.A;
import static workshop.vfx.paper.m8.M8Fixture.B;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.delivery.Outcome;

/** R2 F2: M8 effects run without any M7 ring; one shared task; one planning call per tick with requests; zero-request ticks skip planning. */
class M8OnlySchedulingTest {
    @Test
    @DisplayName("Aura only: first M8 start schedules the one task; each tick plans once with the aura request; stop cancels")
    void auraOnly() {
        M8Fixture f = new M8Fixture();
        assertFalse(f.driver.running());
        f.aura(A);
        assertTrue(f.driver.running());
        assertEquals(1, f.schedules);
        f.run();
        assertEquals(1, f.plans);
        assertEquals(1, f.lastRequests.size());
        assertTrue(f.lastRequests.getFirst().id() >= DynamicEffectRegistry.FIRST_ID);
        assertEquals(Outcome.DELIVERED, f.lastPlan.deliveries().getFirst().outcome());
        assertEquals(27, f.sends.size(), "balanced aura, owner only");
        f.run();                                     // duplicate tick: no second plan
        assertEquals(1, f.plans);
        f.step();
        assertEquals(2, f.plans);
        f.registry.stop(A, EffectKind.AURA);
        assertFalse(f.driver.running());
        assertEquals(1, f.cancels);
    }

    @Test
    @DisplayName("Ring and M8 together: exactly one planning call per tick containing both requests; one task")
    void mixedOnePlan() {
        M8Fixture f = new M8Fixture();
        f.ring(A); f.aura(B);
        assertEquals(1, f.schedules);
        f.run();
        assertEquals(1, f.plans);
        assertEquals(2, f.lastRequests.size());
        f.driver.stop(A);                            // ring gone, aura keeps the task alive
        assertTrue(f.driver.running());
        f.step();
        assertEquals(2, f.plans);
        assertEquals(1, f.lastRequests.size());
    }

    @Test
    @DisplayName("Stationary trail with no live samples: task keeps lightweight ticks, planner is skipped, zero report; moving resumes planning")
    void zeroRequests() {
        M8Fixture f = new M8Fixture();
        f.trail(A);
        f.run();                                     // first observation: one sample, one request
        assertEquals(1, f.plans);
        for (int i = 0; i < 31; i++) f.step();       // standing still past maxAge (30)
        int plansBefore = f.plans;
        f.step();
        assertEquals(plansBefore, f.plans, "no planner call for an empty request set");
        assertTrue(f.driver.running(), "the trail is still active; the shared task keeps running");
        assertEquals(0, f.driver.lastReport().plannedPlacements());
        assertEquals(f.tick, f.driver.lastReport().tickOrdinal());
        f.positions.get(A)[0] += 1.0;                // move 1 block
        f.step();
        assertEquals(plansBefore + 1, f.plans);
    }

    @Test
    @DisplayName("Stale callbacks: a callback from a cancelled generation never plans, also after an M8-only restart")
    void staleCallbacks() {
        M8Fixture f = new M8Fixture();
        f.aura(A);
        Runnable old = f.callback;
        f.registry.stop(A, EffectKind.AURA);
        old.run();
        assertEquals(0, f.plans);
        f.aura(B);
        old.run();
        assertEquals(0, f.plans);
        f.run();
        assertEquals(1, f.plans);
        assertEquals(2, f.schedules);
    }

    @Test
    @DisplayName("Natural end and termination: finished effects are disposed in the tick, and the task is cancelled when nothing remains")
    void endAndTerminate() {
        M8Fixture f = new M8Fixture();
        f.activation(A);                             // 80-tick lifetime
        f.run();
        for (int i = 0; i < 80; i++) f.step();
        assertEquals(0, f.registry.activeCount());
        assertFalse(f.driver.running());
        f.aura(B);
        f.offline.add(B);                            // owner gone -> TERMINATE
        f.step();
        assertEquals(0, f.registry.activeCount());
        assertFalse(f.driver.running());
    }

    @Test
    @DisplayName("Shutdown: close disposes M8 effects and rings, cancels the task, refuses later starts, and stale ticks do nothing")
    void shutdown() {
        M8Fixture f = new M8Fixture();
        f.ring(A); f.aura(A); f.trail(B);
        Runnable cb = f.callback;
        f.driver.close();
        assertEquals(0, f.registry.activeCount());
        assertEquals(0, f.driver.activeCount());
        assertFalse(f.driver.running());
        f.tick++; cb.run();
        assertEquals(0, f.plans);
        assertThrows(IllegalStateException.class, () -> f.aura(A));
        assertThrows(IllegalStateException.class, () -> f.ring(A));
    }
}
