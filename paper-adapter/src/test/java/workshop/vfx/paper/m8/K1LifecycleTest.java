package workshop.vfx.paper.m8;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static workshop.vfx.paper.m8.K1Fixture.*;
import java.util.*;
import workshop.vfx.delivery.*;
import workshop.vfx.paper.core.*;
import workshop.vfx.scene.*;

class K1LifecycleTest {
    @Test void noAutomaticStartAndAuraAloneUsesOneTask(){var f=new K1Fixture();assertEquals(0,f.schedules);assertFalse(f.driver.running());assertEquals(0,f.registry.activeCount());f.balanced();f.run();assertEquals(1,f.schedules);assertEquals(1,f.plans);assertEquals(18,f.sends.size());}
    @Test void allTierReplacementsAreSingleRegistrationAndDisposeOld(){var f=new K1Fixture();long last=-1;for(var tier:List.of(QualityTier.SIMPLE,QualityTier.BALANCED,QualityTier.HEAVY,QualityTier.SIMPLE)){var old=f.registry.effect(A,EffectKind.AURA);long id=f.start(tier);assertTrue(id>last);last=id;old.ifPresent(e->assertTrue(e.finished(f.tick)));assertEquals(1,f.registry.activeCount());assertEquals(tier,f.current().tier());}assertEquals(1,f.schedules);}
    @Test void failedStartPreservesExistingAuraAndTask(){var f=new K1Fixture();f.balanced();var old=f.current();f.tamper=s->null;assertThrows(IllegalArgumentException.class,()->f.start(QualityTier.HEAVY));assertSame(old,f.current());assertFalse(old.finished(f.tick));assertEquals(1,f.schedules);assertEquals(0,f.cancels);}
    @Test void schedulerFailureLeavesNoRegistrationAndRetryWorks(){var f=new K1Fixture();f.failSchedule=true;assertThrows(IllegalStateException.class,()->f.balanced());assertEquals(0,f.registry.activeCount());assertFalse(f.driver.running());var stale=f.callback;f.failSchedule=false;f.balanced();stale.run();assertEquals(0,f.plans);f.run();assertEquals(1,f.plans);}
    @Test void expirationIsExactEvenAfterSkippedSnapshots(){var f=new K1Fixture(Map.of(),Map.of("auraLifetimeTicks",20));f.balanced();var e=f.current();f.tick=119;f.run();assertEquals(1,f.registry.activeCount());f.tick=120;f.run();assertEquals(0,f.registry.activeCount());assertTrue(e.finished(120));assertEquals(1,f.cancels);assertFalse(f.driver.running());}
    @Test void deathTerminatesAndCancelsBeforePlan(){var f=new K1Fixture();f.balanced();var e=f.current();f.dead.add(A);f.run();assertEquals(0,f.plans);assertTrue(e.finished(f.tick));assertEquals(0,f.registry.activeCount());assertEquals(1,f.cancels);}
    @Test void disconnectTerminatesAndReleasedSessionCannotResume(){var f=new K1Fixture();f.balanced();f.offline.add(A);f.ids.release(A);f.run();assertEquals(0,f.plans);assertFalse(f.registry.active());f.offline.clear();f.step();assertEquals(0,f.plans);}
    @Test void auraOffStopsOnlyAuraAndPreservesRing(){var f=new K1Fixture();f.ring(A);f.balanced();f.auras.stop(A);assertNotNull(f.driver.ring(A));assertEquals(0,f.registry.activeCount());assertTrue(f.driver.running());f.run();assertEquals(48,f.sends.size());f.driver.stop(A);assertEquals(1,f.cancels);}
    @Test void stopOwnerAndStopAllCleanUpOnlyIntendedOwners(){var f=new K1Fixture();f.balanced();f.auras.start(B,QualityTier.SIMPLE,f.tick);f.ring(A);f.driver.stop(A);f.registry.stopOwner(A);assertEquals(1,f.registry.activeCount());assertTrue(f.driver.running());f.driver.stopAll();f.registry.stopAll();assertEquals(0,f.registry.activeCount());assertFalse(f.driver.running());assertEquals(1,f.cancels);}
    @Test void shutdownDisposesAndForbidsAllLaterStarts(){var f=new K1Fixture();f.balanced();var e=f.current();var stale=f.callback;f.driver.close();f.driver.close();e.dispose();assertTrue(e.finished(f.tick));assertTrue(e.contribute(f.tick,f.snapshot(A,f.tick)).isEmpty());assertThrows(IllegalStateException.class,()->f.balanced());stale.run();assertEquals(0,f.plans);assertEquals(1,f.cancels);}
    @Test void duplicateTicksAndStoppedCallbacksCannotPlanAgain(){var f=new K1Fixture();f.balanced();f.run();f.run();assertEquals(1,f.plans);var stale=f.callback;f.auras.stop(A);f.tick++;f.balanced();stale.run();assertEquals(1,f.plans);f.run();assertEquals(2,f.plans);assertEquals(2,f.schedules);}
    @Test void thousandsOfReplacementsAndStopsHaveBoundedRegistrationsAndTasks(){var f=new K1Fixture();for(int i=0;i<1000;i++){f.start(QualityTier.HEAVY);assertEquals(1,f.registry.activeCount());f.auras.stop(A);assertEquals(0,f.registry.activeCount());}assertEquals(f.schedules,f.cancels);assertFalse(f.driver.running());}
}
