package workshop.vfx.paper.core;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import workshop.vfx.scene.StyleKey;

/** M7 allocation-order emitter with exact recipient-prefix accounting. */
public final class SessionStyleHealth implements StyleHealth {
    private final ThreadGuard thread;
    private final Set<StyleKey> catalogue;
    private final Map<StyleKey, Integer> streaks = new HashMap<>();
    private final Set<StyleKey> disabled = new LinkedHashSet<>();

    public SessionStyleHealth(Set<StyleKey> catalogue) { this(catalogue, new ThreadGuard()); }
    public SessionStyleHealth(Set<StyleKey> catalogue, ThreadGuard thread) {
        this.catalogue = Set.copyOf(catalogue); this.thread = java.util.Objects.requireNonNull(thread);
    }

    @Override public Set<StyleKey> enabledStyles() {
        thread.check();
        Set<StyleKey> s = new LinkedHashSet<>(catalogue); s.removeAll(disabled); return Set.copyOf(s);
    }
    @Override public boolean isDisabled(StyleKey style) { thread.check(); return disabled.contains(style); }
    @Override public void recordSuccess(StyleKey style) { thread.check(); if (!disabled.contains(style)) streaks.put(style, 0); }
    @Override public boolean recordFailure(StyleKey style) {
        thread.check();
        if (disabled.contains(style)) return false;
        int n = streaks.merge(style, 1, Integer::sum);
        if (n >= DISABLE_AFTER_CONSECUTIVE_FAILURES) { disabled.add(style); return true; }
        return false;
    }
    @Override public int streak(StyleKey style) { thread.check(); return streaks.getOrDefault(style, 0); }
}
