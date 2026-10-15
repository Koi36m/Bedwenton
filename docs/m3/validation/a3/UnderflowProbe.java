import java.math.BigDecimal;
import java.math.MathContext;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.curve.LineSegment;
import workshop.vfx.math.curve.Spiral;

/** A3 probe (review scratch): subnormal underflow against the revision 2 relative bounds, on the throwaway reference. */
public class UnderflowProbe {
    static final double Q = Double.MIN_VALUE;
    static final BigDecimal PI = new BigDecimal("3.14159265358979323846264338327950288419716939937510");
    static String q(BigDecimal v) { return v.divide(new BigDecimal(Q), MathContext.DECIMAL64).stripTrailingZeros().toPlainString() + " q"; }
    static String q(double v) { return q(new BigDecimal(v)); }
    public static void main(String[] a) {
        System.out.println("java " + System.getProperty("java.version") + "; q = Double.MIN_VALUE = 2^-1074");
        System.out.println("== A3 radius: r0 = q, r1 = 2q, t = 0.5, turns = 0, startTurn = 0, rise = 0");
        double r0 = Q, r1 = 2 * Q, t = 0.5;
        System.out.println("  0.5 * q = " + q(0.5 * Q) + " (exact 0.5 q, a tie, rounded to even = 0); 0.5 * 2q = " + q(0.5 * r1));
        double lerp = (1 - t) * r0 + t * r1;
        BigDecimal exact = new BigDecimal(1.5).multiply(new BigDecimal(Q));
        System.out.println("  exact radius 1.5 q; lerp = " + q(lerp) + "; Spiral.point(0.5).x = " + q(new Spiral(r0, r1, 0, 0, 0).point(t).x()));
        BigDecimal s = new BigDecimal(0.5).multiply(new BigDecimal(r0)).add(new BigDecimal(0.5).multiply(new BigDecimal(r1)));
        System.out.println("  S = " + q(s) + "; revision 2 radius bound 2^-51 S = " + q(s.multiply(new BigDecimal(0x1p-51))) + "; nearest doubles q and 2q are both 0.5 q away: no double meets it");
        System.out.println("  revision 2 point bound 2^-49 S + S dphi = " + q(s.multiply(new BigDecimal(0x1p-49)).add(s.multiply(new BigDecimal(2 * Math.PI * 0x1p-51)))));
        System.out.println("  LineSegment((q,0,0), (2q,0,0)).point(0.5).x = " + q(new LineSegment(new Vec3(Q, 0, 0), new Vec3(2 * Q, 0, 0)).point(0.5).x()) + "; revision 2 Bezier bound 2^-49 G max|P| = " + q(new BigDecimal(0x1p-49).multiply(new BigDecimal(2 * Q))));
        System.out.println("== radius error amplified by w and w^2: r0 = q, r1 = 2q, t = 0.5, turns = 1000 (phase 500 turns: cos 1, sin 0)");
        Spiral sp = new Spiral(Q, 2 * Q, 0, 1000, 0);
        BigDecimal w = PI.multiply(BigDecimal.valueOf(2000));
        System.out.println("  derivative z: exact -w 1.5q = " + q(w.multiply(exact).negate()) + "; computed " + q(sp.derivative(0.5).z()));
        System.out.println("  second x:     exact -w^2 1.5q = " + q(w.multiply(w).multiply(exact).negate()) + "; computed " + q(sp.secondDerivative(0.5).x()));
        System.out.println("== subnormal turns: turns = 7q, r = 1e100 (dr = 0), startTurn = 0.25, t = 1 (phase 0.25 + 7q: sin ~ 1)");
        Spiral st = new Spiral(1e100, 1e100, 0, 7 * Q, 0.25);
        BigDecimal wex = PI.multiply(BigDecimal.valueOf(14)).multiply(new BigDecimal(Q));
        BigDecimal dx = wex.multiply(new BigDecimal(1e100)).negate();
        double comp = st.derivative(1).x();
        System.out.println("  w = 2 pi 7q = " + q(wex) + "; fl(fl(2 pi) * 7q) = " + q(2 * Math.PI * 7 * Q));
        System.out.println("  derivative x: exact " + dx.round(MathContext.DECIMAL64) + "; computed " + comp + "; error " + dx.subtract(new BigDecimal(comp)).abs().round(MathContext.DECIMAL64));
        BigDecimal wS = wex.multiply(new BigDecimal(1e100));
        BigDecimal dphi = new BigDecimal(2 * Math.PI * 0x1p-51);
        System.out.println("  revision 2 derivative bound 2^-48 W + W dphi (W = w S) = " + wS.multiply(new BigDecimal(0x1p-48)).add(wS.multiply(dphi)).round(MathContext.DECIMAL64));
        System.out.println("== tolerance computation in doubles (revision 2 helper style) underflows too");
        System.out.println("  2^-49 * q = " + 0x1p-49 * Q + ";  (|1-t| r0 + |t| r1) for the A3 case = " + q(Math.abs(1 - t) * r0 + Math.abs(t) * r1) + " (exact 1.5 q)");
        System.out.println("== overflow of turns * t (T4 clarification): turns = 2^600 (closed), k = 2^500");
        Spiral big = new Spiral(1, 1, 0, 0x1p600, 0);
        System.out.println("  turns * k = " + 0x1p600 * 0x1p500 + "; point(k) = " + big.point(0x1p500) + "; point(0) = " + big.point(0));
        System.out.println("  closed, turns = 2^600, derivative(0) = " + big.derivative(0) + "; second(0) = " + big.secondDerivative(0) + " (w^2 r overflows)");
    }
}
