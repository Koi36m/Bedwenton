import java.math.BigDecimal;
/** Revision 2 counterexample probe (review scratch): reproduces findings A1 and A2 and the revised reduction and bounds. */
public class Counterexamples {
    static double frac(double x) { double r = x % 1.0; if (r < 0) r += 1.0; return r == 1.0 ? 0.0 : r; }
    static double[] cs(double f) {
        double r4 = 4.0 * f, q = Math.floor(r4), a = (r4 - q) * (Math.PI / 2);
        double c = StrictMath.cos(a), s = StrictMath.sin(a);
        switch ((int) q) { case 0: return new double[]{c, s}; case 1: return new double[]{-s, c}; case 2: return new double[]{-c, -s}; default: return new double[]{s, -c}; }
    }
    static String pt(double r, double rise, double t, double[] c) { return "(" + r * c[0] + ", " + rise * t + ", " + -(r * c[1]) + ")"; }
    public static void main(String[] a) {
        System.out.println("java " + System.getProperty("java.version"));
        System.out.println("== A1: r0=r1=1, rise=0, turns=1, startTurn=2^52, t=0.25; exact phase 2^52+1/4 => (0,0,-1)");
        double turns = 1, t = 0.25;
        for (double st : new double[]{0x1p52, -0x1p52, 0x1p53, -0x1p53, 0x1p60, Double.MAX_VALUE, -Double.MAX_VALUE, 0x1p50 + 0.25, 0x1p51 + 0.5, 1e15, -1e15}) {
            double r1 = frac(frac(turns * t) + st);
            double r2 = frac(frac(turns * t) + frac(st));
            System.out.printf("startTurn=%s  frac(st)=%s  R1 f=%s point=%s | R2 f=%s point=%s%n", st, frac(st), r1, pt(1, 0, t, cs(r1)), r2, pt(1, 0, t, cs(r2)));
        }
        System.out.println("product near 2^51: turns=1, t=2^51-0.25, startTurn=0.5 (exact phase frac 0.25)");
        t = 0x1p51 - 0.25; double st = 0.5;
        System.out.println("  R1 f=" + frac(frac(t) + st) + "  naive f=" + frac(t + st) + "  unreduced-product f=" + frac(t + frac(st)) + "  R2 f=" + frac(frac(t) + frac(st)));
        System.out.println("near seam: turns=1, t=2^-53, startTurn=nextDown(1): exact phase 1 => f must be 0: R2 f=" + frac(frac(0x1p-53) + frac(Math.nextDown(1.0))));
        System.out.println("near seam: startTurn=-2^-60 frac=" + frac(-0x1p-60) + "  startTurn=-MIN_VALUE frac=" + frac(-Double.MIN_VALUE) + "  MIN_VALUE frac=" + frac(Double.MIN_VALUE));
        System.out.println("== A2: r0=1, r1=1.2, rise=0, turns=0, startTurn=0, t=-5");
        double r0 = 1.0, rr1 = 1.2; t = -5.0; turns = 0;
        double rf = (1 - t) * r0 + t * rr1;
        BigDecimal exact = new BigDecimal(1).subtract(new BigDecimal(t)).multiply(new BigDecimal(r0)).add(new BigDecimal(t).multiply(new BigDecimal(rr1)));
        System.out.println("  exact r = " + exact + " = " + exact.doubleValue() + " (2^-52 = " + 0x1p-52 + ")  computed lerp r = " + rf);
        double R = Math.abs(rf);
        double dphi = 2 * Math.PI * 0x1p-52 * (Math.abs(turns * t) + 2);
        double r1bound = 0x1p-49 * (R + 0) + R * dphi + Math.ulp(exact.doubleValue());
        double S = Math.abs(1 - t) * r0 + Math.abs(t) * rr1;
        double r2bound = 0x1p-49 * S + S * dphi;
        System.out.println("  error = " + exact.subtract(new BigDecimal(rf)).abs().doubleValue() + "  revision 1 test tolerance (R from the computed r, + ulp slack) = " + r1bound + "  revision 1 bound with the exact R = " + (0x1p-49 * exact.doubleValue() + exact.doubleValue() * dphi));
        System.out.println("  S = " + S + "  revision 2 point bound 2^-49 S + S dphi = " + r2bound + "  radius bound 2^-51 S = " + 0x1p-51 * S);
        System.out.println("== alternative radius form (not conforming) r0 + t*(r1-r0): r0=1e6, r1=1, t=1-1e-7");
        r0 = 1e6; rr1 = 1; t = 1 - 1e-7;
        BigDecimal ex2 = new BigDecimal(1).subtract(new BigDecimal(t)).multiply(new BigDecimal(r0)).add(new BigDecimal(t).multiply(new BigDecimal(rr1)));
        double lerp = (1 - t) * r0 + t * rr1, alt = r0 + t * (rr1 - r0);
        S = Math.abs(1 - t) * r0 + Math.abs(t) * rr1;
        System.out.println("  exact=" + ex2.doubleValue() + " lerp err=" + ex2.subtract(new BigDecimal(lerp)).abs().doubleValue() + " alt err=" + ex2.subtract(new BigDecimal(alt)).abs().doubleValue() + " 2^-51 S=" + 0x1p-51 * S + " 2^-49 S=" + 0x1p-49 * S);
    }
}
