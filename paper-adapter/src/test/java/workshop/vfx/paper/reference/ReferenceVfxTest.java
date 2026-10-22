package workshop.vfx.paper.reference;

import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static workshop.vfx.paper.reference.ReferenceFixture.*;
import workshop.vfx.delivery.*;
import workshop.vfx.math.core.*;
import workshop.vfx.paper.core.*;
import workshop.vfx.paper.m8.*;
import workshop.vfx.scene.*;

class ReferenceVfxTest {
    private static FrameBatch named(EffectFrame frame,String name){return frame.batches().stream().filter(b->b.layer().equals(name)).findFirst().orElseThrow();}
    private static boolean has(EffectFrame frame,String name){return frame.batches().stream().anyMatch(b->b.layer().equals(name));}
    private static EffectFrame at(ReferenceFixture f,long age){f.tick=100+age;return frame(f.draft(A));}

    @Test void unionOfImmutableLayersFitsExistingTierCeilingsWithoutChangingOldCatalogue() {
        var recipes=ReferenceRecipe.create();assertEquals(16,recipes.size());
        for(var tier:QualityTier.values()) {
            Map<String,Layer> layers=new HashMap<>();
            for(var r:recipes){assertEquals(EffectPriority.NORMAL,r.priority());assertTrue(r.variant(tier).maxPointsPerFrame()<=new int[]{24,48,72}[tier.ordinal()]);for(var l:r.variant(tier).layers()){var old=layers.putIfAbsent(l.name(),l);if(old!=null)assertSame(old,l);}}
            assertEquals(10,layers.size());assertEquals(new int[]{24,48,72}[tier.ordinal()],layers.values().stream().mapToInt(l->l.shape().size()).sum());
        }
        var all=ReferenceStyleCatalog.whitelist();M8StyleCatalog.whitelist().forEach((k,v)->assertEquals(v,all.get(k)));
        assertEquals(7,all.size());assertThrows(UnsupportedOperationException.class,()->all.clear());assertThrows(UnsupportedOperationException.class,()->recipes.clear());
    }

    @Test void activationPhasesBuildFromAmbientThroughCrownToCompleteSigil() {
        var f=new ReferenceFixture();f.start(QualityTier.HEAVY);
        assertEquals(List.of("ambient"),at(f,0).batches().stream().map(FrameBatch::layer).toList());
        assertTrue(at(f,19).isEmpty());var head=at(f,20);assertTrue(has(head,"crown"));assertTrue(has(head,"ground"));assertFalse(has(head,"disc"));
        assertFalse(has(at(f,36),"disc"));var forming=at(f,40);assertTrue(has(forming,"disc"));assertTrue(named(forming,"disc").size()<24);
        var complete=at(f,100);assertEquals(24,named(complete,"disc").size());for(int i=0;i<4;i++)assertEquals(5,named(complete,"spiral-"+i).size());
        assertTrue(has(at(f,196),"disc"));f.tick=300;assertTrue(f.current().finished(f.tick));
    }

    @Test void everyWhitePointStaysInRearVerticalPlaneForAllYawAndMovement() {
        for(var tier:QualityTier.values()) {
            var f=new ReferenceFixture();f.start(tier);
            for(int age=40;age<200;age+=4) {
                f.tick=100+age;f.position.put(A,new double[]{age*.28,64+2*Math.sin(age/5.0),-age*.17,age*19.5,87,-90});
                var d=f.draft(A);
                for(var b:frame(d).batches())if(b.style().equals(DemoStyleCatalog.END_ROD))for(int i=0;i<b.size();i++) {
                    var local=d.anchor().inverse().toWorld(b.point(i));assertEquals(-1,local.z(),1e-10);
                    double radius=Math.hypot(local.x(),local.y()-1.55);
                    if(b.layer().equals("disc"))assertEquals(1.3,radius,1e-10);else assertTrue(radius<1.27);
                }
            }
        }
    }

    @Test void stableCompleteOutlineAndAsymmetricFourCurveMotifHaveQuarterTurnAnd80TickPeriod() {
        var f=new ReferenceFixture();f.start(QualityTier.HEAVY);var a=at(f,100);var q=at(f,120);var repeat=at(f,180);
        assertArrayEquals(named(a,"disc").coordinates(),named(q,"disc").coordinates());
        for(int b=0;b<4;b++)for(int i=0;i<5;i++) {
            var p=named(a,"spiral-"+b).point(i);var r=named(q,"spiral-"+b).point(i);var s=named(repeat,"spiral-"+b).point(i);
            assertEquals(-(p.y()-65.55),r.x(),1e-12);assertEquals(65.55+p.x(),r.y(),1e-12);assertEquals(p.z(),r.z(),1e-12);
            assertTrue(p.sub(s).length()<1e-12);
        }
        for(int b=0;b<4;b++) {
            var curve=named(a,"spiral-"+b);var origin=curve.point(0);var chord=curve.point(4).sub(origin);double deviation=0;
            for(int i=1;i<4;i++)deviation=Math.max(deviation,curve.point(i).sub(origin).cross(chord).length()/chord.length());
            assertTrue(deviation>.01,"curve "+b);
        }
    }

