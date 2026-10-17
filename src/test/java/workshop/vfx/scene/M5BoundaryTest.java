package workshop.vfx.scene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Contract sections 1, 2 and 10: dependencies, forbidden calls, no mutable statics and the exact public API shape. */
class M5BoundaryTest {
    private static final Path SRC = Path.of("src/main/java/workshop/vfx/scene");

    private static List<Path> sources() throws IOException {
        try (Stream<Path> s = Files.list(SRC)) {
            return s.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static String code(Path p) throws IOException {
        // strip comments and string literals so documentation may mention forbidden names
        String s = Files.readString(p);
        s = s.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\n]*", " ");
        return s.replaceAll("\"(\\\\.|[^\"\\\\])*\"", "\"\"");
    }

    @Test
    @DisplayName("Imports: only workshop.vfx.math.core, .time, .curve, .sample and java.util; never Bukkit, Paper, Minecraft, java.io, java.nio, java.net, threads or reflection")
    void imports() throws IOException {
        Pattern imports = Pattern.compile("^import\\s+(static\\s+)?([\\w.]+)", Pattern.MULTILINE);
        List<Path> files = sources();
        assertTrue(files.size() >= 10, "production sources present: " + files);
        for (Path p : files) {
            Matcher m = imports.matcher(code(p));
            while (m.find()) {
                String name = m.group(2);
                boolean ok = name.startsWith("workshop.vfx.math.core.") || name.startsWith("workshop.vfx.math.time.")
                    || name.startsWith("workshop.vfx.math.curve.") || name.startsWith("workshop.vfx.math.sample.")
                    || (name.startsWith("java.util.") && !name.startsWith("java.util.concurrent"));
                if (!ok) {
                    fail(p.getFileName() + " imports " + name);
                }
            }
            String c = code(p);
            for (String banned : new String[] {"org.bukkit", "io.papermc", "net.minecraft", "com.destroystokyo", "java.io.", "java.nio.", "java.net.", "java.lang.reflect"}) {
                assertTrue(!c.contains(banned), p.getFileName() + " references " + banned);
            }
        }
    }

    @Test
    @DisplayName("Forbidden calls: no Math trigonometry, pow, exp, log, hypot, fma or random (StrictMath sin and cos only); no clocks, threads, synchronization, Random, System.out or mutable static fields")
    void forbidden() throws Exception {
        Pattern bad = Pattern.compile("\\bMath\\.(sin|cos|tan|asin|acos|atan|atan2|sinh|cosh|tanh|exp|expm1|log|log10|log1p|pow|cbrt|hypot|fma|random)\\b"
            + "|\\bStrictMath\\.(tan|asin|acos|atan|atan2|sinh|cosh|tanh|exp|expm1|log|log10|log1p|pow|cbrt|hypot|fma|random)\\b"
            + "|System\\.(currentTimeMillis|nanoTime|out|err|getenv|getProperty)|\\bThread\\b|\\bsynchronized\\b|\\bvolatile\\b|\\bRandom\\b|ThreadLocal|Instant\\.|Clock\\b|Executor");
        for (Path p : sources()) {
            Matcher m = bad.matcher(code(p));
            if (m.find()) {
                fail(p.getFileName() + " uses forbidden " + m.group());
            }
        }
        for (Class<?> c : publicTypes()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) && !f.isSynthetic()) {
                    assertTrue(Modifier.isFinal(f.getModifiers()), c.getSimpleName() + "." + f.getName() + " must be final");
                }
            }
        }
    }

    private static List<Class<?>> publicTypes() throws ClassNotFoundException {
        List<Class<?>> out = new ArrayList<>();
        for (String n : new String[] {"StyleKey", "Shape", "Motion", "Visibility", "Reveal", "Layer", "SceneLimits", "EffectScene", "EffectFrame", "FrameBatch"}) {
            out.add(Class.forName("workshop.vfx.scene." + n));
        }
        return out;
    }

    private static Set<String> members(Class<?> c) {
        Set<String> out = new TreeSet<>();
        for (Method m : c.getDeclaredMethods()) {
            if (Modifier.isPublic(m.getModifiers()) && !m.isSynthetic()) {
                StringBuilder sb = new StringBuilder(Modifier.isStatic(m.getModifiers()) ? "static " : "").append(m.getReturnType().getSimpleName()).append(' ').append(m.getName()).append('(');
                for (Class<?> p : m.getParameterTypes()) {
                    sb.append(p.getSimpleName()).append(',');
                }
                out.add(sb.append(')').toString());
            }
        }
        for (Field f : c.getDeclaredFields()) {
            if (Modifier.isPublic(f.getModifiers())) {
                out.add("field " + f.getType().getSimpleName() + " " + f.getName());
            }
        }
        for (Constructor<?> k : c.getDeclaredConstructors()) {
            if (Modifier.isPublic(k.getModifiers()) || Modifier.isProtected(k.getModifiers())) {
                out.add("constructor/" + k.getParameterCount());
            }
        }
        return out;
    }

    @Test
    @DisplayName("API shape: exactly the ten public types of contract section 2 with exactly their public members; final classes without public constructors")
    void apiShape() throws Exception {
        Set<String> publicNames = new TreeSet<>();
        for (Path p : sources()) {
            Matcher m = Pattern.compile("^public\\s+(final\\s+)?(class|record|enum|interface)\\s+(\\w+)", Pattern.MULTILINE).matcher(code(p));
            while (m.find()) {
                publicNames.add(m.group(3));
            }
        }
        assertEquals(new TreeSet<>(List.of("EffectFrame", "EffectScene", "FrameBatch", "Layer", "Motion", "Reveal", "SceneLimits", "Shape", "StyleKey", "Visibility")), publicNames);
        assertEquals(Set.of("static Shape point(Vec3,)", "static Shape points(List,)", "static Shape of(SampleSet,)", "int size()", "Vec3 point(int,)", "field int MAX_POINTS"),
            members(Class.forName("workshop.vfx.scene.Shape")));
        assertEquals(Set.of("static Motion fixed(Pose,)", "static Motion spin(Vec3,double,double,Timing,Easing,)", "static Motion scale(double,double,Timing,Easing,)",
                "static Motion translate(Vec3,Vec3,Timing,Easing,)", "Motion then(Motion,)", "int steps()", "Pose poseAt(double,)",
                "field Motion IDENTITY", "field int MAX_STEPS", "field double MAX_TURNS", "field double MIN_SCALE", "field double MAX_SCALE"),
            members(Class.forName("workshop.vfx.scene.Motion")));
        assertEquals(Set.of("static EffectScene of(TimeDomain,double,List,SceneLimits,)", "TimeDomain domain()", "double lifetimeTicks()", "List layers()", "SceneLimits limits()",
                "int maxPointsPerFrame()", "EffectFrame evaluate(Pose,Playback,TimePoint,)", "field double UNBOUNDED"),
            members(Class.forName("workshop.vfx.scene.EffectScene")));
        assertEquals(Set.of("Stage stage()", "double localTicks()", "List batches()", "int pointCount()", "boolean isEmpty()"),
            members(Class.forName("workshop.vfx.scene.EffectFrame")));
        assertEquals(Set.of("int layerIndex()", "String layer()", "StyleKey style()", "TimingSample timing()", "Pose pose()", "int size()", "double x(int,)", "double y(int,)",
                "double z(int,)", "Vec3 point(int,)", "double[] coordinates()"),
            members(Class.forName("workshop.vfx.scene.FrameBatch")));
        for (String n : new String[] {"Shape", "Motion", "EffectScene", "EffectFrame", "FrameBatch"}) {
            Class<?> c = Class.forName("workshop.vfx.scene." + n);
            assertTrue(Modifier.isFinal(c.getModifiers()), n + " is final");
            assertTrue(members(c).stream().noneMatch(s -> s.startsWith("constructor/")), n + " has no public constructor");
        }
        for (String n : new String[] {"StyleKey", "Layer", "SceneLimits"}) {
            assertTrue(Class.forName("workshop.vfx.scene." + n).isRecord(), n + " is a record");
        }
        assertTrue(Class.forName("workshop.vfx.scene.Visibility").isEnum());
        assertTrue(Class.forName("workshop.vfx.scene.Reveal").isEnum());
    }
}
