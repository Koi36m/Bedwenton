package workshop.vfx.paper.m8;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static workshop.vfx.paper.m8.K1Fixture.*;
import workshop.vfx.delivery.QualityTier;

/** Real production pipeline with in-memory host/sink, explicitly NOT a Paper/TPS/MSPT benchmark. */
class K1SyntheticMeasurementsTest {
    private static String stats(long[] values) {
        long[] sorted=values.clone(); Arrays.sort(sorted);
        return "{\"p50\":"+sorted[599]+",\"p95\":"+sorted[1139]+",\"max\":"+sorted[1199]+"}";
    }
    @Test void recordAllTiersAndTwoViewerSustainedPipelineMeasurements() throws Exception {
        StringBuilder json=new StringBuilder("{\"kind\":\"synthetic in-memory production pipeline; NOT Paper, TPS, MSPT or network latency\",\"warmup_ticks\":600,\"measured_ticks\":1200,\"scenarios\":[");
        for(int scenario=0;scenario<4;scenario++) {
            QualityTier tier=scenario==0?QualityTier.SIMPLE:scenario==1?QualityTier.BALANCED:QualityTier.HEAVY;
            var f=new K1Fixture(Map.of(),Map.of("auraLifetimeTicks",12000));
            f.start(tier);
            if(scenario==3){f.auras.start(B,tier,f.tick);f.canView.add(A);f.canView.add(B);}
            long[] planner=new long[1200],emitter=new long[1200],snapshot=new long[1200],total=new long[1200];
            long generated=0,placements=0,ok=0,failed=0;
            for(int i=0;i<1800;i++) {
                double[] p=f.position.get(A);p[0]=Math.sin(i/20.0);p[1]=64+Math.sin(i/5.0);p[3]=i*3;
                long start=System.nanoTime();f.run();long elapsed=System.nanoTime()-start;
                if(i>=600){int k=i-600;var r=f.driver.lastReport();planner[k]=r.planningNanos();emitter[k]=r.emissionNanos();snapshot[k]=f.snapshots.snapshotNanos();total[k]=elapsed;
                    generated+=f.plan.sourcePoints();placements+=r.plannedPlacements();ok+=r.succeededCalls();failed+=r.failedCalls();f.account();}
                f.tick++;
            }
            long expectedPerFrame=scenario==0?8:scenario==1?18:scenario==2?24:48;
            assertEquals(expectedPerFrame*1200,generated);assertEquals(generated*(scenario==3?2:1),placements);
            assertEquals(placements,ok);assertEquals(0,failed);assertEquals(1,f.schedules);
            if(scenario>0)json.append(',');
            json.append("{\"scenario\":\"").append(scenario==3?"two-heavy-auras-two-viewers":tier.name()).append("\",\"generated_per_frame\":").append(expectedPerFrame)
                .append(",\"generated_total\":").append(generated).append(",\"planned_placements\":").append(placements)
                .append(",\"successful_calls\":").append(ok).append(",\"failed_calls\":").append(failed)
                .append(",\"planner_ns\":").append(stats(planner)).append(",\"emitter_ns\":").append(stats(emitter))
                .append(",\"snapshot_reads_ns\":").append(stats(snapshot)).append(",\"whole_fake_tick_ns\":").append(stats(total)).append('}');
            f.driver.close();assertEquals(1,f.cancels);
        }
        json.append("]}\n");
        Path output=Path.of(System.getProperty("adapter.root","."),"build/reports/k1/synthetic-measurements.json");
        Files.createDirectories(output.getParent());Files.writeString(output,json.toString());
        System.out.println(json);
    }
}
