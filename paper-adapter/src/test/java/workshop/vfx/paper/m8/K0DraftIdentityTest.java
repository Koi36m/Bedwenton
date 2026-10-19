package workshop.vfx.paper.m8;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static workshop.vfx.paper.m8.K0Fixture.*;
import java.util.*;
import workshop.vfx.delivery.*;
import workshop.vfx.math.core.*;
import workshop.vfx.math.time.*;

class K0DraftIdentityTest {
    static AnchorSnapshot change(AnchorSnapshot s,UUID owner,long viewer,long tick,UUID world,int wid,double x,double y,double z,float body,float head,
                                  int min,int max,boolean chunk,boolean online,boolean valid,boolean dead){
        return new AnchorSnapshot(tick,owner,viewer,world,wid,x,y,z,body,head,s.pitchDegrees(),min,max,chunk,online,valid,dead,0);
    }
    static AnchorSnapshot atTick(AnchorSnapshot s,long tick){return change(s,s.owner(),s.ownerViewerId(),tick,s.world(),s.worldId(),s.x(),s.y(),s.z(),s.bodyYawDegrees(),s.headYawDegrees(),s.minHeight(),s.maxHeight(),s.chunkLoaded(),s.online(),s.valid(),s.dead());}
    @Test void completeOrderedReasonTableAndHeightBoundaries(){
        var f=new K0Fixture();long a=f.ids.viewerId(A),b=f.ids.viewerId(B);int w=f.ids.worldId(WORLD),w2=f.ids.worldId(WORLD2);
        var good=f.honest(A,f.tick);var binding=new AnchorBinding(A,a);
        record Case(AnchorSnapshot snapshot,AnchorState state,AnchorRejection reason){}
        List<Case> cases=new ArrayList<>();cases.add(new Case(good,AnchorState.READY,AnchorRejection.NONE));
        cases.add(new Case(null,AnchorState.TERMINATE,AnchorRejection.OWNER_UNRESOLVED));
        cases.add(new Case(change(good,B,b,0,WORLD,w,Double.NaN,400,0,0,0,0,0,false,false,false,true),AnchorState.TERMINATE,AnchorRejection.OWNER_MISMATCH));
        cases.add(new Case(change(good,A,b,0,WORLD,w,0,64,0,0,0,-64,320,true,true,true,false),AnchorState.TERMINATE,AnchorRejection.VIEWER_ID_MISMATCH));
        for(int state=0;state<3;state++)cases.add(new Case(change(good,A,a,f.tick,WORLD,w,0,64,0,0,0,-64,320,true,state!=0,state!=1,state==2),AnchorState.TERMINATE,AnchorRejection.OWNER_GONE));
        cases.add(new Case(atTick(good,f.tick-1),AnchorState.SKIP_TICK,AnchorRejection.STALE_OR_FUTURE));
        cases.add(new Case(atTick(good,f.tick+1),AnchorState.SKIP_TICK,AnchorRejection.STALE_OR_FUTURE));
        cases.add(new Case(atTick(good,Long.MIN_VALUE),AnchorState.SKIP_TICK,AnchorRejection.STALE_OR_FUTURE));
        for(Object[] pair:new Object[][]{{WORLD2,w},{WORLD,w2},{WORLD,-1},{null,w},{WORLD,999}})
            cases.add(new Case(change(good,A,a,f.tick,(UUID)pair[0],(Integer)pair[1],0,64,0,0,0,-64,320,true,true,true,false),AnchorState.SKIP_TICK,AnchorRejection.WORLD_INCONSISTENT));
        for(double y:new double[]{Math.nextDown(-64.0),320})cases.add(new Case(change(good,A,a,f.tick,WORLD,w,0,y,0,0,0,-64,320,true,true,true,false),AnchorState.SKIP_TICK,AnchorRejection.OUTSIDE_WORLD_HEIGHT));
        cases.add(new Case(change(good,A,a,f.tick,WORLD,w,0,64,0,0,0,320,320,true,true,true,false),AnchorState.SKIP_TICK,AnchorRejection.OUTSIDE_WORLD_HEIGHT));
        cases.add(new Case(change(good,A,a,f.tick,WORLD,w,0,-64,0,0,0,-64,320,true,true,true,false),AnchorState.READY,AnchorRejection.NONE));
        cases.add(new Case(change(good,A,a,f.tick,WORLD,w,0,64,0,0,0,-64,320,false,true,true,false),AnchorState.SKIP_TICK,AnchorRejection.CHUNK_NOT_LOADED));
        for(Case c:cases){var verdict=f.rules.validate(c.snapshot(),binding,f.tick);assertEquals(c.state(),verdict.state());assertEquals(c.reason(),verdict.reason());}
    }
    @Test void everyNonfinitePositionAndYawIsRejectedBeforeEffectInvocation(){
        for(int axis=0;axis<5;axis++)for(double invalid:new double[]{Double.NaN,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY}){
            var f=new K0Fixture();f.aura(A);var effect=f.current(A,EffectKind.AURA);final int n=axis;
            f.tamper=s->change(s,A,s.ownerViewerId(),s.tick(),s.world(),s.worldId(),n==0?invalid:s.x(),n==1?invalid:s.y(),n==2?invalid:s.z(),n==3?(float)invalid:0,n==4?(float)invalid:0,-64,320,true,true,true,false);
            f.run();assertEquals(0,effect.contributions);assertEquals(0,f.plans);assertEquals(1,f.registry.rejections(AnchorRejection.NON_FINITE));
        }
    }
    @Test void ownerAndViewerSwapCannotRedirectToUseOnlyNonowner(){
        for(boolean swapOwner:new boolean[]{false,true}){
            var f=new K0Fixture();f.aura(A);long b=f.ids.viewerId(B);var effect=f.current(A,EffectKind.AURA);f.run();
            f.tamper=s->change(s,swapOwner?B:A,b,s.tick(),s.world(),s.worldId(),s.x(),s.y(),s.z(),0,0,-64,320,true,true,true,false);f.step();
            assertEquals(0,f.sendsTo(B));assertEquals(27,f.sendsTo(A));assertEquals(0,f.registry.activeCount());assertTrue(effect.disposed());
            assertEquals(1,f.registry.rejections(swapOwner?AnchorRejection.OWNER_MISMATCH:AnchorRejection.VIEWER_ID_MISMATCH));
        }
    }
    @Test void staleFutureRecoverThenTwentyConsecutiveSkipsTerminate(){
        for(long offset:new long[]{-1,1,-100}){
            var f=new K0Fixture();f.aura(A);var effect=f.current(A,EffectKind.AURA);f.run();
            f.tamper=s->atTick(s,s.tick()+offset);f.step();assertEquals(1,f.plans);assertTrue(f.driver.running());
            assertEquals(Map.of(AnchorRejection.STALE_OR_FUTURE,1L),f.registry.rejections(A,EffectKind.AURA));
            f.tamper=null;f.step();assertEquals(54,f.sendsTo(A));
            f.tamper=s->atTick(s,s.tick()+offset);for(int i=0;i<19;i++)f.step();assertEquals(1,f.registry.activeCount());
            f.step();assertEquals(0,f.registry.activeCount());assertFalse(f.driver.running());assertEquals(1,effect.disposals);
        }
    }
    @Test void worldHeightAndChunkErrorsSkipWithoutCreatingMappings(){
        for(int variant=0;variant<3;variant++){
            var f=new K0Fixture();f.aura(A);f.run();final int v=variant;
            f.tamper=s->change(s,A,s.ownerViewerId(),s.tick(),v==0?WORLD2:s.world(),s.worldId(),s.x(),v==1?400:s.y(),s.z(),0,0,-64,320,v!=2,true,true,false);
            f.step();assertEquals(1,f.plans);assertEquals(1,f.registry.activeCount());assertEquals(0,f.sendsTo(B));assertNull(f.ids.world(99));
        }
    }
    @Test void releasedOrReplayedViewerBindingTerminatesWithoutResurrection(){
        for(boolean replay:new boolean[]{false,true}){
            var f=new K0Fixture();f.aura(A);long id=f.ids.viewerId(A);f.run();f.ids.release(A);
            if(replay)f.tamper=s->change(s,A,id,s.tick(),s.world(),s.worldId(),s.x(),s.y(),s.z(),0,0,-64,320,true,true,true,false);
            f.step();assertEquals(0,f.registry.activeCount());assertTrue(f.ids.existingViewerId(A).isEmpty());
            assertNull(f.ids.viewer(id));assertEquals(1,f.registry.rejections(AnchorRejection.VIEWER_ID_MISMATCH));
        }
    }
    @Test void registryDropsEachForgedDraftDimensionAndDisposesOnce(){
        for(int variant=0;variant<5;variant++){
            var f=new K0Fixture();f.aura(A);long b=f.ids.viewerId(B);int world2=f.ids.worldId(WORLD2);var effect=f.current(A,EffectKind.AURA);final int v=variant;
            effect.producer=(t,s)->Optional.of(new EffectDraft(v==0?EffectKind.TRAIL:EffectKind.AURA,v==1?effect.id+1:effect.id,
                v==2?B:A,v==3?b:effect.binding.ownerViewerId(),v==4?world2:s.worldId(),Pose.ofTranslation(new Vec3(s.x(),s.y(),s.z())),probeRecipe(),QualityTier.BALANCED,
                Playback.start(TimePoint.gameTick(effect.start)),TimePoint.gameTick(t),t-effect.start));
            f.run();assertEquals(0,f.plans);assertEquals(1,f.registry.identityViolations());assertEquals(0,f.registry.activeCount());assertEquals(1,effect.disposals);assertEquals(0,f.sendsTo(B));
        }
    }
    @Test void driverRejectsBadOwnerRangeWorldAndDuplicateIdsBeforePlanning(){
        List<EffectDraft> next=new ArrayList<>();
        TickContributor rogue=new TickContributor(){public List<EffectDraft> drafts(long tick){return next;}public boolean active(){return true;}public int activeCount(){return 1;}public void close(){}};
        var f=new K0Fixture(Map.of("maxActiveEffects",8),2,rogue);long a=f.ids.viewerId(A),b=f.ids.viewerId(B);int w=f.ids.worldId(WORLD);long id=DynamicEffectRegistry.FIRST_ID;
        java.util.function.Function<Object[],EffectDraft> draft=p->new EffectDraft(EffectKind.AURA,(Long)p[0],(UUID)p[1],(Long)p[2],(Integer)p[3],Pose.ofTranslation(new Vec3(0.5,64,0.5)),probeRecipe(),QualityTier.BALANCED,Playback.start(TimePoint.gameTick(f.tick)),TimePoint.gameTick(f.tick),0);
        next.add(draft.apply(new Object[]{id,A,b,w}));next.add(draft.apply(new Object[]{id+1,B,a,w}));next.add(draft.apply(new Object[]{0L,A,a,w}));next.add(draft.apply(new Object[]{id+2,A,a,99}));
        next.add(draft.apply(new Object[]{id+4,A,a,w}));next.add(draft.apply(new Object[]{id+4,A,a,w}));
        f.driver.ensureRunning();f.run();assertEquals(5,f.driver.rejectedDrafts());assertEquals(List.of(id+4),f.lastRequests.stream().map(DeliveryRequest::id).toList());
        assertEquals(27,f.sendsTo(A));assertEquals(0,f.sendsTo(B));assertNull(f.ids.world(99));
    }
    @Test void driverDropsMalformedAndOversizeContributionsWithoutEmission(){
        List<EffectDraft> next=new ArrayList<>();
        TickContributor rogue=new TickContributor(){public List<EffectDraft> drafts(long tick){return next;}public boolean active(){return true;}public int activeCount(){return 1;}public void close(){}};
        var f=new K0Fixture(Map.of("maxActiveEffects",2),2,rogue);next.add(null);f.driver.ensureRunning();f.run();assertEquals(1,f.driver.rejectedDrafts());assertEquals(0,f.plans);
        next.add(null);next.add(null);f.tick++;f.run();assertEquals(4,f.driver.rejectedDrafts());assertEquals(0,f.plans);assertTrue(f.driver.running());
    }
    @Test void knownWorldAndOwnerDraftsKeepContextAndOwnerIsolation(){
        var f=new K0Fixture();f.aura(A);f.aura(B);f.run();assertEquals(1,f.plans);assertEquals(2,f.lastRequests.size());assertEquals(27,f.sendsTo(A));assertEquals(27,f.sendsTo(B));
        assertEquals(0,f.driver.rejectedDrafts());assertEquals(0,f.registry.identityViolations());
    }
}
