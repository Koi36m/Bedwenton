package workshop.vfx.paper.m8;

import static org.junit.jupiter.api.Assertions.*;
import static workshop.vfx.paper.m8.M8Fixture.A;
import static workshop.vfx.paper.m8.M8Fixture.B;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** R2 F1: one global capacity for M7 rings and M8 effects, independent of activation order. */
class SharedCapacityTest {
    @Test
    @DisplayName("Rings first, then M8: at cap 2, two rings refuse a new aura; a ring replacement still succeeds")
    void ringsFillCap() {
        M8Fixture f = new M8Fixture(Map.of("maxActiveEffects", 2), 2);
        f.ring(A); f.ring(B);
        assertThrows(IllegalArgumentException.class, () -> f.aura(A));
        f.ring(A);                                   // replacement at capacity
        assertEquals(2, f.driver.activeCount());
        assertEquals(0, f.registry.activeCount());
    }

    @Test
    @DisplayName("M8 first, then rings: M8 effects count toward the ring path's cap; replacement of an M8 effect at cap succeeds")
    void m8FillsCap() {
        M8Fixture f = new M8Fixture(Map.of("maxActiveEffects", 3), 2);
        f.aura(A); f.trail(A); f.aura(B);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> f.ring(B));
        assertTrue(e.getMessage().contains("cap"));
        long old = f.registry.effect(A, EffectKind.AURA).orElseThrow().requestId();
        long replaced = f.aura(A);                   // replacement at capacity
        assertTrue(replaced > old);
        assertEquals(3, f.registry.activeCount());
        assertEquals(0, f.driver.activeCount());
    }

    @Test
    @DisplayName("Every activation order of a mixed set reaches exactly the cap and refuses the next start, whatever its kind")
    void orderIndependence() {
        List<Consumer<M8Fixture>> starts = List.of(f -> f.ring(A), f -> f.aura(A), f -> f.trail(B), f -> f.ring(B), f -> f.aura(B));
        int permutations = 0;
        for (List<Integer> order : permutations(List.of(0, 1, 2, 3, 4))) {
            M8Fixture f = new M8Fixture(Map.of("maxActiveEffects", 4), 2);
            int admitted = 0, refused = 0;
            for (int i : order) {
                try { starts.get(i).accept(f); admitted++; } catch (IllegalArgumentException e) { refused++; }
            }
            assertEquals(4, admitted, "order " + order);
            assertEquals(1, refused, "order " + order);
            assertEquals(4, f.driver.activeCount() + f.registry.activeCount(), "order " + order);
            assertEquals(1, f.schedules, "one shared task, order " + order);
            permutations++;
        }
        assertEquals(120, permutations);
    }

    @Test
    @DisplayName("Per-player M8 cap: a third M8 kind is refused at 2 even below the global cap; replacement still allowed")
    void perPlayerCap() {
        M8Fixture f = new M8Fixture(Map.of("maxActiveEffects", 16), 2);
        f.aura(A); f.trail(A);
        assertThrows(IllegalArgumentException.class, () -> f.activation(A));
        f.trail(A);
        f.activation(B);
        assertEquals(3, f.registry.activeCount());
    }

    @Test
    @DisplayName("M7-only compatibility: without a contributor the hooked driver admits exactly as M7 (cap counts rings only)")
    void m7Only() {
        M8Fixture f = new M8Fixture(Map.of("maxActiveEffects", 1), 2);
        f.ring(A);
        for (int n = 0; n < 5; n++) f.ring(A);
        assertThrows(IllegalArgumentException.class, () -> f.ring(B));
        assertEquals(1, f.driver.activeCount());
        assertEquals(1, f.schedules);
    }

    @Test
    @DisplayName("Freed capacity is reusable by either path: stopping an aura admits a ring, stopping a ring admits an aura")
    void freedCapacity() {
        M8Fixture f = new M8Fixture(Map.of("maxActiveEffects", 2), 2);
        f.ring(A); f.aura(B);
        assertThrows(IllegalArgumentException.class, () -> f.ring(B));
        f.registry.stop(B, EffectKind.AURA);
        f.ring(B);
        f.driver.stop(A);
        f.aura(A);
        assertEquals(2, f.driver.activeCount() + f.registry.activeCount());
    }

    static List<List<Integer>> permutations(List<Integer> items) {
        if (items.isEmpty()) return List.of(List.of());
        List<List<Integer>> out = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            List<Integer> rest = new ArrayList<>(items); Integer head = rest.remove(i);
            for (List<Integer> p : permutations(rest)) { List<Integer> l = new ArrayList<>(); l.add(head); l.addAll(p); out.add(l); }
        }
        return out;
    }
}
