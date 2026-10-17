package workshop.vfx.paper.core;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import workshop.vfx.scene.StyleKey;

/** M7 R3 THROWAWAY REFERENCE (design verification only; compiled by docs/m7/verification, never production). */
public final class SessionStyleHealth implements StyleHealth {
    private final Set<StyleKey> catalogue;
    private final Map<StyleKey, Integer> streaks = new HashMap<>();
    private final Set<StyleKey> disabled = new LinkedHashSet<>();

    public SessionStyleHealth(Set<StyleKey> catalogue) { this.catalogue = Set.copyOf(catalogue); }

    @Override public Set<StyleKey> enabledStyles() {
        Set<StyleKey> s = new LinkedHashSet<>(catalogue); s.removeAll(disabled); return Set.copyOf(s);
    }
    @Override public boolean isDisabled(StyleKey style) { return disabled.contains(style); }
    @Override public void recordSuccess(StyleKey style) { streaks.put(style, 0); }
    @Override public boolean recordFailure(StyleKey style) {
        if (disabled.contains(style)) return false;
        int n = streaks.merge(style, 1, Integer::sum);
        if (n >= DISABLE_AFTER_CONSECUTIVE_FAILURES) { disabled.add(style); return true; }
        return false;
    }
    @Override public int streak(StyleKey style) { return streaks.getOrDefault(style, 0); }
}
