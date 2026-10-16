import java.nio.file.*; import java.util.*;
import workshop.vfx.math.core.Vec3; import workshop.vfx.math.curve.*; import workshop.vfx.math.sample.*;
/** M4 R2.1 probe (review scratch): prints length, estimate, panels and 7 parameterAt/lengthAt values per arclength.csv row and tolerance, as raw bits, to compare two reference implementations. */
public class LenDump {
    static Vec3 v(double[] p, int i) { return new Vec3(p[i], p[i + 1], p[i + 2]); }
    public static void main(String[] a) throws Exception {
        List<String> lines = Files.readAllLines(Path.of(a[0]));
        for (double rel : new double[] {1e-4, 1e-7, 0x1p-30, 1e-12}) { boolean header = false;
            for (String l : lines) {
                if (l.isBlank() || l.startsWith("#")) continue; if (!header) { header = true; continue; }
                String[] r = l.split(","); double[] p = new double[12]; for (int i = 0; i < 12; i++) p[i] = Double.parseDouble(r[1 + i]);
                Curve c = switch (r[0]) { case "LINE" -> new LineSegment(v(p, 0), v(p, 3)); case "QUAD" -> new QuadraticBezier(v(p, 0), v(p, 3), v(p, 6));
                    case "CUBIC" -> new CubicBezier(v(p, 0), v(p, 3), v(p, 6), v(p, 9)); default -> new Spiral(p[0], p[1], p[2], p[3], p[4]); };
                double t0 = Double.parseDouble(r[13]), t1 = Double.parseDouble(r[14]);
                ArcLengthTable t = ArcLengthTable.build(c, t0, t1, new ArcLengthTolerance(0, rel), new SampleLimits(1 << 20, 1 << 22));
                StringBuilder sb = new StringBuilder(Long.toHexString(Double.doubleToRawLongBits(t.length())) + " " + t.panelCount() + " " + Long.toHexString(Double.doubleToRawLongBits(t.errorEstimate())));
                for (int k = 1; k < 8; k++) { sb.append(' ').append(Long.toHexString(Double.doubleToRawLongBits(t.lengthAt(t0 + (t1 - t0) * (k / 8.0)))));
                    if (t.length() > 0) sb.append(' ').append(Long.toHexString(Double.doubleToRawLongBits(t.parameterAt(t.length() * (k / 8.0))))); }
                System.out.println(sb);
            }
        }
    }
}
