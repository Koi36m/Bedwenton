import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.curve.Spiral;

/**
 * Revision 2 bound-ratio probe (review scratch; run against a throwaway reference implementation). For every spiral
 * reference row it reports the worst error / bound under the revision 2 bounds (contract 7.2), and how many rows the
 * revision 1 bounds (R = |computed r(t)|, |startTurn| in dphi) would reject. Usage: java ProbeBoundRatiosR2 <reference dir>
 */
public class ProbeBoundRatiosR2 {
    static double h(String s) { return Double.parseDouble(s); }
    static Vec3 v(String[] r, int i) { return new Vec3(h(r[i]), h(r[i + 1]), h(r[i + 2])); }
    static double m(Vec3 a) { return Math.max(Math.abs(a.x()), Math.max(Math.abs(a.y()), Math.abs(a.z()))); }

    public static void main(String[] a) throws Exception {
        List<String[]> rows = new ArrayList<>();
        boolean header = false;
        for (String l : Files.readAllLines(Path.of(a[0]).resolve("spiral.csv"))) {
            if (l.isBlank() || l.startsWith("#")) continue;
            if (!header) { header = true; continue; }
            rows.add(l.split(","));
        }
        double[][] worst = new double[2][3]; // [rev1 rows, rev2 rows][point, derivative, second]
        String[] worstRow = new String[3];
        int r1Reject = 0, exactFail = 0, idx = 0;
        String firstReject = null;
        for (String[] r : rows) {
            int part = idx++ < 549 ? 0 : 1;
            double r0 = h(r[0]), r1 = h(r[1]), rise = h(r[2]), tu = h(r[3]), st = h(r[4]), t = h(r[5]);
            Spiral s = new Spiral(r0, r1, rise, tu, st);
            Vec3 p = s.point(t), d = s.derivative(t), dd = s.secondDerivative(t);
            double ep = m(p.sub(v(r, 7))), ed = m(d.sub(v(r, 10))), es = m(dd.sub(v(r, 13)));
            if (r[6].equals("EXACT") && ep != 0) exactFail++;
            double S = r0 == r1 ? r0 : Math.abs(1 - t) * r0 + Math.abs(t) * r1;
            double dr = Math.abs(r1 - r0), w = 2 * Math.PI * Math.abs(tu);
            double dphi = 2 * Math.PI * 0x1p-52 * (Math.abs(tu * t) + 2);
            double bp = 0x1p-49 * (S + Math.abs(rise * t)) + S * dphi;
            double W = dr + w * S + Math.abs(rise), W2 = 2 * dr * w + w * w * S;
            double[] ratio = {bp > 0 ? ep / bp : 0, W > 0 ? ed / (0x1p-48 * W + W * dphi) : 0, W2 > 0 ? es / (0x1p-47 * W2 + W2 * dphi) : 0};
            for (int k = 0; k < 3; k++) {
                if (ratio[k] > worst[part][k]) { worst[part][k] = ratio[k]; if (part == 1) worstRow[k] = r[0] + "," + r[1] + "," + r[3] + "," + r[4] + "," + r[5]; }
            }
            // revision 1 test tolerance: R from the floating-point radius, |startTurn| in dphi, + ulp of the expected value
            double R1 = Math.abs(r0 == r1 ? r0 : (1 - t) * r0 + t * r1);
            double dphi1 = 2 * Math.PI * 0x1p-52 * (Math.abs(tu * t) + Math.abs(st) + 2);
            double tol1 = 0x1p-49 * (R1 + Math.abs(rise * t)) + R1 * dphi1 + Math.ulp(m(v(r, 7)));
            if (!r[6].equals("EXACT") && ep > tol1) { r1Reject++; if (firstReject == null) firstReject = String.join(",", r).substring(0, 120); }
        }
        System.out.printf("rows %d, EXACT point mismatches %d%n", rows.size(), exactFail);
        System.out.printf("worst error / revision 2 bound, revision 1 rows (1-549):  point %.3f, derivative %.3f, second %.3f%n", worst[0][0], worst[0][1], worst[0][2]);
        System.out.printf("worst error / revision 2 bound, revision 2 rows (550-959): point %.3f, derivative %.3f, second %.3f%n", worst[1][0], worst[1][1], worst[1][2]);
        for (int k = 0; k < 3; k++) System.out.println("  worst revision 2 row [" + k + "] (r0,r1,turns,startTurn,t): " + worstRow[k]);
        System.out.printf("ROUNDED rows whose point the revision 1 tolerance rejects: %d%s%n", r1Reject, firstReject == null ? "" : " (first: " + firstReject + "...)");
    }
}
