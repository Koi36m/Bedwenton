package workshop.vfx.paper.m8;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static workshop.vfx.paper.m8.K1Fixture.*;
import workshop.vfx.delivery.QualityTier;
import workshop.vfx.math.core.Vec3;

/** Additional K1.2 checks against real evaluated production scenes. */
class K12BackSigilTest {
    @Test void entireWhiteFormationIsInVerticalXYPlaneBehindTorsoAtEveryTier() {
        for(var tier:QualityTier.values()){
            var f=new K1Fixture();f.start(tier);
            for(int age=0;age<160;age++){
                f.tick=100+age;
                for(var b:frame(f.draft(A)).batches())if(!b.layer().equals("halo"))for(int i=0;i<b.size();i++){
                    assertEquals(-.85,b.z(i),1e-12);
                    assertTrue(b.y(i)>=64+1.25-1.05-1e-12 && b.y(i)<=64+1.25+1.05+1e-12);
                }
            }
        }
    }

    @Test void backwardOffsetRotatesWithBodyYawRatherThanWorldDirections() {
        for(float yaw:new float[]{0,45,90,180,270,-90,359.5f}){
            var f=new K1Fixture();f.position.put(A,new double[]{7,70,-9,yaw,0,0});f.balanced();
            var ring=frame(f.draft(A)).batches().getFirst();Vec3 sum=Vec3.ZERO;
            for(int i=0;i<ring.size();i++)sum=sum.add(ring.point(i));
            Vec3 centre=sum.mul(1.0/ring.size());double angle=Math.toRadians(yaw);
            assertEquals(7+.85*Math.sin(angle),centre.x(),1e-12);
            assertEquals(71.25,centre.y(),1e-12);
            assertEquals(-9-.85*Math.cos(angle),centre.z(),1e-12);
        }
    }

    @Test void discNeverTurnsEdgeOnWhenInnerPatternRotates() {
        var f=new K1Fixture();f.position.get(A)[3]=37;f.balanced();
        double yaw=Math.toRadians(37);Vec3 forward=new Vec3(-Math.sin(yaw),0,Math.cos(yaw));
        for(int age=0;age<80;age++){
            f.tick=100+age;var ring=frame(f.draft(A)).batches().getFirst();
            Vec3 u=ring.point(4).sub(ring.point(0)),v=ring.point(8).sub(ring.point(0));
            Vec3 normal=u.cross(v).normalizedOr(Vec3.ZERO);
            assertEquals(1,Math.abs(normal.dot(forward)),1e-12);
        }
    }

    @Test void stableOutlineRemainsCompleteAndCircularWhilePatternTurns() {
        var f=new K1Fixture();f.start(QualityTier.HEAVY);
        var initial=frame(f.draft(A)).batches().getFirst().coordinates();
        for(int age=1;age<=160;age++){
            f.tick=100+age;var ring=frame(f.draft(A)).batches().getFirst();
            assertArrayEquals(initial,ring.coordinates());
            for(int i=0;i<ring.size();i++)assertEquals(1.05,Math.hypot(ring.x(i),ring.y(i)-65.25),1e-12);
        }
    }

    @Test void bothStrokesRemainStrictlyContainedThroughoutCompleteRotations() {
        for(var tier:QualityTier.values()){
            var f=new K1Fixture();f.start(tier);
            for(int age=0;age<160;age++){
                f.tick=100+age;var batches=frame(f.draft(A)).batches();
                for(int b=1;b<=2;b++)for(int i=0;i<batches.get(b).size();i++){
                    var stroke=batches.get(b);assertTrue(Math.hypot(stroke.x(i),stroke.y(i)-65.25)<.665*(1.05/.78));
                    assertEquals(-.85,stroke.z(i),1e-12);
                }
            }
        }
    }

    @Test void strokesAreCurvedRatherThanStraightSpokes() {
        var f=new K1Fixture();f.start(QualityTier.HEAVY);var batches=frame(f.draft(A)).batches();
        for(int b=1;b<=2;b++){
            var curve=batches.get(b);Vec3 origin=curve.point(0),chord=curve.point(curve.size()-1).sub(origin);
            double deviation=0;
            for(int i=1;i<curve.size()-1;i++)deviation=Math.max(deviation,curve.point(i).sub(origin).cross(chord).length()/chord.length());
            assertTrue(deviation>.03,"stroke "+b+" must visibly curve");
        }
    }

    @Test void asymmetricPatternCannotHideRotationThroughHalfTurnSymmetry() {
        var f=new K1Fixture();f.start(QualityTier.HEAVY);var first=frame(f.draft(A));f.tick=140;var half=frame(f.draft(A));
        double largestMismatch=0;
        for(int b=1;b<=2;b++)for(int i=0;i<half.batches().get(b).size();i++){
            Vec3 point=half.batches().get(b).point(i);double nearest=Double.POSITIVE_INFINITY;
            for(int a=1;a<=2;a++)for(int j=0;j<first.batches().get(a).size();j++)nearest=Math.min(nearest,point.sub(first.batches().get(a).point(j)).length());
            largestMismatch=Math.max(largestMismatch,nearest);
        }
        assertTrue(largestMismatch>.05);
    }

