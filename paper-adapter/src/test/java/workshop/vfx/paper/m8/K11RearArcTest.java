package workshop.vfx.paper.m8;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static workshop.vfx.paper.m8.K1Fixture.*;
import workshop.vfx.delivery.QualityTier;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.paper.core.ParticleSpec;

/** K1.1 acceptance checks for the actual evaluated production shapes, not a reference implementation. */
class K11RearArcTest {
    @Test void frontStaysClearForEveryTierPhaseAndBodyYaw() {
        for (var tier : QualityTier.values()) {
            var f = new K1Fixture(); f.start(tier);
            for (int age = 0; age < 240; age++) {
                f.tick = 100 + age; f.position.get(A)[3] = age * 17.5;
                var draft = f.draft(A); var inverse = draft.anchor().inverse();
                var arc = frame(draft).batches().getFirst(); int behind = 0;
                for (int i = 0; i < arc.size(); i++) {
                    Vec3 local = inverse.toWorld(arc.point(i));
                    if (local.z() < 1e-10) behind++;
                    // Even Heavy leaves the forward-facing 144-degree wedge empty at maximum sway.
                    assertTrue(Math.abs(Math.atan2(local.x(), local.z())) >= Math.toRadians(72) - 1e-10);
                }
                assertTrue(behind >= (tier == QualityTier.HEAVY ? 13 : arc.size()), tier + " at age " + age);
            }
        }
    }

    @Test void arcIncludesBothEndsWithUniformSpacingAndDistinctTierSpans() {
        for (var tier : QualityTier.values()) {
            var f = new K1Fixture(); f.start(tier);
            var arc = frame(f.draft(A)).batches().getFirst();
            double span = Math.toRadians(tier == QualityTier.SIMPLE ? 120 : tier == QualityTier.BALANCED ? 160 : 200);
            double first = Math.PI / 2 - span / 2 - Math.toRadians(8);
            for (int i = 0; i < arc.size(); i++) {
                double angle = first + span * i / (arc.size() - 1);
                assertEquals(0.9 * Math.cos(angle), arc.x(i), 1e-12);
                assertEquals(-0.9 * Math.sin(angle), arc.z(i), 1e-12);
                if (i > 0) assertEquals(1.8 * Math.sin(span / (arc.size() - 1) / 2),
                    arc.point(i).sub(arc.point(i - 1)).length(), 1e-12);
            }
            assertTrue(arc.point(0).sub(arc.point(arc.size() - 1)).length() > 1.5);
        }
    }

    @Test void waistCentroidRemainsBehindTheTurningPlayerThroughoutSway() {
        var f = new K1Fixture(); f.start(QualityTier.HEAVY);
        for (int age = 0; age < 120; age++) {
            f.tick = 100 + age; f.position.put(A, new double[]{12 + age * 0.28, 65, -8, age * 9, 80, -90});
            var draft = f.draft(A); var arc = frame(draft).batches().getFirst();
            Vec3 sum = Vec3.ZERO;
            for (int i = 0; i < arc.size(); i++) sum = sum.add(draft.anchor().inverse().toWorld(arc.point(i)));
            Vec3 centroid = sum.mul(1.0 / arc.size());
            assertTrue(centroid.z() < -0.4);
            assertTrue(Math.abs(centroid.x()) < 0.1);
            assertEquals(1.0, centroid.y(), 1e-10);
        }
    }

    @Test void swayHasNoLoopJumpAndSlowsAtBothTurnarounds() {
        var f = new K1Fixture(); f.balanced(); Vec3[] points = new Vec3[121];
        for (int age = 0; age <= 120; age++) {
            f.tick = 100 + age; points[age] = frame(f.draft(A)).batches().getFirst().point(0);
            if (age > 0) assertTrue(points[age].sub(points[age - 1]).length() < 0.013);
        }
        assertTrue(points[0].sub(points[60]).length() < 1e-12);
        assertTrue(points[60].sub(points[120]).length() < 1e-12);
        for (int turn : new int[]{30, 60, 90}) {
            assertTrue(points[turn].sub(points[turn - 1]).length() < 0.001);
            assertTrue(points[turn + 1].sub(points[turn]).length() < 0.001);
        }
        assertTrue(points[15].sub(points[14]).length() > 0.01);
    }

    @Test void haloRetainsHeightRadiusAndSpinWithWiderParticleSeparation() {
        assertEquals(new ParticleSpec.Dust(0x5CE1FF, 0.55f), M8StyleCatalog.whitelist().get(M8StyleCatalog.CYAN_HALO));
        for (var tier : new QualityTier[]{QualityTier.BALANCED, QualityTier.HEAVY}) {
            var f = new K1Fixture(); f.start(tier);
            var halo = frame(f.draft(A)).batches().get(1);
            assertEquals(tier == QualityTier.BALANCED ? 6 : 8, halo.size());
            for (int i = 0; i < halo.size(); i++) {
                assertEquals(66.15, halo.y(i), 1e-12);
                assertEquals(0.45, Math.hypot(halo.x(i), halo.z(i)), 1e-12);
                assertTrue(halo.point(i).sub(halo.point((i + 1) % halo.size())).length() > 0.34);
            }
            f.tick = 120; var quarter = frame(f.draft(A)).batches().get(1);
            assertEquals(0, quarter.x(0), 1e-12); assertEquals(0.45, quarter.z(0), 1e-12);
        }
    }

    @Test void tightBudgetStillEmitsOnlyRearArcPointsWithoutChangingSharedScheduling() {
        var f = new K1Fixture(Map.of("maxCallsPerTick", 4, "maxCallsPerViewerPerTick", 4), Map.of());
        f.start(QualityTier.HEAVY);
        for (int i = 0; i < 120; i++) {
            f.run(); assertFalse(f.sends.isEmpty()); assertTrue(f.sends.size() <= 4);
            for (var sent : f.sends) {
                assertEquals(workshop.vfx.paper.core.DemoStyleCatalog.END_ROD, sent.style());
                assertTrue(Math.abs(Math.atan2(sent.x(), sent.z())) >= Math.toRadians(72) - 1e-10);
            }
            f.account(); f.tick++;
        }
        assertEquals(1, f.schedules); assertEquals(120, f.plans);
    }
}
