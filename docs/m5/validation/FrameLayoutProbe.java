import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Quat;
import workshop.vfx.math.core.Vec3;

/**
 * M5 R1 design probe (review scratch): allocation and time of three frame layouts for DM5-2, transforming the same
 * local points by the same pose. (a) one record per particle (layer name, style, position Vec3); (b) one batch per
 * layer holding a List<Vec3>; (c) one batch per layer holding packed x,y,z doubles.
 */
public class FrameLayoutProbe {
    record Particle(String layer, String style, Vec3 position) { }
    record BatchList(String layer, String style, List<Vec3> points) { }
    record BatchPacked(String layer, String style, double[] xyz) { }
    static final com.sun.management.ThreadMXBean MX = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    static long sink;

    public static void main(String[] a) {
        int layers = 8, perLayer = 1250, frames = 400; // 10,000 points per frame
        Vec3[] local = new Vec3[perLayer];
        for (int i = 0; i < perLayer; i++) local[i] = new Vec3(Math.cos(i), 0.01 * i, Math.sin(i));
        Pose pose = new Pose(new Vec3(100, 64, -30), new Quat(0.9, 0.1, 0.3, 0.2), 1.5);
        for (int round = 0; round < 3; round++) {
            String tag = round < 2 ? "warmup" : "measured";
            for (int layout = 0; layout < 3; layout++) {
                long b0 = MX.getCurrentThreadAllocatedBytes(), t0 = System.nanoTime();
                for (int f = 0; f < frames; f++) {
                    if (layout == 0) {
                        List<Particle> out = new ArrayList<>();
                        for (int l = 0; l < layers; l++) for (Vec3 p : local) out.add(new Particle("layer" + l, "ns:style", pose.toWorld(p)));
                        sink += out.size();
                    } else if (layout == 1) {
                        List<BatchList> out = new ArrayList<>();
                        for (int l = 0; l < layers; l++) {
                            List<Vec3> pts = new ArrayList<>(perLayer);
                            for (Vec3 p : local) pts.add(pose.toWorld(p));
                            out.add(new BatchList("layer" + l, "ns:style", List.copyOf(pts)));
                        }
                        sink += out.size();
                    } else {
                        List<BatchPacked> out = new ArrayList<>();
                        for (int l = 0; l < layers; l++) {
                            double[] xyz = new double[3 * perLayer];
                            for (int i = 0; i < perLayer; i++) { Vec3 w = pose.toWorld(local[i]); xyz[3 * i] = w.x(); xyz[3 * i + 1] = w.y(); xyz[3 * i + 2] = w.z(); }
                            out.add(new BatchPacked("layer" + l, "ns:style", xyz));
                        }
                        sink += out.size();
                    }
                }
                long bytes = MX.getCurrentThreadAllocatedBytes() - b0, ns = System.nanoTime() - t0;
                if (round == 2) System.out.printf("%-8s layout %s: %8.1f bytes per point, %7.1f ns per point (10,000 points x %d frames)%n", tag,
                    new String[] {"(a) per-particle records ", "(b) batch with List<Vec3>", "(c) batch, packed doubles"}[layout], bytes / (double) (frames * layers * perLayer), ns / (double) (frames * layers * perLayer), frames);
            }
        }
        System.out.println("(the Vec3 temporaries of pose.toWorld are included in every layout; escape analysis may remove some of them in (c))");
    }
}
