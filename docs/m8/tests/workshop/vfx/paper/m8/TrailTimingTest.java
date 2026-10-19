package workshop.vfx.paper.m8;

import static org.junit.jupiter.api.Assertions.*;
import static workshop.vfx.paper.m8.M8Fixture.A;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.delivery.DeliveryRequest;
import workshop.vfx.delivery.EffectDelivery;
import workshop.vfx.delivery.Outcome;

/** R2 F3: the trail's effect clock (lifetime, frame ordinal) is separate from its rebuilt 2-tick scene clock. */
class TrailTimingTest {
    private static EffectDelivery trailDelivery(M8Fixture f) {
        return f.lastPlan.deliveries().stream().filter(d -> d.requestId() >= DynamicEffectRegistry.FIRST_ID).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("A moving player's trail emits on every tick well beyond the first two, including local ticks 3, 50, 100 and 299")
    void emitsBeyondTwoTicks() {
        M8Fixture f = new M8Fixture();
        f.trail(A, 300);
        long start = f.tick;
        f.run();
        for (int local = 1; local < 300; local++) {
            f.positions.get(A)[0] += 0.28;           // sprint along +X
            f.step();
            EffectDelivery d = trailDelivery(f);
            assertTrue(d.outcome() == Outcome.DELIVERED || d.outcome() == Outcome.THINNED, "local " + local + ": " + d.outcome());
            assertTrue(d.keptPoints() > 0, "local " + local);
            if (local == 3 || local == 50 || local == 100 || local == 299) {
                DeliveryRequest q = f.lastRequests.getFirst();
                assertEquals(local, q.frameOrdinal(), "frame ordinal = effect clock");
                assertEquals(0.0, q.playback().localTicks(q.now()), "scene clock restarted each tick");
                assertEquals(start + local, f.tick);
            }
        }
        f.positions.get(A)[0] += 0.28;
        f.step();                                    // local 300 = lifetime: effect finished and disposed
        assertEquals(0, f.registry.activeCount());
        assertFalse(f.driver.running());
    }

    @Test
    @DisplayName("Lifetime is measured on the effect clock: a 1200-tick trail is still active at local 100 and ends exactly at 1200")
    void lifetimeClock() {
        M8Fixture f = new M8Fixture();
        f.trail(A, 1200);
        f.run();
        for (int local = 1; local < 1200; local++) {
            f.positions.get(A)[2] += 0.3;
            f.step();
            if (local == 100) assertEquals(1, f.registry.activeCount());
        }
        assertEquals(1, f.registry.activeCount(), "local 1199 still active");
        f.step();
        assertEquals(0, f.registry.activeCount(), "local 1200 finished");
    }

    @Test
    @DisplayName("History expiry: after stopping at local 100, live samples drop to zero after maxAge and requests stop")
    void expiry() {
        M8Fixture f = new M8Fixture();
        f.trail(A, 1200);
        f.run();
        for (int local = 1; local <= 100; local++) { f.positions.get(A)[0] += 0.28; f.step(); }
        TrailEffect t = (TrailEffect) f.registry.effect(A, EffectKind.TRAIL).orElseThrow();
        long stoppedAt = f.tick;
        assertTrue(t.history().live(f.tick).size() > 0);
        int plans = f.plans;
        for (int k = 1; k <= 31; k++) f.step();      // maxAge = 30
        assertEquals(0, t.history().live(f.tick).size(), "all samples older than maxAge at " + (f.tick - stoppedAt));
        assertTrue(f.plans < plans + 31, "planning stopped once the history emptied");
        assertTrue(t.history().size() <= t.history().capacity());
    }

    @Test
    @DisplayName("Thinning phase follows the effect clock: with a kept cap below the trail size, M6's offset is floorMod(frameOrdinal, g)")
    void thinningPhase() {
        M8Fixture f = new M8Fixture(Map.of("simpleKeptCap", 4, "balancedKeptCap", 4, "heavyKeptCap", 72), 2);
        f.trail(A, 600);
        f.run();
        for (int local = 1; local <= 120; local++) {
            f.positions.get(A)[0] += 0.7;            // 2 samples per tick -> 24 samples quickly
            f.step();
            if (local >= 20 && local <= 23) {
                EffectDelivery d = trailDelivery(f);
                assertEquals(Outcome.THINNED, d.outcome());
                DeliveryRequest q = f.lastRequests.getFirst();
                int pos = 0, n = d.frame().batches().get(d.orderedBatch(pos)).size(), m = d.kept(pos);
                int g = (n + m - 1) / m;
                assertEquals((int) Math.floorMod(q.frameOrdinal(), (long) g), d.selectedIndex(pos, 0), "local " + local);
            }
        }
    }

    @Test
    @DisplayName("Discontinuity: a teleport (serial change) starts a new segment and no live sample lies between the two locations")
    void teleportBreak() {
        M8Fixture f = new M8Fixture();
        f.trail(A, 600);
        f.run();
        for (int i = 0; i < 10; i++) { f.positions.get(A)[0] += 0.3; f.step(); }
        f.positions.get(A)[0] = 500; f.serials.put(A, 1L);
        f.step();
        TrailEffect t = (TrailEffect) f.registry.effect(A, EffectKind.TRAIL).orElseThrow();
        List<TrailSample> live = t.history().live(f.tick);
        assertEquals(500, live.getFirst().x());
        assertTrue(live.stream().noneMatch(s -> s.x() > 10 && s.x() < 500), "no samples between old and new location");
        assertNotEquals(live.getFirst().segment(), live.get(1).segment());
    }

    @Test
    @DisplayName("Short teleport (3 blocks, below maxStep) with a serial change still breaks the segment and adds no interpolated samples")
    void shortTeleportBreak() {
        M8Fixture f = new M8Fixture();
        f.trail(A, 600);
        f.run();
        for (int i = 0; i < 5; i++) { f.positions.get(A)[0] += 0.3; f.step(); }
        TrailEffect t = (TrailEffect) f.registry.effect(A, EffectKind.TRAIL).orElseThrow();
        int before = t.history().live(f.tick).size();
        double from = f.positions.get(A)[0];
        f.positions.get(A)[0] = from + 3.0; f.serials.put(A, 1L);
        f.step();
        List<TrailSample> live = t.history().live(f.tick);
        assertEquals(before + 1, live.size(), "exactly one new sample: the teleport destination");
        assertEquals(from + 3.0, live.getFirst().x(), 1e-12);
        assertTrue(live.stream().noneMatch(s -> s.x() > from + 1e-9 && s.x() < from + 3.0 - 1e-9), "nothing between");
        assertNotEquals(live.getFirst().segment(), live.get(1).segment());
    }
}
