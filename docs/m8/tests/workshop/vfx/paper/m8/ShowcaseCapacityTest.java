package workshop.vfx.paper.m8;

import static org.junit.jupiter.api.Assertions.*;
import static workshop.vfx.paper.m8.M8Fixture.A;
import static workshop.vfx.paper.m8.M8Fixture.B;
import static workshop.vfx.paper.m8.M8Fixture.C;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/** R3 F7: the S-X1 and S-X2 smoke scenarios replayed offline, so their capacity assertions are known to be reachable. */
class ShowcaseCapacityTest {
    private static final Map<String, Object> SX1_OVERRIDE = Map.of("maxActiveEffects", 8);
    private static final int SX1_PER_PLAYER = 3;

    private static void all(M8Fixture f, UUID p) { f.ring(p); f.aura(p); f.trail(p); f.activation(p); }
    private static int total(M8Fixture f) { return f.driver.activeCount() + f.registry.activeCount(); }
    private static void refused(String message, Executable start) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, start);
        assertEquals(message, e.getMessage());
    }

    @Test
    @DisplayName("S-X1 (override 8/3): two players reach 8 effects with one task; every further A/B start is a replacement and succeeds")
    void sx1TwoPlayersReplaceAtCapacity() {
        M8Fixture f = new M8Fixture(SX1_OVERRIDE, SX1_PER_PLAYER);
        all(f, A); all(f, B);
        assertEquals(8, total(f));
        assertEquals(1, f.schedules);
        // With one effect per kind and four kinds, A and B own every (player, kind) slot: a ninth unique effect from
        // them is impossible, so S-X1 asserts replacement at capacity instead (R2's ninth-effect step was unreachable).
        for (UUID p : List.of(A, B)) all(f, p);
        assertEquals(8, total(f), "replacement at capacity, every kind, both players");
        assertEquals(1, f.schedules);
        f.run();
        assertEquals(8, f.lastRequests.size(), "all eight effects in one plan");
    }

    @Test
    @DisplayName("S-X1b (override 8/3): a third player's first ring or M8 effect is the ninth unique effect and is refused; freed capacity admits it")
    void sx1bThirdOwnerRefused() {
        M8Fixture f = new M8Fixture(SX1_OVERRIDE, SX1_PER_PLAYER);
        f.players.add(C);
        all(f, A); all(f, B);
        refused("Active effect cap reached", () -> f.aura(C));
        refused("Active effect cap reached", () -> f.trail(C));
        refused("Active ring cap reached", () -> f.ring(C));
        assertEquals(8, total(f));
        f.aura(A);
        assertEquals(8, total(f), "replacement still permitted after a refusal");
        f.registry.stop(B, EffectKind.TRAIL);
        f.aura(C);
        assertEquals(8, total(f));
        refused("Active ring cap reached", () -> f.ring(C));
        assertEquals(1, f.schedules);
    }

    @Test
    @DisplayName("S-X2 (production defaults 4/2), ring-first order: the fifth unique effect is refused by the global cap; replacements succeed")
    void sx2RingFirst() {
        M8Fixture f = new M8Fixture(Map.of(), 2);
        f.ring(A); f.aura(A); f.ring(B); f.aura(B);
        assertEquals(4, total(f));
        refused("Active effect cap reached", () -> f.trail(B));       // B has 1 of 2 M8 effects: global cap, not per-player
        refused("Active effect cap reached", () -> f.trail(A));
        f.ring(B); f.aura(A);
        assertEquals(4, total(f));
    }

    @Test
    @DisplayName("S-X2 (production defaults 4/2), M8-first order: the fifth is refused on the ring path; a third M8 kind hits the per-player cap")
    void sx2M8First() {
        M8Fixture f = new M8Fixture(Map.of(), 2);
        f.aura(A); f.trail(A); f.aura(B); f.trail(B);
        assertEquals(4, total(f));
        refused("Active ring cap reached", () -> f.ring(A));
        refused("Per-player M8 effect cap reached", () -> f.activation(A));
        f.trail(A); f.aura(B);
        assertEquals(4, total(f));
    }
}
