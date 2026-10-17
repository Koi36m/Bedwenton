package workshop.vfx.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static workshop.vfx.delivery.M6TestSupport.fingerprint;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** G8: the plan is a pure function of its arguments, identical across repetition and threads. */
class M6DeterminismTest {
    @Test
    @DisplayName("G8: 200 random scenarios planned 10 times each give identical plans")
    void repetition() {
        Random rnd = new Random(8);
        for (int i = 0; i < 200; i++) {
            M6TestSupport.Scenario s = M6TestSupport.randomScenario(rnd);
            String first = fingerprint(s.plan());
            for (int k = 0; k < 10; k++) {
                assertEquals(first, fingerprint(s.plan()));
            }
        }
    }

    @Test
    @DisplayName("G8: the same scenarios planned concurrently on 8 threads give the single-threaded plans")
    void threads() throws Exception {
        Random rnd = new Random(88);
        List<M6TestSupport.Scenario> scenarios = new ArrayList<>();
        List<String> expected = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            M6TestSupport.Scenario s = M6TestSupport.randomScenario(rnd);
            scenarios.add(s);
            expected.add(fingerprint(s.plan()));
        }
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<List<String>>> futures = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                int shift = t;
                futures.add(pool.submit(() -> {
                    List<String> out = new ArrayList<>();
                    for (int i = 0; i < scenarios.size(); i++) {
                        out.add(fingerprint(scenarios.get((i + shift * 13) % scenarios.size()).plan()));
                    }
                    return out;
                }));
            }
            for (int t = 0; t < 8; t++) {
                List<String> got = futures.get(t).get();
                for (int i = 0; i < scenarios.size(); i++) {
                    assertEquals(expected.get((i + t * 13) % scenarios.size()), got.get(i));
                }
            }
        } finally {
            pool.shutdown();
        }
    }
}