    @Test void cadenceIsStartRelativeSparseAndNeverReplaysMissedTicks() {
        var f=new ReferenceFixture();f.tick=103;f.start(QualityTier.HEAVY);
        for(int age=0;age<200;age++) {
            f.tick=103+age;var d=f.draft(A);assertEquals(age,d.frameOrdinal());assertEquals(age,d.playback().localTicks(d.now()));
            var frame=frame(d);if(age%4!=0)assertTrue(frame.batches().stream().noneMatch(b->b.style().equals(DemoStyleCatalog.END_ROD)));
            if(age>100&&age%4==0)assertEquals(44,frame.batches().stream().filter(b->b.style().equals(DemoStyleCatalog.END_ROD)).mapToInt(FrameBatch::size).sum());
        }
        f.tick=220;var clock=f.draft(A).playback();f.tick=267;var late=f.draft(A);assertSame(clock,late.playback());assertEquals(164,late.frameOrdinal());
        assertEquals(44,frame(late).batches().stream().filter(b->b.style().equals(DemoStyleCatalog.END_ROD)).mapToInt(FrameBatch::size).sum());
    }

    @Test void crownIsLimeAboveHeadAndFloatsWhileGroundRingExpandsAtFeet() {
        var f=new ReferenceFixture();f.start(QualityTier.HEAVY);var a=at(f,20);var b=at(f,40);
        for(var frame:List.of(a,b))for(var layer:frame.batches())if(layer.layer().equals("crown"))for(int i=0;i<layer.size();i++) {
            assertEquals(ReferenceStyleCatalog.LIME,layer.style());assertEquals(.43,Math.hypot(layer.x(i),layer.z(i)),1e-12);
            assertTrue(layer.y(i)>=66.35&&layer.y(i)<=66.63+1e-12);
        }
        assertTrue(named(b,"crown").y(0)>named(a,"crown").y(0));
        for(var frame:List.of(a,b))for(int i=0;i<named(frame,"ground").size();i++)assertEquals(64.08,named(frame,"ground").y(i),1e-12);
        assertEquals(.28,Math.hypot(named(a,"ground").x(0),named(a,"ground").z(0)),1e-12);
        assertEquals(.665,Math.hypot(named(b,"ground").x(0),named(b,"ground").z(0)),1e-12);
    }

    @Test void twinkleGroupsAlternateAndAmbientMotesAreBoundedDeterministic() {
        var f=new ReferenceFixture();f.start(QualityTier.HEAVY);
        for(int age=40;age<200;age++) {
            var frame=at(f,age);assertEquals(age%16==0,has(frame,"star-white"));assertEquals(age%16==8,has(frame,"star-gold"));
            assertEquals(age%8==0,has(frame,"ambient"));
            if(has(frame,"ambient"))for(int i=0;i<4;i++) {
                var p=named(frame,"ambient").point(i);assertEquals(.95,Math.hypot(p.x(),p.z()),1e-12);assertTrue(p.y()>=64.45&&p.y()<=65.86+1e-12);
            }
        }
        var one=at(f,128);var two=at(f,128);for(int i=0;i<one.batches().size();i++)assertArrayEquals(one.batches().get(i).coordinates(),two.batches().get(i).coordinates());
    }

    @Test void noActivationUntilExplicitStartAndExpiryIsExactly200Ticks() {
        var f=new ReferenceFixture();assertEquals(0,f.schedules);assertFalse(f.registry.active());f.start(QualityTier.BALANCED);
        f.tick=299;f.run();assertTrue(f.registry.active());assertEquals(1,f.schedules);f.tick=300;f.run();
        assertFalse(f.registry.active());assertFalse(f.driver.running());assertEquals(1,f.cancels);assertTrue(f.sends.isEmpty());
    }

    @Test void deathDisconnectAndShutdownDisposeAndStopTheSharedTask() {
        for(int reason=0;reason<3;reason++) {
            var f=new ReferenceFixture();f.start(QualityTier.HEAVY);var effect=f.current();f.tick=152;
            if(reason==0)f.dead.add(A);else if(reason==1){f.offline.add(A);f.ids.release(A);}else f.driver.close();
            f.run();assertTrue(effect.finished(f.tick));assertFalse(f.registry.active());assertFalse(f.driver.running());assertTrue(f.sends.isEmpty());assertEquals(1,f.cancels);
        }
    }

