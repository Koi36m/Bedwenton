import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.curve.*;
import workshop.vfx.math.sample.*;

/** M4 R2.1 probe (review scratch): F4 half-width underflow and F5 weighted-sum overflow, on a given reference implementation. */
public class F45 {
    static final double Q = Double.MIN_VALUE, MAX = Double.MAX_VALUE;
    static void table(String label, Curve c, double t0, double t1) {
        try {
            ArcLengthTable t = ArcLengthTable.build(c, t0, t1, new ArcLengthTolerance(0, 0x1p-40), new SampleLimits(64, 4096));
            String la, pa;
            try { la = String.valueOf(t.lengthAt(Math.nextUp(t0))); } catch (RuntimeException e) { la = e.getClass().getSimpleName(); }
            try { pa = t.length() > 0 ? String.valueOf(t.parameterAt(0.5 * t.length())) : "-"; } catch (RuntimeException e) { pa = e.getClass().getSimpleName(); }
            System.out.printf("  %-58s length=%-24s converged=%-5s panels=%-3d lengthAt(nextUp(t0))=%s parameterAt(L/2)=%s%n", label, t.length(), t.converged(), t.panelCount(), la, pa);
        } catch (RuntimeException e) {
            System.out.printf("  %-58s build throws %s: %s%n", label, e.getClass().getSimpleName(), e.getMessage());
        }
    }
    public static void main(String[] a) {
        System.out.println("== F4: M3 LineSegment on [0, k MIN_VALUE] (exact length = speed * k MIN_VALUE)");
        for (double speed : new double[] {1, 3, 1e10, 1e300}) {
            LineSegment line = new LineSegment(Vec3.ZERO, new Vec3(speed, 0, 0));
            for (double k : new double[] {1, 2, 3}) table(String.format("speed %.0e on [0, %.0f q] (exact %s)", speed, k, speed * (k * Q)), line, 0, k * Q);
        }
        System.out.println("== F5: smooth finite curves whose weighted node sum overflows (true lengths finite)");
        LineSegment big = new LineSegment(Vec3.ZERO, new Vec3(1e308, 0, 0));
        table("line speed 1e308 on [0, q] (exact 4.94e-16)", big, 0, Q);
        table("line speed 1e308 on [0, 1e-300] (exact 1e8)", big, 0, 1e-300);
        table("line speed 1e308 on [0, 1e-10] (exact 1e298)", big, 0, 1e-10);
        LineSegment huge = new LineSegment(Vec3.ZERO, new Vec3(MAX / 2, MAX / 2, MAX / 2));
        table("line (MAX/2)(1,1,1), speed 1.56e308, on [0, 1e-300]", huge, 0, 1e-300);
        LineSegment top = new LineSegment(Vec3.ZERO, new Vec3(MAX, MAX, 0));
        table("line (MAX, MAX, 0), speed 2.5e308 (overflows), on [0, 1e-300]", top, 0, 1e-300);
        Spiral ring = Spiral.circle(1e306);
        table("circle radius 1e306 (speed 6.3e306) on [0, 1e-300]", ring, 0, 1e-300);
        System.out.println("== F5: finite table (speed 1e308 on [0, 2^-20]); lengthAt one ulp above a panel start (half-width 0)");
        try {
            ArcLengthTable t = ArcLengthTable.build(big, 0, 0x1p-20, new ArcLengthTolerance(0, 0x1p-40), new SampleLimits(64, 4096));
            System.out.println("  length=" + t.length() + " lengthAt(MIN_VALUE)=" + t.lengthAt(Q) + " lengthAt(nextUp(2^-24))=" + t.lengthAt(Math.nextUp(0x1p-24)));
        } catch (RuntimeException e) { System.out.println("  " + e); }
    }
}
