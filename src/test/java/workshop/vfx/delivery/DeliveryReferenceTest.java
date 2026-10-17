package workshop.vfx.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import workshop.vfx.math.time.TimeDomain;
import workshop.vfx.scene.EffectScene;
import workshop.vfx.scene.Layer;
import workshop.vfx.scene.Reveal;
import workshop.vfx.scene.StyleKey;

/**
 * M6-TEST-SPECIFICATION.md section 4: replays the plans written by the independent Python generator
 * (tools/generate_m6_reference_plans.py, exact integer arithmetic) and compares every reported field.
 */
class DeliveryReferenceTest {
    private static final Path DIR = Path.of("src/test/resources/m6/reference");

    @Test
    @DisplayName("Manifest: the six reference files match MANIFEST.sha256")
    void manifest() throws IOException, NoSuchAlgorithmException {
        List<String> lines = Files.readAllLines(DIR.resolve("MANIFEST.sha256"), StandardCharsets.UTF_8);
        assertEquals(6, lines.size());
        for (String line : lines) {
            String[] parts = line.split("  ");
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(DIR.resolve(parts[1])));
            assertEquals(parts[0], String.format("%064x", new BigInteger(1, digest)), parts[1]);
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"bloom", "cloudstep", "healer", "trident", "astral", "stress"})
    @DisplayName("Reference plans: every case of the family reproduces the independent plan field by field")
    void family(String family) throws IOException {
        List<String> lines = Files.readAllLines(DIR.resolve(family + ".txt"), StandardCharsets.UTF_8);
        int cases = 0;
        int expects = 0;
        Case c = null;
        for (String line : lines) {
            if (line.startsWith("#") || line.isEmpty()) {
                continue;
            }
            String[] t = line.split(" ");
            switch (t[0]) {
                case "case" -> c = new Case(t[1], Long.parseLong(value(t[2], "tick")));
                case "policy" -> {
                    long[] v = new long[12];
                    for (int i = 0; i < 12; i++) {
                        v[i] = Long.parseLong(t[i + 1]);
                    }
                    c.policy = new DeliveryPolicy((int) v[0], (int) v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8], (int) v[9], (int) v[10], (int) v[11]);
                }
                case "styles" -> {
                    for (String s : list(t[1])) {
                        c.styles.add(StyleKey.of(s));
                    }
                }
                case "recipe" -> {
                    c.priorities.put(t[1], EffectPriority.valueOf(t[2]));
                    Map<String, LayerRole> roles = new HashMap<>();
                    for (String kv : list(value(t[3], "roles"))) {
                        String[] p = kv.split(":");
                        roles.put(p[0], LayerRole.valueOf(p[1]));
                    }
                    c.roles.put(t[1], roles);
                    c.variants.put(t[1], new EnumMap<>(QualityTier.class));
                }
                case "variant" -> {
                    double lifetime = Double.parseDouble(value(t[3], "lifetime"));
                    List<Layer> layers = new ArrayList<>();
                    for (String spec : list(value(t[4], "layers"))) {
                        String[] p = spec.split(":");
                        String style = String.join(":", java.util.Arrays.copyOfRange(p, 2, p.length - 2));
                        layers.add(M6TestSupport.layer(p[0], Integer.parseInt(p[1]), style, Reveal.valueOf(p[p.length - 2]),
                                Double.parseDouble(p[p.length - 1])));
                    }
                    c.variants.get(t[1]).put(QualityTier.valueOf(t[2]),
                            EffectScene.of(TimeDomain.GAME, lifetime, layers, M6TestSupport.LIMITS));
                }
                case "request" -> {
                    String name = value(t[2], "recipe");
                    Case cc = c;
                    EffectRecipe recipe = c.recipes.computeIfAbsent(name,
                            k -> EffectRecipe.of(k, cc.priorities.get(k), cc.variants.get(k), cc.roles.get(k)));
                    List<String> vs = list(value(t[7], "viewers"));
                    long[] viewers = new long[vs.size()];
                    for (int i = 0; i < viewers.length; i++) {
                        viewers[i] = Long.parseLong(vs.get(i));
                    }
                    long id = Long.parseLong(t[1]);
                    QualityTier tier = QualityTier.valueOf(value(t[3], "tier"));
                    int world = Integer.parseInt(value(t[4], "world"));
                    long ordinal = Long.parseLong(value(t[5], "ordinal"));
                    double local = Double.parseDouble(value(t[6], "local"));
                    boolean fail = value(t[8], "fail").equals("1");
                    c.requests.add(fail ? M6TestSupport.failing(id, recipe, tier, world, ordinal, local, viewers)
                            : M6TestSupport.request(id, recipe, tier, world, ordinal, local, viewers));
                }
                case "expect" -> c.expects.add(t);
                case "totals" -> c.totals = t;
                case "end" -> {
                    expects += check(c);
                    cases++;
                    c = null;
                }
                default -> throw new AssertionError("unknown line " + line);
            }
        }
        assertTrue(cases >= 3 && expects >= cases, family + ": " + cases + " cases");
    }

    private static int check(Case c) {
        DeliveryPlan plan = DeliveryPlanner.plan(c.tick, c.requests, c.styles, c.policy);
        M6TestSupport.checkInvariants(c.requests, c.styles, c.policy, plan);
        assertEquals(c.tick, plan.tickOrdinal());
        Map<Long, EffectDelivery> byId = new HashMap<>();
        for (EffectDelivery d : plan.deliveries()) {
            byId.put(d.requestId(), d);
        }
        for (String[] e : c.expects) {
            EffectDelivery d = byId.get(Long.parseLong(e[1]));
            String at = c.name + " request " + e[1];
            assertEquals(Integer.parseInt(value(e[2], "rank")), d.allocationRank(), at + " rank");
            assertEquals(Outcome.valueOf(value(e[3], "outcome")), d.outcome(), at + " outcome");
            String tier = value(e[4], "tier");
            assertEquals(tier.equals("-") ? null : QualityTier.valueOf(tier), d.tier(), at + " tier");
            assertEquals(Long.parseLong(value(e[5], "reserved")), d.reservedGeneratedPoints(), at + " reserved");
            assertEquals(Integer.parseInt(value(e[6], "source")), d.sourcePoints(), at + " source");
            assertEquals(Integer.parseInt(value(e[7], "unsupported")), d.unsupportedPoints(), at + " unsupported");
            assertEquals(Integer.parseInt(value(e[8], "kept")), d.keptPoints(), at + " kept");
            assertEquals(Long.parseLong(value(e[9], "placements")), d.placements(), at + " placements");
            List<String> batches = list(value(e[10], "batches"));
            assertEquals(batches.size(), d.orderedBatchCount(), at + " batches");
            for (int pos = 0; pos < batches.size(); pos++) {
                String[] b = batches.get(pos).split(":");
                int n = Integer.parseInt(b[3]);
                int m = Integer.parseInt(b[1]);
                int offset = Integer.parseInt(b[2]);
                assertEquals(Integer.parseInt(b[0]), d.orderedBatch(pos), at + " batch " + pos);
                assertEquals(m, d.kept(pos), at + " kept " + pos);
                assertEquals(n, d.frame().batches().get(d.orderedBatch(pos)).size(), at + " size " + pos);
                for (int j = 0; j < m; j++) {
                    // the contract's selection, with the generator's offset
                    assertEquals(offset + (int) ((long) j * n / m), d.selectedIndex(pos, j), at + " index " + pos + "/" + j);
                }
            }
            List<String> recipients = list(value(e[11], "recipients"));
            assertEquals(recipients.size(), d.recipientCount(), at + " recipients");
            for (int r = 0; r < recipients.size(); r++) {
                String[] p = recipients.get(r).split(":");
                assertEquals(Long.parseLong(p[0]), d.recipient(r), at + " recipient " + r);
                assertEquals(Integer.parseInt(p[1]), d.recipientPoints(r), at + " recipient points " + r);
            }
        }
        String[] t = c.totals;
        assertEquals(Long.parseLong(value(t[1], "reserved")), plan.reservedGeneratedPoints(), c.name);
        assertEquals(Long.parseLong(value(t[2], "source")), plan.sourcePoints(), c.name);
        assertEquals(Long.parseLong(value(t[3], "kept")), plan.keptPoints(), c.name);
        assertEquals(Long.parseLong(value(t[4], "recipients")), plan.recipients(), c.name);
        assertEquals(Long.parseLong(value(t[5], "placements")), plan.placements(), c.name);
        assertEquals(Long.parseLong(value(t[6], "unsupported")), plan.unsupportedPoints(), c.name);
        Map<Outcome, Integer> counts = new EnumMap<>(Outcome.class);
        for (String kv : list(value(t[7], "counts"))) {
            String[] p = kv.split(":");
            counts.put(Outcome.valueOf(p[0]), Integer.parseInt(p[1]));
        }
        for (Outcome o : Outcome.values()) {
            assertEquals((int) counts.getOrDefault(o, 0), plan.count(o), c.name + " count " + o);
        }
        return c.expects.size();
    }

    private static String value(String token, String key) {
        assertTrue(token.startsWith(key + "="), "expected " + key + " in " + token);
        return token.substring(key.length() + 1);
    }

    private static List<String> list(String s) {
        return s.equals("-") ? List.of() : List.of(s.split(";"));
    }

    private static final class Case {
        final String name;
        final long tick;
        DeliveryPolicy policy;
        final Set<StyleKey> styles = new java.util.HashSet<>();
        final Map<String, EffectPriority> priorities = new HashMap<>();
        final Map<String, Map<String, LayerRole>> roles = new HashMap<>();
        final Map<String, Map<QualityTier, EffectScene>> variants = new HashMap<>();
        final Map<String, EffectRecipe> recipes = new LinkedHashMap<>();
        final List<DeliveryRequest> requests = new ArrayList<>();
        final List<String[]> expects = new ArrayList<>();
        String[] totals;

        Case(String name, long tick) {
            this.name = name;
            this.tick = tick;
        }
    }
}
