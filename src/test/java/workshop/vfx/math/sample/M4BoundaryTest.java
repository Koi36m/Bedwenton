package workshop.vfx.math.sample;

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
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Contract sections 2 and 10: API shape, dependencies, purity and forbidden calls of the production package. */
class M4BoundaryTest {
    private static final Path SRC = Path.of("src/main/java/workshop/vfx/math/sample");

    private static List<Path> sources() throws IOException {
        try (Stream<Path> s = Files.list(SRC)) {
            return s.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static String stripComments(String code) {
        return code.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "").replaceAll("\"(\\\\.|[^\"\\\\])*\"", "\"\"");
    }

    @Test
    @DisplayName("Dependencies and calls: imports only java.*, workshop.vfx.math.core and workshop.vfx.math.curve (never time); no trigonometry, pow, exp, log, fma, random, clocks, threads, synchronization or I/O")
    void dependenciesAndCalls() throws IOException {
        List<Path> files = sources();
        assertTrue(files.size() >= 6, "production sources present");
        Pattern imports = Pattern.compile("^import\\s+(static\\s+)?([\\w.]+)", Pattern.MULTILINE);
        Pattern forbidden = Pattern.compile("\\b(Math|StrictMath)\\.(sin|cos|tan|asin|acos|atan|atan2|sinh|cosh|tanh|exp|expm1|log|log10|log1p|pow|cbrt|hypot|fma|random)\\b"
            + "|\\bnew\\s+(Random|SplittableRandom|Thread)\\b|ThreadLocalRandom|System\\.(nanoTime|currentTimeMillis)|\\bsynchronized\\b|\\bvolatile\\b"
            + "|java\\.util\\.concurrent|java\\.io|java\\.nio|parallelStream|\\.parallel\\(\\)|workshop\\.vfx\\.math\\.time");
        for (Path p : files) {
            String code = stripComments(Files.readString(p));
            Matcher m = imports.matcher(code);
            while (m.find()) {
                String name = m.group(2);
                assertTrue(name.startsWith("java.lang") || name.startsWith("java.util.") && !name.startsWith("java.util.concurrent")
                    || name.startsWith("workshop.vfx.math.core.") || name.startsWith("workshop.vfx.math.curve."), p + " imports " + name);
            }
            Matcher f = forbidden.matcher(code);
            if (f.find()) {
                fail(p.getFileName() + " uses forbidden " + f.group());
            }
        }
    }

    @Test
    @DisplayName("Purity: no mutable static state (every static field is final and of an immutable type)")
    void noMutableStatics() throws Exception {
        for (Class<?> c : publicTypes()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) && !f.isSynthetic() && !f.isEnumConstant() && !f.getName().equals("$VALUES")) {
                    assertTrue(Modifier.isFinal(f.getModifiers()), c.getSimpleName() + "." + f.getName() + " must be final");
                    assertTrue(f.getType().isPrimitive() || f.getType() == String.class || f.getType().isRecord() || f.getType().isEnum(),
                        c.getSimpleName() + "." + f.getName() + " must be immutable, is " + f.getType());
                }
            }
        }
    }

    private static List<Class<?>> publicTypes() {
        return List.of(Endpoints.class, SampleLimits.class, ArcLengthTolerance.class, ArcLengthTable.class, SampleSet.class, Sampler.class);
    }

    private static Set<String> publicMethods(Class<?> c) {
        return Arrays.stream(c.getDeclaredMethods()).filter(m -> Modifier.isPublic(m.getModifiers()) && !m.isSynthetic())
            .map(m -> (Modifier.isStatic(m.getModifiers()) ? "static " : "") + m.getName() + Arrays.stream(m.getParameterTypes()).map(Class::getSimpleName).collect(Collectors.joining(",", "(", ")")))
            .collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    @DisplayName("API shape (contract section 2): exactly the six public types with exactly the specified public members; no public constructors on ArcLengthTable, SampleSet, Sampler")
    void apiShape() throws Exception {
        Set<String> types = new TreeSet<>();
        for (Path p : sources()) {
            String code = stripComments(Files.readString(p));
            Matcher m = Pattern.compile("^public\\s+(final\\s+)?(class|record|enum|interface)\\s+(\\w+)", Pattern.MULTILINE).matcher(code);
            while (m.find()) {
                types.add(m.group(3));
            }
        }
        assertEquals(new TreeSet<>(List.of("ArcLengthTable", "ArcLengthTolerance", "Endpoints", "SampleLimits", "SampleSet", "Sampler")), types);
        assertTrue(Endpoints.class.isEnum());
        assertEquals(List.of("INCLUDE_END", "EXCLUDE_END"), Arrays.stream(Endpoints.values()).map(Enum::name).toList());
        assertTrue(SampleLimits.class.isRecord() && ArcLengthTolerance.class.isRecord());
        for (Class<?> c : List.of(ArcLengthTable.class, SampleSet.class, Sampler.class)) {
            assertTrue(Modifier.isFinal(c.getModifiers()), c + " final");
            for (Constructor<?> k : c.getDeclaredConstructors()) {
                assertTrue(!Modifier.isPublic(k.getModifiers()) && !Modifier.isProtected(k.getModifiers()), c + " has no public constructor");
            }
        }
        assertEquals(new TreeSet<>(List.of("static build(Curve,ArcLengthTolerance,SampleLimits)", "static build(Curve,double,double,ArcLengthTolerance,SampleLimits)",
            "converged()", "curve()", "effectiveTolerance()", "endParameter()", "errorEstimate()", "evaluations()", "length()", "lengthAt(double)",
            "panelCount()", "parameterAt(double)", "startParameter()")), publicMethods(ArcLengthTable.class));
        assertEquals(new TreeSet<>(List.of("parameter(int)", "parameters()", "point(int)", "points()", "size()", "transformed(Pose)")),
            publicMethods(SampleSet.class).stream().filter(s -> !s.startsWith("toString") && !s.startsWith("equals") && !s.startsWith("hashCode")).collect(Collectors.toCollection(TreeSet::new)));
        assertEquals(new TreeSet<>(List.of("static bySpacing(ArcLengthTable,double,Endpoints,SampleLimits)", "static countForSpacing(double,double,Endpoints)",
            "static uniformDistance(ArcLengthTable,int,Endpoints,SampleLimits)", "static uniformParameter(Curve,double,double,int,Endpoints,SampleLimits)",
            "static uniformParameter(Curve,int,Endpoints,SampleLimits)")), publicMethods(Sampler.class));
        for (String f : List.of("MAX_SAMPLES_CEILING", "MIN_EVALUATIONS", "MAX_EVALUATIONS_CEILING")) {
            Field fld = SampleLimits.class.getField(f);
            assertTrue(Modifier.isStatic(fld.getModifiers()) && Modifier.isFinal(fld.getModifiers()) && fld.getType() == int.class);
        }
        for (String f : List.of("MIN_RELATIVE", "MAX_RELATIVE")) {
            Field fld = ArcLengthTolerance.class.getField(f);
            assertTrue(Modifier.isStatic(fld.getModifiers()) && Modifier.isFinal(fld.getModifiers()) && fld.getType() == double.class);
        }
        Method m = SampleSet.class.getMethod("points");
        assertEquals(List.class, m.getReturnType());
    }
}
