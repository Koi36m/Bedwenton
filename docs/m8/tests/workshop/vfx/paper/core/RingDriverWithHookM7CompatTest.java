// M8 R2 GENERATED COMPATIBILITY TEST: paper-adapter/src/test/java/workshop/vfx/paper/core/RingDriverTest.java (accepted M7,
// ed5fc7b) with 'RingDriver' replaced by 'RingDriverWithHook' and the class renamed. It runs the M7 ring tests, unmodified
// in substance, against the hooked reference driver with no contributor (TickContributor.NONE): M7-only compatibility.
package workshop.vfx.paper.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import workshop.vfx.delivery.*;

class RingDriverWithHookM7CompatTest {
    static final UUID A=new UUID(0,1),B=new UUID(0,2),WORLD=new UUID(0,3);
    static final class Host implements RingDriverWithHook.Host {
        long tick=10, nanos; boolean ready=true; int schedules,cancels,plans;Runnable callback;
        List<UUID> players=List.of(A,B,A);Map<UUID,ViewerFacts> facts=new HashMap<>();List<DeliveryRequest> lastRequests;
        Host() {facts.put(A,new ViewerFacts(true,true,0,0.5,64.1,0.5,true,false));
            facts.put(B,new ViewerFacts(true,true,0,0.5,64.1,0.5,true,false));}
        public long tick(){return tick;} public long nanoTime(){return nanos+=10;}
        public boolean worldReady(UUID world,double x,double y,double z){return ready;}
        public ViewerFacts facts(UUID player){return facts.get(player);}
        public List<UUID> players(){return players;}
        public Runnable schedule(Runnable body){callback=body;schedules++;return ()->cancels++;}
    }
    static final class Fixture {
        final ThreadGuard thread=new ThreadGuard();final Host host=new Host();final DefaultSessionIds ids=new DefaultSessionIds(thread);
        final DemoFixture.Sink sink=new DemoFixture.Sink();final RingDriverWithHook driver;
        Fixture(Map<String,Object> settings) {
            driver=new RingDriverWithHook(thread,host,DemoConfig.from(settings),ids,new SessionStyleHealth(DemoFixture.CATALOGUE,thread),
                DemoFixture.ANY_POINT,sink,(tick,requests,styles,policy)->{
                    host.plans++;host.lastRequests=requests;return DeliveryPlanner.plan(tick,requests,styles,policy);});
        }
        Fixture(){this(Map.of());}
        void start(UUID player){driver.start(player,WORLD,0.5,64.1,0.5,QualityTier.BALANCED);}
    }
    @Test void idleEnableThenOnePlannerForAllRingsAndNoDuplicateTickBudgets() {
        var f=new Fixture();assertFalse(f.driver.running());assertEquals(0,f.host.schedules);
        f.start(A);f.start(B);assertEquals(1,f.host.schedules);f.host.callback.run();
        assertEquals(1,f.host.plans);assertEquals(2,f.host.lastRequests.size());assertEquals(96,f.sink.sends.size());
        f.host.callback.run();assertEquals(1,f.host.plans);assertEquals(96,f.sink.sends.size());
        f.host.tick++;f.host.callback.run();assertEquals(2,f.host.plans);
        assertEquals(10,f.driver.lastReport().planningNanos());assertEquals(10,f.driver.lastReport().emissionNanos());
    }
    @Test void repeatedStartReplacesAndDoesNotMultiplyTasksAtCap() {
        var f=new Fixture(Map.of("maxActiveEffects",1));f.start(A);long first=f.driver.ring(A).id();
        for(int n=0;n<5;n++)f.start(A);
        assertEquals(1,f.driver.activeCount());assertEquals(1,f.host.schedules);assertTrue(f.driver.ring(A).id()>first);
        assertThrows(IllegalArgumentException.class,()->f.start(B));assertEquals(1,f.driver.activeCount());
    }
    @Test void ownerFirstAndDuplicateEnumerationCannotReachM6() {
        var f=new Fixture(Map.of("maxViewersPerEffect",1));f.ids.viewerId(B);f.start(A);
        f.host.facts.put(B,new ViewerFacts(true,true,0,0.5,64.1,0.5,true,true));
        f.host.callback.run();var request=f.host.lastRequests.getFirst();
        assertEquals(1,request.viewerCount());assertEquals(f.ids.viewerId(A),request.viewer(0));
        f.host.tick++;f.host.facts.put(A,new ViewerFacts(true,true,0,0.5,64.1,0.5,false,false));
        f.host.callback.run();assertEquals(f.ids.viewerId(B),f.host.lastRequests.getFirst().viewer(0));
    }
    @Test void permissionAndWorldEligibilityArePerRingAndRechecked() {
        var f=new Fixture();f.start(A);f.start(B);f.host.callback.run();
        assertEquals(48,f.sink.count(f.ids.viewerId(A)));assertEquals(48,f.sink.count(f.ids.viewerId(B)));
        f.host.tick++;f.host.facts.put(B,new ViewerFacts(true,true,0,0.5,64.1,0.5,true,true));f.host.callback.run();
        assertEquals(144,f.sink.count(f.ids.viewerId(B)));
        f.host.tick++;f.host.facts.put(B,new ViewerFacts(true,true,0,0.5,64.1,0.5,false,false));f.host.callback.run();
        assertEquals(144,f.sink.count(f.ids.viewerId(B)));assertEquals(2,f.driver.activeCount());
    }
    @Test void cancelledCallbackAndPreviousGenerationNeverEmit() {
        var f=new Fixture();f.start(A);Runnable old=f.host.callback;f.driver.stop(A);f.driver.stop(A);
        old.run();assertEquals(0,f.host.plans);assertEquals(1,f.host.cancels);
        f.start(B);old.run();assertEquals(0,f.host.plans);f.host.callback.run();assertEquals(1,f.host.plans);
        f.driver.close();f.host.tick++;f.host.callback.run();assertEquals(1,f.host.plans);
        assertThrows(IllegalStateException.class,()->f.start(A));assertFalse(f.driver.running());
    }
    @Test void unloadAndMissingWorldStopOnlyAffectedRings() {
        var f=new Fixture();f.start(A);f.start(B);f.driver.stopWorld(new UUID(0,999));assertEquals(2,f.driver.activeCount());
        f.driver.stopWorld(WORLD);assertEquals(0,f.driver.activeCount());assertEquals(1,f.host.cancels);
        f.start(A);f.host.ready=false;f.host.callback.run();assertEquals(0,f.driver.activeCount());assertEquals(0,f.host.plans);
    }
    @Test void expiryAndOwnerOfflineStopAndCancel() {
        var f=new Fixture(Map.of("ringLifetimeTicks",20));f.start(A);f.host.tick=30;f.host.callback.run();
        assertEquals(0,f.driver.activeCount());assertEquals(0,f.host.plans);
        f.host.tick=40;f.start(A);f.host.facts.remove(A);f.host.callback.run();assertFalse(f.driver.running());
    }
    @Test void teleportWorldChangeAndInvalidAnchorsFailClosed() {
        var f=new Fixture();f.start(A);
        assertFalse(f.driver.leavesAnchor(A,WORLD,0.5,64.1,32.5));
        assertTrue(f.driver.leavesAnchor(A,WORLD,0.5,64.1,32.5001));
        assertTrue(f.driver.leavesAnchor(A,new UUID(0,8),0.5,64.1,0.5));
        assertTrue(f.driver.leavesAnchor(A,WORLD,Double.NaN,64.1,0.5));
        assertThrows(IllegalArgumentException.class,()->f.driver.start(B,WORLD,Double.NaN,0,0,QualityTier.SIMPLE));
        f.host.ready=false;assertThrows(IllegalArgumentException.class,()->f.start(B));assertEquals(1,f.driver.activeCount());
        f.host.facts.put(A,new ViewerFacts(true,true,1,0.5,64.1,0.5,true,true));f.host.callback.run();assertFalse(f.driver.running());
    }
    @Test void falsePrimaryRefusesTickAndCommandsBeforeHostAccess() {
        var guard=new ThreadGuard(()->false);var host=new Host();
        var driver=new RingDriverWithHook(guard,host,DemoConfig.from(Map.of()),new DefaultSessionIds(guard),
            new SessionStyleHealth(DemoFixture.CATALOGUE,guard),DemoFixture.ANY_POINT,new DemoFixture.Sink());
        assertThrows(IllegalStateException.class,driver::tick);
        assertThrows(IllegalStateException.class,()->driver.start(A,WORLD,0,0,0,QualityTier.SIMPLE));assertEquals(0,host.plans);
    }
    @Test void styleFailuresPersistAcrossDriverTicksAndNextPlanExcludesStyle() {
        var f=new Fixture();f.sink.alwaysFail=DemoFixture.END_ROD;f.start(A);f.host.callback.run();
        assertEquals(3,f.driver.lastReport().failedCalls());assertEquals(1,f.driver.lastReport().stylesDisabledThisTick().size());
        f.host.tick++;f.host.callback.run();assertEquals(0,f.driver.lastReport().failedCalls());
        assertEquals(32,f.sink.sends.size()); // sixteen accent calls in each of two ticks
    }
}