    @Test void innerStrokesRotateOneQuarterTurnAroundLocalNormal() {
        var f=new K1Fixture();f.balanced();var initial=frame(f.draft(A));f.tick=120;var quarter=frame(f.draft(A));
        for(int b=1;b<=2;b++)for(int i=0;i<initial.batches().get(b).size();i++){
            var p=initial.batches().get(b).point(i);var q=quarter.batches().get(b).point(i);
            assertEquals(-(p.y()-65.25),q.x(),1e-12);assertEquals(65.25+p.x(),q.y(),1e-12);assertEquals(p.z(),q.z(),1e-12);
        }
    }

    @Test void rotationWrapIsContinuousAndRepeatsExactlyAfter80Ticks() {
        var f=new K1Fixture();f.balanced();var first=frame(f.draft(A));f.tick=179;var before=frame(f.draft(A));f.tick=180;var wrap=frame(f.draft(A));f.tick=181;var after=frame(f.draft(A));
        for(int b=1;b<=2;b++)for(int i=0;i<first.batches().get(b).size();i++){
            Vec3 p=wrap.batches().get(b).point(i);assertTrue(p.sub(first.batches().get(b).point(i)).length()<1e-12);
            double previous=p.sub(before.batches().get(b).point(i)).length(),next=after.batches().get(b).point(i).sub(p).length();
            assertEquals(previous,next,1e-12);assertTrue(previous<.053*(1.05/.78));
        }
    }

    @Test void joinedStrokesNeverDuplicateParticlesAtTheirSharedJunction() {
        for(var tier:QualityTier.values()){
            var f=new K1Fixture();f.start(tier);
            for(int age:new int[]{0,20,40,79}){
                f.tick=100+age;List<Vec3> points=new ArrayList<>();
                for(var b:frame(f.draft(A)).batches())if(!b.layer().equals("halo"))for(int i=0;i<b.size();i++)points.add(b.point(i));
                for(int i=0;i<points.size();i++)for(int j=i+1;j<points.size();j++)assertTrue(points.get(i).sub(points.get(j)).length()>.01);
            }
        }
    }

    @Test void haloCoordinatesAreBitIdenticalToCapturedVerifiedK11Baseline() throws Exception {
        Path root=Path.of(System.getProperty("adapter.root","."));var lines=Files.readAllLines(root.resolve("src/test/resources/m8-k1.2/k1.1-halo-coordinates.csv"));
        assertEquals(85,lines.size());
        for(String line:lines.subList(1,lines.size())){
            String[] row=line.split(",");var f=new K1Fixture();f.position.put(A,new double[]{2.5,70,-4,135,45,-90});f.start(QualityTier.valueOf(row[0]));f.tick=100+Long.parseLong(row[1]);
            var halo=frame(f.draft(A)).batches().getLast();int i=Integer.parseInt(row[2]);
            assertEquals(Double.doubleToLongBits(Double.parseDouble(row[3])),Double.doubleToLongBits(halo.x(i)));
            assertEquals(Double.doubleToLongBits(Double.parseDouble(row[4])),Double.doubleToLongBits(halo.y(i)));
            assertEquals(Double.doubleToLongBits(Double.parseDouble(row[5])),Double.doubleToLongBits(halo.z(i)));
        }
    }

    @Test void allGeometryIsReusedAcrossTicksOwnersAndReplacement() {
        var f=new K1Fixture();f.balanced();var first=f.draft(A);f.auras.start(B,QualityTier.BALANCED,f.tick);
        assertSame(first.recipe(),f.draft(B).recipe());var layers=first.recipe().variant(QualityTier.BALANCED).layers();assertEquals(4,layers.size());
        for(int age=1;age<80;age++){
            f.tick=100+age;var next=f.draft(A);assertSame(first.recipe(),next.recipe());
            for(int i=0;i<layers.size();i++)assertSame(layers.get(i).shape(),next.recipe().variant(QualityTier.BALANCED).layers().get(i).shape());
        }
        f.start(QualityTier.HEAVY);assertSame(first.recipe(),f.draft(A).recipe());
    }

    @Test void targetTierAllocationsFitExistingCapsAndAccountingWithOneGlobalPlan() {
        for(var tier:QualityTier.values()){
            var f=new K1Fixture();f.start(tier);f.run();int count=tier==QualityTier.SIMPLE?20:tier==QualityTier.BALANCED?34:44;
            assertEquals(count,f.plan.reservedGeneratedPoints());assertEquals(count,f.sends.size());assertEquals(1,f.plans);assertEquals(1,f.schedules);f.account();
            var batches=frame(f.draft(A)).batches();assertEquals(tier==QualityTier.SIMPLE?12:tier==QualityTier.BALANCED?16:20,batches.get(0).size());
            assertEquals(tier==QualityTier.SIMPLE?4:tier==QualityTier.BALANCED?6:8,batches.get(1).size());assertEquals(batches.get(1).size(),batches.get(2).size());
        }
    }

    @Test void teleportAndNewWorldUseFreshRearPoseWithoutRestartingPatternClock() {
        var f=new K1Fixture();f.balanced();var first=f.draft(A);f.worlds.put(A,W2);f.position.put(A,new double[]{12000,82,-3000,45,0,90});f.tick=123;
        var next=f.draft(A);assertSame(first.recipe(),next.recipe());assertEquals(first.requestId(),next.requestId());assertEquals(23,next.frameOrdinal());assertEquals(f.ids.worldId(W2),next.worldId());
        for(var b:frame(next).batches())for(int i=0;i<b.size();i++)assertTrue(b.point(i).sub(K12GeometryOracle.world(K12GeometryOracle.local(b,i,23),f.position.get(A))).length()<1e-9);
    }
}
