package workshop.vfx.math.sample;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.curve.CubicBezier;
import workshop.vfx.math.curve.Curve;
import workshop.vfx.math.curve.LineSegment;
import workshop.vfx.math.curve.QuadraticBezier;
import workshop.vfx.math.curve.Spiral;

/**
 * Helpers for the M4 tests. Nothing here integrates a speed or inverts an arc length the way the contract recommends:
 * the oracles are the generator's exact or 1e-28 values (tools/generate_m4_reference_vectors.py), closed forms
 * (lines, constant-speed spirals, and the asinh antiderivative of sqrt(a t^2 + b t + c) for quadratic Beziers and
 * spirals, evaluated in double here), and the M3 contract's derivative error bounds restated from the inputs.
 */
final class SampleTestSupport {
    private SampleTestSupport() {}

    static final double EPS = 0x1p-53;
    static final double ETA = Double.MIN_VALUE;
    static final SampleLimits LIMITS = new SampleLimits(1 << 16, 1 << 20);
    static final ArcLengthTolerance TIGHT = new ArcLengthTolerance(0, 0x1p-30);

    static double hex(String s) {
        return Double.parseDouble(s);
    }

    static void assertExact(Vec3 expected, Vec3 actual, String label) {
        if (!(expected.x() == actual.x() && expected.y() == actual.y() && expected.z() == actual.z())) {
            fail(label + ": expected exactly " + expected + " but was " + actual);
        }
    }

    static double maxAbs(Vec3 v) {
        return Math.max(Math.abs(v.x()), Math.max(Math.abs(v.y()), Math.abs(v.z())));
    }

    // ------------------------------------------------------------ reference data

