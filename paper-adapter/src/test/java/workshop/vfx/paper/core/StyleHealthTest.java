package workshop.vfx.paper.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static workshop.vfx.paper.core.DemoFixture.ANY_POINT;
import static workshop.vfx.paper.core.DemoFixture.CATALOGUE;
import static workshop.vfx.paper.core.DemoFixture.DUST;
import static workshop.vfx.paper.core.DemoFixture.END_ROD;
import static workshop.vfx.paper.core.DemoFixture.context;
import static workshop.vfx.paper.core.DemoFixture.ring;
import static workshop.vfx.paper.core.DemoFixture.roomy;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.delivery.DeliveryPlan;
import workshop.vfx.delivery.DeliveryPlanner;
import workshop.vfx.delivery.Outcome;
import workshop.vfx.delivery.QualityTier;
import workshop.vfx.scene.StyleKey;

/** R2 F3: session-wide style disabling after three consecutive send failures (contract 7.1); M6 is unchanged. */
class StyleHealthTest {
    private static final long V = 3;
    private static final EmissionLimits LIMITS = new EmissionLimits(4096, 1024);

    /** One BALANCED ring (32 END_ROD essential points first, then 16 DUST) to viewer 3, planned with the given styles. */
    private static DeliveryPlan plan(long tick, Set<StyleKey> styles) {
        return DeliveryPlanner.plan(tick, List.of(ring(1, QualityTier.BALANCED, 0, 0.5, tick, V)), styles, roomy());
    }

    private static EmissionReport emit(DeliveryPlan p, StyleHealth h, DemoFixture.Sink sink) {
        return new DefaultPlanEmitter().emit(p, Map.of(1L, context(1, V, 0, 0.5)), h,
                new DemoFixture.Viewers().put(V, true, false).gate(), ANY_POINT, sink, LIMITS);
    }

    @Test
    @DisplayName("Third consecutive failure disables the style at once; its remaining points in the tick are skipped")
    void disableAfterThree() {
        SessionStyleHealth h = new SessionStyleHealth(CATALOGUE);
        DemoFixture.Sink sink = new DemoFixture.Sink();
        sink.alwaysFail = DUST;
        EmissionReport r = emit(plan(0, h.enabledStyles()), h, sink);
        assertEquals(32, sink.count(END_ROD));
        assertEquals(3, r.failedCalls());
        assertEquals(13L, r.skippedPoints().get(SkipReason.STYLE_DISABLED));
        assertEquals(Set.of(DUST), r.stylesDisabledThisTick());
        assertTrue(h.isDisabled(DUST));
        assertEquals(Set.of(END_ROD), h.enabledStyles());
    }

    @Test
    @DisplayName("A success of the same style resets the streak: fail, fail, succeed, fail, fail does not disable")
    void streakResets() {
        SessionStyleHealth h = new SessionStyleHealth(CATALOGUE);
        DemoFixture.Sink sink = new DemoFixture.Sink();
        sink.script.put(DUST, List.of(true, true, false, true, true));
        EmissionReport r = emit(plan(0, h.enabledStyles()), h, sink);
        assertFalse(h.isDisabled(DUST));
        assertEquals(4, r.failedCalls());
        assertEquals(0, h.streak(DUST), "the sixth and later dust sends succeeded");
        assertTrue(r.stylesDisabledThisTick().isEmpty());
    }

    @Test
    @DisplayName("Successes of another style do not reset the streak; the streak spans ticks")
    void streakSpansTicksAndStyles() {
        SessionStyleHealth h = new SessionStyleHealth(CATALOGUE);
        DemoFixture.Sink sink = new DemoFixture.Sink();
        // tick 0: dust sends 1-14 succeed, 15 and 16 fail; tick 1: the first dust send fails -> third consecutive
        List<Boolean> s = new java.util.ArrayList<>(Collections.nCopies(14, false));
        s.add(true);
        s.add(true);
        s.add(true);
        sink.script.put(DUST, s);
        emit(plan(0, h.enabledStyles()), h, sink);
        assertEquals(2, h.streak(DUST));
        assertFalse(h.isDisabled(DUST));
        EmissionReport r1 = emit(plan(1, h.enabledStyles()), h, sink);
        assertTrue(h.isDisabled(DUST), "END_ROD successes in between did not reset the DUST streak");
        assertEquals(Set.of(DUST), r1.stylesDisabledThisTick());
    }

    @Test
    @DisplayName("The next planning pass excludes the disabled style: M6 reports it unsupported, nothing of it is sent")
    void nextPlanExcludes() {
        SessionStyleHealth h = new SessionStyleHealth(CATALOGUE);
        DemoFixture.Sink first = new DemoFixture.Sink();
        first.alwaysFail = DUST;
        emit(plan(0, h.enabledStyles()), h, first);
        DeliveryPlan next = plan(1, h.enabledStyles());
        assertEquals(Outcome.THINNED, next.deliveries().get(0).outcome());
        assertEquals(16, next.deliveries().get(0).unsupportedPoints());
        assertEquals(32, next.deliveries().get(0).keptPoints());
        DemoFixture.Sink sink = new DemoFixture.Sink();
        EmissionReport r = emit(next, h, sink);
        assertEquals(0, sink.count(DUST));
        assertEquals(32, r.succeededCalls());
        assertNull(r.skippedPoints().get(SkipReason.STYLE_DISABLED), "the planner no longer selects it");
    }

    @Test
    @DisplayName("All styles disabled: the planner skips the ring as unsupported before evaluation; a new session re-enables")
    void allDisabled() {
        SessionStyleHealth h = new SessionStyleHealth(CATALOGUE);
        for (int i = 0; i < 3; i++) {
            h.recordFailure(END_ROD);
            h.recordFailure(DUST);
        }
        assertTrue(h.enabledStyles().isEmpty());
        DeliveryPlan p = plan(0, h.enabledStyles());
        assertEquals(Outcome.SKIPPED_UNSUPPORTED, p.deliveries().get(0).outcome());
        assertNull(p.deliveries().get(0).frame());
        assertFalse(h.recordFailure(DUST), "disabling is reported once");
        assertEquals(CATALOGUE, new SessionStyleHealth(CATALOGUE).enabledStyles());
    }
}
