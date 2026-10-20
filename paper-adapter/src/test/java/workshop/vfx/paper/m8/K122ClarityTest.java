package workshop.vfx.paper.m8;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static workshop.vfx.paper.m8.K1Fixture.*;
import workshop.vfx.delivery.QualityTier;
import workshop.vfx.paper.core.DemoStyleCatalog;

/** Actual aura -> shared driver -> M6 -> emitter checks, including non-emission ticks. */
class K122ClarityTest {
    @Test void allTiersEmitWhiteOnlyEveryFourStartRelativeTicksWithExactGeometryAndAccounting() {
        for (var tier : QualityTier.values()) {
            var f = new K1Fixture(); f.tick = 103; f.start(tier);
            int total = new int[]{20,34,44}[tier.ordinal()], halo = new int[]{0,6,8}[tier.ordinal()];
            for (int age = 0; age < 160; age++) {
                f.tick = 103 + age;
                f.position.put(A, new double[]{age*.28,64+Math.sin(age/5.0),-age*.1,age*7,90,-90});
                f.run(); int expected = age%4==0 ? total : halo;
                assertEquals(expected, f.plan.reservedGeneratedPoints());
                assertEquals(expected, f.plan.sourcePoints()); assertEquals(expected, f.plan.placements());
                assertEquals(expected, f.sends.size());
                assertEquals(age%4==0 ? total-halo : 0,
                    f.sends.stream().filter(s -> s.style().equals(DemoStyleCatalog.END_ROD)).count());
                assertEquals(halo, f.sends.stream().filter(s -> s.style().equals(M8StyleCatalog.CYAN_HALO)).count());
                var d = f.draft(A); assertEquals(age, d.frameOrdinal());
                assertEquals(age, d.playback().localTicks(d.now()));
                for (var b : frame(d).batches()) for (int i=0; i<b.size(); i++)
                    assertTrue(b.point(i).sub(K12GeometryOracle.world(K12GeometryOracle.local(b,i,age), f.position.get(A))).length()<1e-9);
                f.account(); assertEquals(1, f.schedules); assertEquals(age+1, f.plans);
            }
        }
    }

    @Test void actualHaloAtBothCadencesMatchesCapturedBaselineBits() throws Exception {
        var root=Path.of(System.getProperty("adapter.root","."));
        var rows=Files.readAllLines(root.resolve("src/test/resources/m8-k1.2/k1.1-halo-coordinates.csv"));
        assertEquals(85,rows.size());
        for (String line : rows.subList(1,rows.size())) {
            String[] row=line.split(","); var f=new K1Fixture();
            f.position.put(A,new double[]{2.5,70,-4,135,45,-90}); f.start(QualityTier.valueOf(row[0]));
            f.tick=100+Long.parseLong(row[1]); var halo=frame(f.draft(A)).batches().getLast();
            assertEquals("halo",halo.layer()); int i=Integer.parseInt(row[2]);
            assertEquals(Double.doubleToLongBits(Double.parseDouble(row[3])),Double.doubleToLongBits(halo.x(i)));
            assertEquals(Double.doubleToLongBits(Double.parseDouble(row[4])),Double.doubleToLongBits(halo.y(i)));
            assertEquals(Double.doubleToLongBits(Double.parseDouble(row[5])),Double.doubleToLongBits(halo.z(i)));
        }
    }

    @Test void missedTicksDoNotPauseRestartOrCatchUpTheRotation() {
        var f=new K1Fixture(); f.balanced(); var zero=frame(f.draft(A)); var clock=f.draft(A).playback();
        for (int age : new int[]{1,13,16,20,79,80,81,84}) {
            f.tick=100+age; var d=f.draft(A); assertSame(clock,d.playback());
            assertEquals(age,d.playback().localTicks(d.now())); var actual=frame(d);
            assertEquals(age%4==0?34:6,actual.pointCount());
            if(age%4==0) {
                for(var b:actual.batches())for(int i=0;i<b.size();i++)
                    assertTrue(b.point(i).sub(K12GeometryOracle.world(K12GeometryOracle.local(b,i,age),f.position.get(A))).length()<1e-9);
                if(age==80) for(int b=0;b<4;b++)assertArrayEquals(zero.batches().get(b).coordinates(),actual.batches().get(b).coordinates(),1e-12);
            }
        }
    }

