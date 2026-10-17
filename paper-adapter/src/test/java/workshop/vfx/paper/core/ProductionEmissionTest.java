package workshop.vfx.paper.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import workshop.vfx.delivery.*;
import workshop.vfx.scene.*;

class ProductionEmissionTest {
    @Test void recipientPrefixesMatchIndependentSelectionFormulaBitwise() {
        Random random=new Random(7003);
        for(int n=0;n<240;n++) {
            long tick=random.nextInt(500);QualityTier tier=QualityTier.values()[n%3];
            var q=DemoFixture.ring(1,tier,0,0.5,tick,0,1,2);
            int cap=random.nextInt(73),placement=random.nextInt(220),viewer=random.nextInt(73);
            var policy=new DeliveryPolicy(1,3,3,1152,0,placement,0,placement,viewer,cap,cap,cap);
            Set<StyleKey> styles=n%4==0?Set.of(DemoFixture.DUST):DemoFixture.CATALOGUE;
            var plan=DeliveryPlanner.plan(tick,List.of(q),styles,policy);var d=plan.deliveries().getFirst();
            var sink=new DemoFixture.Sink();
            var report=new DefaultPlanEmitter().emit(plan,Map.of(1L,DemoFixture.context(1,0,0,0.5)),
                new SessionStyleHealth(styles),(c,v)->Optional.empty(),DemoFixture.ANY_POINT,sink,new EmissionLimits(4096,1024));
            List<DemoFixture.Send> expected=new ArrayList<>();
            for(int r=0;r<d.recipientCount();r++) {
                int left=d.recipientPoints(r);
                for(int p=0;p<d.orderedBatchCount()&&left>0;p++) {
                    var batch=d.frame().batches().get(d.orderedBatch(p));int kept=d.kept(p),size=batch.size();
                    int width=(size+kept-1)/kept,offset=Math.floorMod(tick,width);
                    for(int j=0;j<kept&&left>0;j++,left--) {
                        int index=offset+(int)((long)j*size/kept);
                        expected.add(new DemoFixture.Send(d.recipient(r),0,batch.style(),batch.x(index),batch.y(index),batch.z(index)));
                    }
                }
            }
            assertEquals(expected,sink.sends,"scenario "+n);assertEquals(plan.placements(),report.succeededCalls());
            for(int i=0;i<expected.size();i++) {
                assertEquals(Double.doubleToRawLongBits(expected.get(i).x()),Double.doubleToRawLongBits(sink.sends.get(i).x()));
                assertEquals(Double.doubleToRawLongBits(expected.get(i).y()),Double.doubleToRawLongBits(sink.sends.get(i).y()));
                assertEquals(Double.doubleToRawLongBits(expected.get(i).z()),Double.doubleToRawLongBits(sink.sends.get(i).z()));
            }
        }
    }
    @Test void skippedPointsAreNotPromotedBeyondRecipientPrefix() {
        var plan=DeliveryPlanner.plan(1,List.of(DemoFixture.ring(1,QualityTier.HEAVY,0,0.5,1,0)),DemoFixture.CATALOGUE,
            new DeliveryPolicy(1,1,1,1152,0,5,0,5,5,72,72,72));
        var d=plan.deliveries().getFirst();var b=d.frame().batches().get(d.orderedBatch(0));
        double first=b.x(d.selectedIndex(0,0));var sink=new DemoFixture.Sink();
        var report=new DefaultPlanEmitter().emit(plan,Map.of(1L,DemoFixture.context(1,0,0,0.5)),new SessionStyleHealth(DemoFixture.CATALOGUE),
            (c,v)->Optional.empty(),(w,x,y,z)->x==first?Optional.of(SkipReason.POINT_OUTSIDE_WORLD):Optional.empty(),sink,new EmissionLimits(4096,1024));
        assertEquals(4,sink.sends.size());assertEquals(1L,report.skippedPoints().get(SkipReason.POINT_OUTSIDE_WORLD));
        assertEquals(b.x(d.selectedIndex(0,1)),sink.sends.getFirst().x());
    }
    @Test void limitsCountFailuresAndApplyAcrossRecipients() {
        var plan=DeliveryPlanner.plan(0,List.of(DemoFixture.ring(1,QualityTier.HEAVY,0,0.5,0,0,1)),DemoFixture.CATALOGUE,DemoFixture.roomy());
        var sink=new DemoFixture.Sink();sink.alwaysFail=DemoFixture.END_ROD;
        var report=new DefaultPlanEmitter().emit(plan,Map.of(1L,DemoFixture.context(1,0,0,0.5)),new SessionStyleHealth(DemoFixture.CATALOGUE),
            (c,v)->Optional.empty(),DemoFixture.ANY_POINT,sink,new EmissionLimits(7,5));
        assertEquals(7,report.attemptedCalls());assertEquals(3,report.failedCalls());assertEquals(4,report.succeededCalls());
        assertTrue(sink.count(0)<=2);assertTrue(sink.count(1)<=5);assertEquals(plan.placements(),report.attemptedCalls()+
            report.skippedPoints().values().stream().mapToLong(Long::longValue).sum());
    }
    @Test void allGateReasonsSkipOnlyTheirRecipientAndPreserveAccounting() {
        for(SkipReason reason:List.of(SkipReason.VIEWER_OFFLINE,SkipReason.VIEWER_WORLD_CHANGED,SkipReason.VIEWER_PERMISSION_REVOKED,
            SkipReason.VIEWER_POSITION_INVALID,SkipReason.VIEWER_OUT_OF_RANGE)) {
            var plan=DeliveryPlanner.plan(0,List.of(DemoFixture.ring(1,QualityTier.SIMPLE,0,0.5,0,0,1)),DemoFixture.CATALOGUE,DemoFixture.roomy());
            var sink=new DemoFixture.Sink();var report=new DefaultPlanEmitter().emit(plan,Map.of(1L,DemoFixture.context(1,0,0,0.5)),
                new SessionStyleHealth(DemoFixture.CATALOGUE),(c,v)->v==0?Optional.of(reason):Optional.empty(),DemoFixture.ANY_POINT,sink,new EmissionLimits(4096,1024));
            assertEquals(0,sink.count(0));assertEquals(24,sink.count(1));assertEquals(24,report.placementsInSkippedRecipients());
            assertEquals(1L,report.skippedRecipients().get(reason));assertEquals(plan.placements(),report.attemptedCalls()+report.placementsInSkippedRecipients());
        }
    }
    @Test void demoRecipePreservesCountsRadiiLoopAndStaticAnchor() {
        var recipe=DemoRecipe.create(600);
        for(var tier:QualityTier.values()) {
            int total=switch(tier){case SIMPLE->24;case BALANCED->48;case HEAVY->72;};
            var q=DeliveryRequest.of(1,recipe,tier,0,workshop.vfx.math.core.Pose.ofTranslation(new workshop.vfx.math.core.Vec3(-3.5,64.1,10.5)),
                workshop.vfx.math.time.Playback.start(workshop.vfx.math.time.TimePoint.gameTick(100)),workshop.vfx.math.time.TimePoint.gameTick(143),43,new long[]{0});
            var d=DeliveryPlanner.plan(143,List.of(q),DemoFixture.CATALOGUE,DemoFixture.roomy()).deliveries().getFirst();
            assertEquals(total,d.sourcePoints());assertEquals(total,d.keptPoints());
            for(FrameBatch batch:d.frame().batches())for(int i=0;i<batch.size();i++) {
                assertEquals(64.1,batch.y(i));double x=batch.x(i)+3.5,z=batch.z(i)-10.5;
                assertEquals(batch.style().equals(DemoFixture.END_ROD)?2.0:2.25,Math.hypot(x,z),1e-12);
            }
        }
    }
}