    @Test void referenceReplacementAndOffLeaveArcaneHaloIntact() {
        var f=new ReferenceFixture();f.auras.start(A,QualityTier.BALANCED,f.tick);var aura=f.registry.effect(A,EffectKind.AURA).orElseThrow();
        f.start(QualityTier.HEAVY);var old=f.current();f.tick=160;f.start(QualityTier.SIMPLE);
        assertTrue(old.finished(f.tick));assertSame(aura,f.registry.effect(A,EffectKind.AURA).orElseThrow());assertEquals(2,f.registry.activeCount());assertEquals(1,f.schedules);
        f.reference.stop(A);assertSame(aura,f.registry.effect(A,EffectKind.AURA).orElseThrow());assertTrue(f.driver.running());
        f.auras.stop(A);assertFalse(f.driver.running());assertEquals(1,f.cancels);
    }

    @Test void oneGlobalPlannerAndTaskServeRingAuraAndReferenceWithinAllBudgets() {
        var f=new ReferenceFixture(Map.of("maxCallsPerTick",60,"maxCallsPerViewerPerTick",30),Map.of());
        f.ring(A);f.auras.start(A,QualityTier.BALANCED,f.tick);f.start(QualityTier.HEAVY);f.canView.add(B);
        for(int age=0;age<200;age++) {
            f.tick=100+age;f.run();assertEquals(age+1,f.plans);assertEquals(1,f.schedules);assertEquals(3,f.requests.size());
            assertTrue(f.plan.placements()<=60);assertTrue(f.sendsTo(A)<=30);assertTrue(f.sendsTo(B)<=30);f.account();
        }
    }

    @Test void eachTierStaysBelowItsCeilingAndFullPipelineAccountsEveryPlacement() {
        for(var tier:QualityTier.values()) {
            var f=new ReferenceFixture();f.start(tier);int cap=new int[]{24,48,72}[tier.ordinal()];
            for(int age=0;age<200;age++){f.tick=100+age;f.run();assertTrue(f.plan.reservedGeneratedPoints()<=cap);assertTrue(f.plan.sourcePoints()<=cap);assertEquals(f.plan.placements(),f.sends.size());f.account();}
        }
    }

    @Test void ownersAndOptedInViewersAreIsolatedAcrossPermissionsRangeAndWorld() {
        var f=new ReferenceFixture();f.start(QualityTier.HEAVY);f.reference.start(B,QualityTier.SIMPLE,f.tick);f.tick=228;f.run();
        assertTrue(f.sendsTo(A)>0);assertTrue(f.sendsTo(B)>0);long own=f.sendsTo(A),other=f.sendsTo(B);
        f.canView.add(B);f.tick=244;f.run();assertEquals(own,f.sendsTo(A));assertEquals(own+other,f.sendsTo(B));
        f.worlds.put(B,W2);f.tick=260;f.run();assertEquals(other,f.sendsTo(B));
        f.noUse.add(A);f.canView.clear();f.tick=276;f.run();assertEquals(0,f.sendsTo(A));f.account();
    }

    @Test void postPlanPermissionRevocationIsFailClosedWithExactAccounting() {
        var f=new ReferenceFixture();f.start(QualityTier.HEAVY);f.canView.add(B);f.tick=228;
        f.afterPlan=()->{f.noUse.add(A);f.canView.clear();};f.run();assertTrue(f.plan.placements()>0);assertTrue(f.sends.isEmpty());
        assertEquals(f.plan.placements(),f.driver.lastReport().placementsInSkippedRecipients());f.account();
    }

    @Test void staleAndForgedAnchorsDoNotAdvanceOrBypassTheIdentityGuard() {
        var f=new ReferenceFixture();f.start(QualityTier.HEAVY);f.tick=220;f.tamper=s->replace(s,s.tick()-1,null,null,null,null,null,null,null,null,null,null);
        f.run();assertEquals(0,f.plans);assertTrue(f.registry.active());f.tamper=java.util.function.UnaryOperator.identity();f.tick=224;f.run();assertEquals(124,f.requests.getFirst().frameOrdinal());assertTrue(f.sends.size()>0);
        f.tick=228;f.tamper=s->replace(s,null,B,null,null,null,null,null,null,null,null,null);f.run();assertFalse(f.registry.active());assertEquals(1,f.registry.rejections(AnchorRejection.OWNER_MISMATCH));
    }

