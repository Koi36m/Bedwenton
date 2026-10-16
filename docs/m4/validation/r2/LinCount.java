import java.nio.file.*; import java.util.*;
import workshop.vfx.math.core.Vec3; import workshop.vfx.math.curve.*; import workshop.vfx.math.sample.*;
/** M4 R2 probe (review scratch): counts panels in linear mode (contract 5.5) over the arclength.csv rows at four tolerances. Needs a reference implementation instrumented with public static counters PANELS and LINEAR (panels with I2 > 0 outside ratio mode). */
public class LinCount {
    static Vec3 v(double[] p, int i) { return new Vec3(p[i], p[i + 1], p[i + 2]); }
    public static void main(String[] a) throws Exception {
        List<String> lines = Files.readAllLines(Path.of(a[0])); boolean header = false; int rows = 0;
        for (double rel : new double[] {1e-4, 1e-7, 0x1p-30, 1e-12}) { header = false; for (String l : lines) {
            if (l.isBlank() || l.startsWith("#")) continue; if (!header) { header = true; continue; }
            String[] r = l.split(","); double[] p = new double[12]; for (int i = 0; i < 12; i++) p[i] = Double.parseDouble(r[1 + i]);
            Curve c = switch (r[0]) { case "LINE" -> new LineSegment(v(p, 0), v(p, 3)); case "QUAD" -> new QuadraticBezier(v(p, 0), v(p, 3), v(p, 6));
                case "CUBIC" -> new CubicBezier(v(p, 0), v(p, 3), v(p, 6), v(p, 9)); default -> new Spiral(p[0], p[1], p[2], p[3], p[4]); };
            ArcLengthTable.build(c, Double.parseDouble(r[13]), Double.parseDouble(r[14]), new ArcLengthTolerance(0, rel), new SampleLimits(1 << 20, 1 << 22)); rows++; }
        }
        System.out.println("tables " + rows + " panels " + ArcLengthTable.PANELS + " linear-mode panels with I2 > 0: " + ArcLengthTable.LINEAR);
    }
}
