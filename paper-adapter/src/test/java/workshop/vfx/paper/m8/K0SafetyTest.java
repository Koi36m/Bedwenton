package workshop.vfx.paper.m8;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static workshop.vfx.paper.m8.K0Fixture.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import workshop.vfx.delivery.QualityTier;

class K0SafetyTest {
    @Test void configurationDefaultsDisabledAndPreservesAllHardBounds(){
        var defaults=M8Config.from(Map.of());assertFalse(defaults.enabled());assertEquals(QualityTier.SIMPLE,defaults.defaultTier());
        assertEquals(2,defaults.maxEffectsPerPlayer());assertEquals(20,defaults.invalidAnchorTicksToTerminate());
        for(String key:List.of("maxEffectsPerPlayer","auraLifetimeTicks","trailLifetimeTicks","invalidAnchorTicksToTerminate")){
            int min=key.equals("maxEffectsPerPlayer")||key.equals("invalidAnchorTicksToTerminate")?1:20;
            int max=key.equals("maxEffectsPerPlayer")?3:key.equals("invalidAnchorTicksToTerminate")?200:12000;
            for(int n:new int[]{min,max})assertDoesNotThrow(()->M8Config.from(Map.of(key,n)));
            for(int n:new int[]{min-1,max+1})assertThrows(IllegalArgumentException.class,()->M8Config.from(Map.of(key,n)));
            for(Object bad:new Object[]{"2",2.5,Long.MAX_VALUE,Double.NaN})assertThrows(IllegalArgumentException.class,()->M8Config.from(Map.of(key,bad)));
        }
        for(double step:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY,0.5,Math.nextUp(64.0)})
            assertThrows(IllegalArgumentException.class,()->M8Config.from(Map.of("trailMaxStep",step)));
        for(double step:new double[]{Math.nextUp(0.5),64})assertDoesNotThrow(()->M8Config.from(Map.of("trailMaxStep",step)));
        for(String flag:List.of("enabled","heavyAllowed"))assertThrows(IllegalArgumentException.class,()->M8Config.from(Map.of(flag,"false")));
        assertThrows(IllegalArgumentException.class,()->M8Config.from(Map.of("defaultTier","other")));
    }
    @Test void disabledRoutingLeavesM7GrammarAndSuggestionsToOriginalParser(){
        for(String kind:List.of("ring","stop","status","aura","trail","activate"))assertFalse(K0CommandRouting.handles(false,new String[]{kind}));
        for(String kind:List.of("ring","stop","status"))assertFalse(K0CommandRouting.handles(true,new String[]{kind}));
        for(String kind:List.of("aura","trail","activate"))assertTrue(K0CommandRouting.handles(true,new String[]{kind}));
        assertTrue(K0CommandRouting.unavailable(false,true).contains("Permission required"));
        assertTrue(K0CommandRouting.unavailable(true,false).contains("requires a player"));
        assertTrue(K0CommandRouting.unavailable(true,true).contains("not implemented"));
    }
    @Test void failedStartCallbackCannotRunInAnyLaterGeneration(){
        var f=new K0Fixture();f.failNextSchedule=true;
        assertThrows(IllegalStateException.class,()->f.aura(A));Runnable stale=f.callback;stale.run();assertEquals(0,f.plans);
        f.aura(B);stale.run();assertEquals(0,f.plans);f.run();assertEquals(1,f.plans);
        f.registry.stopAll();f.tick++;f.aura(A);stale.run();assertEquals(1,f.plans);f.run();assertEquals(2,f.plans);
    }
    @Test void aliasingFactoryMustNotDisposeAnyExistingEffect(){
        var f=new K0Fixture(Map.of("maxActiveEffects",3),2);f.aura(A);f.aura(B);
        var old=f.current(A,EffectKind.AURA);var other=f.current(B,EffectKind.AURA);
        assertThrows(IllegalStateException.class,()->f.registry.start(A,EffectKind.AURA,(id,b)->old));
        assertThrows(IllegalStateException.class,()->f.registry.start(A,EffectKind.AURA,(id,b)->other));
        assertFalse(old.disposed());assertFalse(other.disposed());assertEquals(2,f.registry.activeCount());f.run();assertEquals(54,f.sends.size());
    }
    @Test void successfulReplacementCommitsBeforeDisposalAndNeverDoubleDisposes(){
        var f=new K0Fixture(Map.of("maxActiveEffects",1),2);f.aura(A);var old=f.current(A,EffectKind.AURA);
        old.disposal=()->assertNotSame(old,f.registry.effect(A,EffectKind.AURA).orElseThrow());
        f.aura(A);var next=f.current(A,EffectKind.AURA);assertEquals(1,old.disposals);assertEquals(0,next.disposals);
        f.registry.stopOwner(A);f.registry.stopOwner(A);f.driver.close();f.driver.close();
        assertEquals(1,next.disposals);assertEquals(1,old.disposals);assertEquals(1,f.cancels);
    }
    @Test void reentrantFactoryCannotDestroyTheOldRegistration(){
        var f=new K0Fixture();f.aura(A);var old=f.current(A,EffectKind.AURA);
        assertThrows(IllegalStateException.class,()->f.registry.start(A,EffectKind.AURA,(id,b)->{f.registry.stopOwner(A);return null;}));
        assertSame(old,f.current(A,EffectKind.AURA));assertFalse(old.disposed());f.run();assertEquals(27,f.sends.size());
    }
    @Test void contributionExceptionsAreContainedAndOtherEffectsSurvive(){
        var f=new K0Fixture();f.aura(A);f.aura(B);var a=f.current(A,EffectKind.AURA);
        a.producer=(t,s)->{throw new IllegalStateException("injected update failure");};f.run();
        assertEquals(1,f.registry.updateFailures());assertEquals(1,a.disposals);assertEquals(1,f.registry.activeCount());
        assertEquals(27,f.sendsTo(B));assertTrue(f.driver.running());
    }
    @Test void shutdownCancelsEvenIfAnInjectedContributorViolatesCloseContract(){
        TickContributor broken=new TickContributor(){public List<EffectDraft> drafts(long t){return List.of();}public boolean active(){return true;}public int activeCount(){return 1;}public void close(){throw new IllegalStateException("close failure");}};
        var f=new K0Fixture(Map.of(),2,broken);f.ring(A);Runnable old=f.callback;
        assertThrows(IllegalStateException.class,f.driver::close);assertFalse(f.driver.running());assertEquals(0,f.driver.activeCount());assertEquals(1,f.cancels);
        f.tick++;old.run();assertEquals(0,f.plans);assertThrows(IllegalStateException.class,()->f.ring(A));
    }
    @Test void entryPointsRejectOtherThreadsBeforeMutation() throws Exception {
        var f=new K0Fixture();f.aura(A);AtomicReference<Throwable> failure=new AtomicReference<>();
        List<Runnable> calls=List.of(()->f.aura(B),()->f.registry.stopOwner(A),()->f.registry.stopAll(),()->f.registry.drafts(f.tick),
            ()->f.registry.activeCount(),()->f.registry.close(),()->f.driver.ensureRunning(),()->f.driver.effectsChanged(),
            ()->f.driver.hasCapacityFor(false),()->f.driver.rejectedDrafts(),()->f.rules.validate(null,new AnchorBinding(A,0),f.tick));
        for(Runnable call:calls){failure.set(null);Thread other=new Thread(()->{try{call.run();}catch(Throwable bad){failure.set(bad);}});other.start();other.join();assertInstanceOf(IllegalStateException.class,failure.get());}
        assertEquals(1,f.registry.activeCount());assertTrue(f.driver.running());
    }
    @Test void boundedReplacementAndStopCyclesFreeCapacityWithoutTaskGrowth(){
        var f=new K0Fixture(Map.of("maxActiveEffects",2),2);
        for(int n=0;n<1000;n++){
            f.aura(A);f.aura(A);assertEquals(1,f.registry.activeCount());f.ring(B);assertEquals(2,f.driver.activeCount()+f.registry.activeCount());
            assertThrows(IllegalArgumentException.class,()->f.trail(B));f.registry.stopOwner(A);f.driver.stop(B);
            assertEquals(0,f.registry.activeCount());assertFalse(f.driver.running());f.tick++;
        }
        assertEquals(f.schedules,f.cancels);assertEquals(0,f.registry.identityViolations());
    }
}
