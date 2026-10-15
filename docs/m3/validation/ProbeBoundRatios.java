import workshop.vfx.math.curve.*;
import workshop.vfx.math.core.Vec3;
import java.nio.file.*;
import java.util.*;
public class Ratio {
    static double h(String s) { return Double.parseDouble(s); }
    static Vec3 v(String[] r, int i) { return new Vec3(h(r[i]), h(r[i+1]), h(r[i+2])); }
    static double m(Vec3 a) { return Math.max(Math.abs(a.x()), Math.max(Math.abs(a.y()), Math.abs(a.z()))); }
    static List<String[]> csv(Path p) throws Exception { List<String[]> o = new ArrayList<>(); boolean hd = false;
        for (String l : Files.readAllLines(p)) { if (l.isBlank() || l.startsWith("#")) continue; if (!hd) { hd = true; continue; } o.add(l.split(",")); } return o; }
    public static void main(String[] a) throws Exception {
        Path d = Path.of(a[0]);
        double we = 0, wp = 0, wd = 0, ws = 0, sp = 0, sd = 0, ss = 0;
        for (String[] r : csv(d.resolve("easing.csv"))) if (r[2].equals("ROUNDED")) we = Math.max(we, Math.abs(Easing.valueOf(r[0]).apply(h(r[1])) - h(r[3])) / 0x1p-50);
        for (String[] r : csv(d.resolve("bezier.csv"))) {
            if (!r[2].equals("ROUNDED")) continue;
            double t = h(r[1]); int n = r[0].equals("LINE") ? 1 : r[0].equals("QUAD") ? 2 : 3;
            Vec3[] P = {v(r,3), v(r,6), v(r,9), v(r,12)};
            Curve c = n == 1 ? new LineSegment(P[0], P[1]) : n == 2 ? new QuadraticBezier(P[0], P[1], P[2]) : new CubicBezier(P[0], P[1], P[2], P[3]);
            double mp = 0, md = 0; for (int i = 0; i <= n; i++) { mp = Math.max(mp, m(P[i])); if (i > 0) md = Math.max(md, m(P[i].sub(P[i-1]))); }
            double G = Math.abs(1 - t) + Math.abs(t);
            wp = Math.max(wp, m(c.point(t).sub(v(r,15))) / (0x1p-49 * Math.pow(G, n) * mp));
            wd = Math.max(wd, m(c.derivative(t).sub(v(r,18))) / (0x1p-48 * n * Math.pow(G, n - 1) * md));
            if (n > 1) ws = Math.max(ws, m(c.secondDerivative(t).sub(v(r,21))) / (0x1p-46 * 4 * n * (n - 1) * Math.pow(G, n - 2) * mp));
        }
        for (String[] r : csv(d.resolve("spiral.csv"))) {
            if (!r[6].equals("ROUNDED")) continue;
            double r0 = h(r[0]), r1 = h(r[1]), rise = h(r[2]), tu = h(r[3]), st = h(r[4]), t = h(r[5]);
            Spiral s = new Spiral(r0, r1, rise, tu, st);
            double R = Math.abs(r0 == r1 ? r0 : (1 - t) * r0 + t * r1), dr = Math.abs(r1 - r0), w = 2 * Math.PI * Math.abs(tu);
            double dphi = 2 * Math.PI * 0x1p-52 * (Math.abs(tu * t) + Math.abs(st) + 2);
            sp = Math.max(sp, m(s.point(t).sub(v(r,7))) / (0x1p-49 * (R + Math.abs(rise * t)) + R * dphi));
            double W = dr + w * R + Math.abs(rise); if (W > 0) sd = Math.max(sd, m(s.derivative(t).sub(v(r,10))) / (0x1p-48 * W + W * dphi));
            double W2 = 2 * dr * w + w * w * R; if (W2 > 0) ss = Math.max(ss, m(s.secondDerivative(t).sub(v(r,13))) / (0x1p-47 * W2 + W2 * dphi));
        }
        System.out.printf("worst error / contract bound (reference implementation, ROUNDED rows):%n easing %.3f%n bezier point %.3f, derivative %.3f, second %.3f%n spiral point %.3f, derivative %.3f, second %.3f%n", we, wp, wd, ws, sp, sd, ss);
    }
}