    static List<String[]> csv(String name) {
        String path = "/m4/reference/" + name;
        try (InputStream in = SampleTestSupport.class.getResourceAsStream(path)) {
            if (in == null) {
                fail("missing resource " + path);
            }
            List<String[]> rows = new ArrayList<>();
            boolean header = false;
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#")) {
                    continue;
                }
                if (!header) {
                    header = true;
                    continue;
                }
                rows.add(t.split(","));
            }
            return rows;
        } catch (IOException e) {
            fail("cannot read " + path + ": " + e);
            return List.of();
        }
    }

    static byte[] bytes(String path) {
        try (InputStream in = SampleTestSupport.class.getResourceAsStream(path)) {
            if (in == null) {
                fail("missing resource " + path);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            fail("cannot read " + path + ": " + e);
            return new byte[0];
        }
    }

    static Vec3 vec(double[] p, int i) {
        return new Vec3(p[i], p[i + 1], p[i + 2]);
    }

    /** The curve of a reference row (kind, then 12 parameter columns). */
    static Curve curveOf(String kind, double[] p) {
        return switch (kind) {
            case "LINE" -> new LineSegment(vec(p, 0), vec(p, 3));
            case "QUAD" -> new QuadraticBezier(vec(p, 0), vec(p, 3), vec(p, 6));
            case "CUBIC" -> new CubicBezier(vec(p, 0), vec(p, 3), vec(p, 6), vec(p, 9));
            default -> new Spiral(p[0], p[1], p[2], p[3], p[4]);
        };
    }

    static double[] params(String[] row) {
        double[] p = new double[12];
        for (int i = 0; i < 12; i++) {
            p[i] = hex(row[1 + i]);
        }
        return p;
    }

    /**
     * Upper bound of the absolute error of the computed speed |C'(t)| over [t0, t1], restated from the M3 contract
     * (section 7.2 with amendment A3) from the inputs only, doubled, plus the norm's own rounding (8 eps of the
     * speed). Integrated over the range this bounds the rounding part of the length error.
     */
    static double speedErrorBound(String kind, double[] p, double t0, double t1) {
        double tm = Math.max(Math.abs(t0), Math.abs(t1));
        double g = Math.max(Math.abs(1 - t0) + Math.abs(t0), Math.abs(1 - t1) + Math.abs(t1));
        double comp;
        if (kind.equals("SPIRAL")) {
            double r0 = p[0], r1 = p[1], rise = p[2], turns = p[3];
            double s = r0 == r1 ? r0 : g * Math.max(r0, r1);
            double w = 2 * Math.PI * Math.abs(turns), dr = Math.abs(r1 - r0);
            double big = dr + w * s + Math.abs(rise);
            double dphi = 2 * Math.PI * 0x1p-52 * (Math.abs(turns) * tm + 2);
            comp = 0x1p-48 * big + big * dphi + 4 * (1 + w + s) * ETA;
            return 2 * (Math.sqrt(3) * comp + 8 * EPS * big);
        }
        int n = kind.equals("LINE") ? 1 : kind.equals("QUAD") ? 2 : 3;
        double maxDiff = 0;
        for (int i = 0; i < n; i++) {
            for (int c = 0; c < 3; c++) {
                maxDiff = Math.max(maxDiff, Math.abs(p[3 * (i + 1) + c] - p[3 * i + c]));
            }
        }
        comp = 0x1p-48 * n * Math.pow(g, n - 1) * maxDiff + 2 * n * n * Math.pow(g, n) * ETA;
        return 2 * (Math.sqrt(3) * comp + 8 * EPS * n * Math.pow(g, n - 1) * maxDiff * Math.sqrt(3));
    }

    // ------------------------------------------------------------ closed-form arc length (independent of the table)

    /** Integral of sqrt(a t^2 + b t + c) over [u, v] for a quadratic that is >= 0 on R; asinh form. */
    static double sqrtQuadraticIntegral(double a, double b, double c, double u, double v) {
        return prim(a, b, c, v) - prim(a, b, c, u);
    }

    private static double prim(double a, double b, double c, double t) {
        double q = Math.max(0, (a * t + b) * t + c);
        if (a == 0) {
            return Math.sqrt(c) * t;
        }
        double disc = 4 * a * c - b * b;
        if (disc > 1e-12 * (b * b + 4 * a * Math.abs(c))) {
            double x = (2 * a * t + b) / Math.sqrt(disc);
            double asinh = x >= 0 ? Math.log(x + Math.sqrt(x * x + 1)) : -Math.log(-x + Math.sqrt(x * x + 1));
            return (2 * a * t + b) * Math.sqrt(q) / (4 * a) + disc / (8 * a * Math.sqrt(a)) * asinh;
        }
        double ts = -b / (2 * a);
        return Math.sqrt(a) * (t - ts) * Math.abs(t - ts) / 2;
    }

    /** Arc length of a spiral (M3 contract formula) from u to v, closed form. */
    static double spiralLength(Spiral s, double u, double v) {
        double dr = s.endRadius() - s.startRadius(), w = 2 * Math.PI * s.turns();
        double a = w * w * dr * dr, b = 2 * w * w * dr * s.startRadius();
        double c = dr * dr + s.rise() * s.rise() + w * w * s.startRadius() * s.startRadius();
        return sqrtQuadraticIntegral(a, b, c, u, v);
    }

    /** Arc length of a quadratic Bezier from u to v, closed form. */
    static double quadLength(QuadraticBezier q, double u, double v) {
        Vec3 d0 = q.p1().sub(q.p0()), e = q.p2().sub(q.p1()).sub(d0);
        double a = 4 * e.dot(e), b = 8 * d0.dot(e), c = 4 * d0.dot(d0);
        return sqrtQuadraticIntegral(a, b, c, u, v);
    }

    // ------------------------------------------------------------ instrumented curves

    /** Counts calls; delegates to an M3 curve. Not thread-safe (tests only). */
    static final class CountingCurve implements Curve {
        final Curve inner;
        int points, derivatives, seconds;

        CountingCurve(Curve inner) {
            this.inner = inner;
        }

        @Override
        public Vec3 point(double t) {
            points++;
            return inner.point(t);
        }

        @Override
        public Vec3 derivative(double t) {
            derivatives++;
            return inner.derivative(t);
        }

        @Override
        public Vec3 secondDerivative(double t) {
            seconds++;
            return inner.secondDerivative(t);
        }
    }

    /** A user curve x = t, y = amplitude sin(2 pi frequency t): fast oscillation, adversarial for quadrature. */
    static Curve wiggle(double amplitude, double frequency) {
        return new Curve() {
            @Override
            public Vec3 point(double t) {
                return new Vec3(t, amplitude * StrictMath.sin(2 * Math.PI * frequency * t), 0);
            }

            @Override
            public Vec3 derivative(double t) {
                return new Vec3(1, amplitude * 2 * Math.PI * frequency * StrictMath.cos(2 * Math.PI * frequency * t), 0);
            }

            @Override
            public Vec3 secondDerivative(double t) {
                return new Vec3(0, -amplitude * Math.pow(2 * Math.PI * frequency, 2) * StrictMath.sin(2 * Math.PI * frequency * t), 0);
            }
        };
    }
}