    @Test void twoVariantsAreSessionCachedAndHaloLayerIsSharedAcrossOwnersAndReplacement() {
        var f=new K1Fixture(); f.balanced(); var full=f.draft(A).recipe();
        f.tick=101; var quiet=f.draft(A).recipe(); assertNotSame(full,quiet);
        for(var tier:QualityTier.values()) {
            var layers=quiet.variant(tier).layers(); assertEquals(tier==QualityTier.SIMPLE?0:1,layers.size());
            if(!layers.isEmpty())assertSame(full.variant(tier).layers().getLast(),layers.getFirst());
        }
        f.auras.start(B,QualityTier.HEAVY,101); assertSame(full,f.draft(B).recipe());
        f.tick=102; assertSame(quiet,f.draft(A).recipe()); assertSame(quiet,f.draft(B).recipe());
        f.tick=104; assertSame(full,f.draft(A).recipe()); assertSame(quiet,f.draft(B).recipe());
        f.start(QualityTier.SIMPLE); assertSame(full,f.draft(A).recipe()); assertEquals(1,f.schedules);
    }

    @Test void skippedWhiteReservesNothingAndSharesOneGlobalBudgetWithUnchangedRing() {
        var f=new K1Fixture(Map.of("maxCallsPerTick",56,"maxCallsPerViewerPerTick",56),Map.of());
        f.ring(A); f.start(QualityTier.HEAVY); f.tick=101; f.run();
        assertEquals(1,f.plans); assertEquals(1,f.schedules); assertEquals(2,f.requests.size());
        assertEquals(56,f.plan.reservedGeneratedPoints()); assertEquals(56,f.plan.placements());
        assertEquals(56,f.sends.size()); assertEquals(32,f.sends.stream().filter(s->s.style().equals(DemoStyleCatalog.END_ROD)).count());
        assertEquals(16,f.sends.stream().filter(s->s.style().equals(DemoStyleCatalog.DUST)).count());
        assertEquals(8,f.sends.stream().filter(s->s.style().equals(M8StyleCatalog.CYAN_HALO)).count()); f.account();
        f.tick=104; f.run(); assertEquals(2,f.plans); assertTrue(f.plan.placements()<=56); f.account();
    }

    @Test void quietTicksStillCleanUpDeathDisconnectExpiryAndExplicitStops() {
        for(var tier:QualityTier.values())for(int reason=0;reason<4;reason++) {
            var f=new K1Fixture(Map.of(),Map.of("auraLifetimeTicks",21)); f.start(tier); f.run(); f.tick=101;
            if(reason==0)f.dead.add(A); else if(reason==1){f.offline.add(A);f.ids.release(A);}
            else if(reason==2)f.tick=121; else f.auras.stop(A);
            f.run(); assertFalse(f.registry.active()); assertFalse(f.driver.running());
            assertEquals(1,f.cancels); assertTrue(f.sends.isEmpty());
        }
    }

    @Test void quietTicksKeepFailClosedIdentityPermissionsAndRecoveryPhase() {
        var f=new K1Fixture(); f.balanced(); f.tick=101;
        f.tamper=s->replace(s,s.tick()-1,null,null,null,null,null,null,null,null,null,null);
        f.run(); assertEquals(0,f.plans); assertTrue(f.registry.active());
        f.tamper=java.util.function.UnaryOperator.identity(); f.tick=102; f.run();
        assertEquals(6,f.sends.size()); assertEquals(2,f.requests.getFirst().frameOrdinal()); f.account();
        f.tick=104; f.run(); assertEquals(34,f.sends.size()); assertEquals(4,f.requests.getFirst().frameOrdinal());
        var g=new K1Fixture(); g.balanced(); g.tick=101; g.canView.add(B);
        g.afterPlan=()->{g.noUse.add(A);g.canView.clear();}; g.run();
        assertTrue(g.sends.isEmpty()); assertEquals(12,g.driver.lastReport().placementsInSkippedRecipients()); g.account();
        var h=new K1Fixture(); h.balanced(); h.tick=101;
        h.tamper=s->replace(s,null,B,null,null,null,null,null,null,null,null,null); h.run();
        assertEquals(0,h.plans); assertFalse(h.registry.active()); assertEquals(1,h.registry.rejections(AnchorRejection.OWNER_MISMATCH));
    }

    @Test void duplicateCallbacksDoNotBurstAndSimpleQuietTicksKeepOneTaskAlive() {
        var f=new K1Fixture(); f.start(QualityTier.SIMPLE); f.run(); f.run(); assertEquals(1,f.plans);
        f.tick=101; f.run(); assertTrue(f.sends.isEmpty()); assertTrue(f.registry.active());
        assertTrue(f.driver.running()); assertEquals(0,f.plan.reservedGeneratedPoints()); f.account();
        f.run(); assertEquals(2,f.plans); f.tick=104; f.run(); assertEquals(20,f.sends.size());
        assertEquals(3,f.plans); assertEquals(1,f.schedules); f.auras.stop(A); assertEquals(1,f.cancels);
    }
}
