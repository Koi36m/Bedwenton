import java.util.SplittableRandom;
public class Trig {
    public static void main(String[] a) {
        SplittableRandom r = new SplittableRandom(1);
        long diffSin = 0, diffCos = 0, n = 0;
        double ex = 0;
        for (int i = 0; i < 20_000_000; i++) {
            double x = r.nextDouble(-Math.PI * 4, Math.PI * 4) * (i % 4 == 0 ? 1e6 : 1);
            if (Math.sin(x) != StrictMath.sin(x)) { diffSin++; if (ex == 0) ex = x; }
            if (Math.cos(x) != StrictMath.cos(x)) diffCos++;
            n++;
        }
        System.out.println("Java " + Runtime.version() + ": Math.sin != StrictMath.sin in " + diffSin + " of " + n
                + " cases; cos: " + diffCos + "; example x=" + ex + " Math=" + Math.sin(ex) + " Strict=" + StrictMath.sin(ex));
        // radians vs turns at a full-circle seam and after many turns
        double twoPi = 2 * Math.PI;
        System.out.println("sin(2*PI) = " + StrictMath.sin(twoPi) + "  (turn-reduced: exactly 0)");
        double turns = 1e6 + 0.25; // a quarter turn after a million turns: exact value is (cos, sin) = (0, 1)
        System.out.println("radians: cos(2*PI*t) at t=1e6+0.25 -> " + StrictMath.cos(twoPi * turns) + " ; turn-reduced -> 0.0 exactly");
    }
}