    @Test void failedReadinessOrCapacityDoesNotReplaceExistingEffects() {
        var f=new ReferenceFixture();f.start(QualityTier.SIMPLE);var old=f.current();f.tamper=s->null;
        assertThrows(IllegalArgumentException.class,()->f.start(QualityTier.HEAVY));assertSame(old,f.current());assertEquals(1,f.schedules);
        var g=new ReferenceFixture(Map.of(),Map.of("maxEffectsPerPlayer",1));g.auras.start(A,QualityTier.SIMPLE,g.tick);
        assertThrows(IllegalArgumentException.class,()->g.start(QualityTier.HEAVY));assertEquals(1,g.registry.activeCount());assertEquals(1,g.schedules);
    }

    @Test void duplicateCallbacksAndMissingEmissionTicksCannotCreateExtraTasksOrBursts() {
        var f=new ReferenceFixture();f.start(QualityTier.HEAVY);f.tick=228;f.run();f.run();assertEquals(1,f.plans);
        f.tick=229;f.run();assertEquals(0,f.plan.sourcePoints());assertTrue(f.registry.active());f.account();
        f.tick=276;f.run();assertEquals(3,f.plans);assertEquals(1,f.schedules);assertTrue(f.plan.sourcePoints()<=72);
        f.registry.stopAll();var stale=f.callback;f.tick=280;stale.run();assertEquals(3,f.plans);assertEquals(1,f.cancels);
    }

    @Test void teleportAndDimensionContinueAtFreshBodyPoseWithoutRestartingClock() {
        var f=new ReferenceFixture();f.start(QualityTier.HEAVY);var first=f.draft(A);f.tick=228;f.worlds.put(A,W2);f.position.put(A,new double[]{10000,80,-3000,90,45,-90});
        var next=f.draft(A);assertSame(first.playback(),next.playback());assertEquals(128,next.frameOrdinal());assertEquals(f.ids.worldId(W2),next.worldId());
        for(var b:frame(next).batches())for(int i=0;i<b.size();i++){var p=next.anchor().inverse().toWorld(b.point(i));assertTrue(Math.abs(p.x())<=1.3+1e-9);if(b.style().equals(DemoStyleCatalog.END_ROD))assertEquals(-1,p.z(),1e-10);}
    }

    static final class CommandProbe implements ReferenceCommands.Endpoint {
        boolean use=true,player=true;int starts,stops;QualityTier tier;String reply="";
        public boolean permitted(){return use;}public boolean player(){return player;}public void start(QualityTier t){starts++;tier=t;}public void stop(){stops++;}public String status(){return "reference=off";}public void reply(String s){reply=s;}
    }
    @Test void separateCommandEnforcesExistingPermissionPlayerConfigHeavyAndExactGrammar() {
        var thread=new ThreadGuard();var c=new ReferenceCommands(thread,M8Config.from(Map.of("enabled",true,"heavyAllowed",false)));var p=new CommandProbe();
        p.use=false;c.execute(p,new String[0]);assertEquals(0,p.starts);assertTrue(p.reply.contains("Permission"));p.use=true;p.player=false;c.execute(p,new String[0]);assertEquals(0,p.starts);
        p.player=true;c.execute(p,new String[]{"heavy"});assertEquals(0,p.starts);c.execute(p,new String[]{"balanced"});assertEquals(QualityTier.BALANCED,p.tier);assertEquals(1,p.starts);
        c.execute(p,new String[]{"status"});assertEquals("reference=off",p.reply);c.execute(p,new String[]{"off"});assertEquals(1,p.stops);
        c.execute(p,new String[]{"balanced","extra"});c.execute(p,new String[]{"trail"});assertEquals(1,p.starts);assertEquals(List.of("balanced"),c.suggest(new String[]{"b"}));
        new ReferenceCommands(thread,M8Config.from(Map.of())).execute(p,new String[0]);assertEquals(1,p.starts);
    }

    @Test void paperWiringAddsOnlySeparatePermissionProtectedCommandAndHistoricalSendPath() throws Exception {
        Path root=Path.of(System.getProperty("adapter.root","."));String text=Files.readString(root.resolve("src/main/java/workshop/vfx/paper/bukkit/VfxDemoPlugin.java"));
        assertTrue(text.contains("registerCommand(\"vfxreference\""));assertTrue(text.contains("referenceCommands.execute("));assertTrue(text.contains("referenceCommands.suggest("));
        assertTrue(text.contains("player.spawnParticle(resolved.particle(),x,y,z,0,0.0,0.0,0.0,0.0,resolved.data(),false)"));
        assertFalse(text.contains("world.spawnParticle"));assertFalse(text.contains("runTaskAsynchronously"));
    }
}
