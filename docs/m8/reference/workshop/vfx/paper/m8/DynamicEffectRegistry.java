package workshop.vfx.paper.m8;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import workshop.vfx.paper.core.SessionIds;

/** M8 R3 THROWAWAY REFERENCE (design verification only; never production). */
public final class DynamicEffectRegistry implements TickContributor {
    public static final long FIRST_ID = 1L << 62;
    private record Key(UUID owner, EffectKind kind) { }
    private record Entry(DynamicEffect effect, AnchorBinding binding) { }
    private final Map<Key, Entry> effects = new HashMap<>();
    private final AnchorSource anchors;
    private final SessionIds ids;
    private final int maxPerPlayer;
    private EffectScheduler scheduler;
    private long nextId = FIRST_ID;
    private int failedStarts, disposeFailures, identityViolations;
    private boolean closed;

    public DynamicEffectRegistry(AnchorSource anchors, SessionIds ids, int maxPerPlayer) {
        this.anchors = Objects.requireNonNull(anchors); this.ids = Objects.requireNonNull(ids);
        if (maxPerPlayer < 1 || maxPerPlayer > 3) throw new IllegalArgumentException("maxEffectsPerPlayer 1..3");
        this.maxPerPlayer = maxPerPlayer;
    }
    public void bind(EffectScheduler scheduler) { this.scheduler = Objects.requireNonNull(scheduler); }

    /**
     * R3 F5: transactional start. Admission (F1) and every step that can fail run before the single commit point, so a
     * failure leaves the registry, the existing effect (if replacing), the counts and the task exactly as they were:
     * <ol>
     *   <li>admission: replacement always fits; otherwise the per-player M8 cap and the shared global cap (no mutation);</li>
     *   <li>allocate the request id (consumed even if the start fails; ids are never reused);</li>
     *   <li>build the candidate; verify it reports this id, the bound owner and the requested kind;</li>
     *   <li>{@code scheduler.ensureRunning()} (idempotent; on failure the scheduler leaves no task behind);</li>
     *   <li>commit: register the candidate, then dispose the replaced effect (nothing after the commit can fail).</li>
     * </ol>
     * A failure in steps 3-4 disposes the candidate only and rethrows.
     */
    public long start(UUID owner, EffectKind kind, EffectFactory factory) {
        if (closed) throw new IllegalStateException("VFX session is disabled");
        Objects.requireNonNull(owner); Objects.requireNonNull(kind); Objects.requireNonNull(factory);
        if (kind == EffectKind.RING) throw new IllegalArgumentException("rings are started by RingDriver");
        Key key = new Key(owner, kind);
        Entry old = effects.get(key);
        boolean replacing = old != null;
        long mine = effects.keySet().stream().filter(k -> k.owner().equals(owner)).count();
        if (!replacing && mine >= maxPerPlayer) throw new IllegalArgumentException("Per-player M8 effect cap reached");
        if (!scheduler.hasCapacityFor(replacing)) throw new IllegalArgumentException("Active effect cap reached");
        if (nextId == Long.MAX_VALUE) throw new IllegalStateException("M8 request ids exhausted");
        long id = nextId++;
        AnchorBinding binding = new AnchorBinding(owner, ids.viewerId(owner));
        DynamicEffect candidate = null;
        try {
            candidate = factory.create(id, binding);
            if (candidate == null || candidate.requestId() != id || !owner.equals(candidate.owner()) || candidate.kind() != kind)
                throw new IllegalStateException("Effect factory returned a mismatched effect");
            scheduler.ensureRunning();
        } catch (RuntimeException e) {
            failedStarts++;
            if (candidate != null) disposeQuietly(candidate);
            throw e;
        }
        effects.put(key, new Entry(candidate, binding));            // commit point
        if (old != null) disposeQuietly(old.effect());
        return id;
    }
    public void stop(UUID owner, EffectKind kind) {
        Entry e = effects.remove(new Key(owner, kind)); if (e != null) disposeQuietly(e.effect()); scheduler.effectsChanged();
    }
    public void stopOwner(UUID owner) {
        effects.entrySet().removeIf(en -> { if (en.getKey().owner().equals(owner)) { disposeQuietly(en.getValue().effect()); return true; } return false; });
        scheduler.effectsChanged();
    }
    public Optional<DynamicEffect> effect(UUID owner, EffectKind kind) {
        return Optional.ofNullable(effects.get(new Key(owner, kind))).map(Entry::effect);
    }
    public int failedStarts() { return failedStarts; }
    public int disposeFailures() { return disposeFailures; }
    public int identityViolations() { return identityViolations; }

    /**
     * R3 F6: a draft is passed on only when it carries its effect's request id, kind, bound owner and bound owner viewer
     * id; otherwise it is dropped, counted and the effect is stopped this tick.
     */
    @Override public List<EffectDraft> drafts(long tick) {
        List<EffectDraft> out = new ArrayList<>();
        List<Map.Entry<Key, Entry>> ordered = new ArrayList<>(effects.entrySet());
        ordered.sort(Comparator.comparingLong(en -> en.getValue().effect().requestId()));
        for (Map.Entry<Key, Entry> en : ordered) {
            DynamicEffect e = en.getValue().effect();
            AnchorBinding b = en.getValue().binding();
            Optional<EffectDraft> d = e.contribute(tick, anchors.snapshot(b.owner(), tick));
            boolean forged = d.isPresent() && !matches(d.get(), e, b);
            if (forged) identityViolations++;
            if (forged || e.finished(tick)) { disposeQuietly(e); effects.remove(en.getKey()); continue; }
            d.ifPresent(out::add);
        }
        return out;
    }
    private static boolean matches(EffectDraft d, DynamicEffect e, AnchorBinding b) {
        return d.requestId() == e.requestId() && d.kind() == e.kind() && b.owner().equals(d.owner())
                && d.ownerViewerId() == b.ownerViewerId();
    }
    private void disposeQuietly(DynamicEffect e) {
        try { e.dispose(); } catch (RuntimeException ex) { disposeFailures++; }
    }
    @Override public boolean active() { return !effects.isEmpty(); }
    @Override public int activeCount() { return effects.size(); }
    @Override public void close() { closed = true; effects.values().forEach(en -> disposeQuietly(en.effect())); effects.clear(); }
}
