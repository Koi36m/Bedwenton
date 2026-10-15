package workshop.vfx.math.curve;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Contract sections 1, 2 and 9: API shape, purity, dependencies and the StrictMath rule. */
class M3BoundaryTest {
    private static final Path SRC = Path.of("src/main/java/workshop/vfx/math/curve");
    private static final List<Class<?>> PUBLIC_TYPES = List.of(Curve.class, LineSegment.class, QuadraticBezier.class,
            CubicBezier.class, Spiral.class, PosedCurve.class, Easing.class);
    /** java.lang.Math transcendental or fused operations; StrictMath.* does not match because of the look-behind. */
    private static final Pattern NON_PORTABLE = Pattern.compile("(?<![A-Za-z])Math\\.(sin|cos|tan|asin|acos|atan|atan2|sinh|cosh|tanh|exp|expm1|log|log10|log1p|pow|cbrt|hypot|fma|random)\\s*\\(");
    private static final List<String> FORBIDDEN = List.of("System.nanoTime", "System.currentTimeMillis", "java.time.",
            "java.util.Random", "SplittableRandom", "ThreadLocalRandom", "Thread.", "new Thread", "Executor", "synchronized",
            "volatile", "org.bukkit", "io.papermc", "net.minecraft", "org.joml", "workshop.vfx.math.time");

    private static List<Path> sources() throws IOException {
        try (Stream<Path> s = Files.list(SRC)) {
            return s.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    @Test
    @DisplayName("Public API: the seven specified types are public; curves are records implementing Curve; every other source type is not public (section 2)")
    void apiShape() throws IOException {
        for (Class<?> c : PUBLIC_TYPES) {
            assertTrue(Modifier.isPublic(c.getModifiers()), c + " public");
        }
        assertTrue(Curve.class.isInterface(), "Curve is an interface");
        for (Class<?> c : List.of(LineSegment.class, QuadraticBezier.class, CubicBezier.class, Spiral.class, PosedCurve.class)) {
            assertTrue(c.isRecord() && Curve.class.isAssignableFrom(c), c + " is a record implementing Curve");
        }
        assertTrue(Easing.class.isEnum(), "Easing is an enum");
        for (Path p : sources()) {
            String name = p.getFileName().toString().replace(".java", "");
            boolean listed = PUBLIC_TYPES.stream().anyMatch(c -> c.getSimpleName().equals(name));
            if (!listed && Pattern.compile("public\\s+(final\\s+)?(class|interface|record|enum)\\s+" + name + "\\b").matcher(Files.readString(p)).find()) {
                fail("unspecified public type " + name);
            }
        }
    }

    @Test
    @DisplayName("Portability: no java.lang.Math transcendental, pow, hypot or fma calls; trigonometry goes through StrictMath (section 7, DM3-7)")
    void strictMathOnly() throws IOException {
        for (Path p : sources()) {
            String src = Files.readString(p);
            Matcher m = NON_PORTABLE.matcher(src);
            if (m.find()) {
                fail(p.getFileName() + " uses non-portable " + m.group());
            }
        }
    }

    @Test
    @DisplayName("Purity: no clocks, randomness, threads, platform types or dependency on the M2 time package; no mutable statics (sections 1, 9)")
    void purity() throws IOException {
        for (Path p : sources()) {
            String src = Files.readString(p);
            for (String token : FORBIDDEN) {
                if (src.contains(token)) {
                    fail(p.getFileName() + " contains forbidden token '" + token + "'");
                }
            }
        }
        for (Class<?> c : PUBLIC_TYPES) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    assertTrue(Modifier.isFinal(f.getModifiers()), c.getSimpleName() + "." + f.getName() + " static final");
                } else {
                    assertTrue(Modifier.isFinal(f.getModifiers()), c.getSimpleName() + "." + f.getName() + " final");
                }
            }
            for (Method m : c.getDeclaredMethods()) {
                String rt = m.getReturnType().getName();
                assertTrue(!rt.startsWith("org.bukkit") && !rt.startsWith("org.joml"), c + " exposes " + rt);
            }
        }
    }
}
