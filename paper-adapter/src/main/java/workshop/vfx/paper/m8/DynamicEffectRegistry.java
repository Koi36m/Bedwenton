package workshop.vfx.paper.m8;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import workshop.vfx.paper.core.SessionIds;
import workshop.vfx.paper.core.ThreadGuard;

/** Bounded registry for future effects. K0 supplies no visual implementations or public start command. */
public final class DynamicEffectRegistry implements TickContributor {
    public static final long FIRST_ID = 1L << 62;
    private record Key(UUID owner, EffectKind kind) { }
    private static final class Entry {
        final DynamicEffect effect;
        final AnchorBinding binding;
        final long id;
        final Map<AnchorRejection, Long> rejections = new EnumMap<>(AnchorRejection.class);
        int invalidRun;
        Entry(DynamicEffect effect, AnchorBinding binding, long id) { this.effect=effect; this.binding=binding; this.id=id; }
    }
    private final ThreadGuard thread;
    private final AnchorSource anchors;
    private final SessionIds ids;
    private final AnchorIdentityGuard identity;
    private final int maxPerPlayer, invalidLimit;
    private final Map<Key, Entry> effects = new HashMap<>();
    private final Map<AnchorRejection, Long> rejections = new EnumMap<>(AnchorRejection.class);
    private EffectScheduler scheduler;
    private long nextId=FIRST_ID, failedStarts, disposeFailures, identityViolations, updateFailures;
    private boolean closed, mutating;

    public DynamicEffectRegistry(ThreadGuard thread, AnchorSource anchors, SessionIds ids, int maxPerPlayer, int invalidLimit) {
        this.thread=Objects.requireNonNull(thread);this.anchors=Objects.requireNonNull(anchors);this.ids=Objects.requireNonNull(ids);
        if (maxPerPlayer<1 || maxPerPlayer>3 || invalidLimit<1 || invalidLimit>200) throw new IllegalArgumentException("M8 limits outside hard ceilings");
        this.maxPerPlayer=maxPerPlayer;this.invalidLimit=invalidLimit;identity=new AnchorIdentityGuard(ids,thread);
    }
    public void bind(EffectScheduler scheduler) {
        thread.check(); if (closed || mutating || !effects.isEmpty()) throw new IllegalStateException("Bind only an idle registry");
        this.scheduler=Objects.requireNonNull(scheduler);
    }
    private void begin() {
        thread.check();if (mutating) throw new IllegalStateException("Reentrant registry mutation");
        if (scheduler==null) throw new IllegalStateException("Registry is not bound to its shared driver");mutating=true;
    }
    public long start(UUID owner, EffectKind kind, EffectFactory factory) {
        begin();
        try {
            if (closed) throw new IllegalStateException("VFX session is disabled");
            Objects.requireNonNull(owner);Objects.requireNonNull(kind);Objects.requireNonNull(factory);
            if (kind==EffectKind.RING) throw new IllegalArgumentException("Rings are started by RingDriver");
            Key key=new Key(owner,kind);Entry old=effects.get(key);boolean replacing=old!=null;
            long mine=effects.keySet().stream().filter(k->k.owner().equals(owner)).count();
            if (!replacing && mine>=maxPerPlayer) throw new IllegalArgumentException("Per-player M8 effect cap reached");
            if (!scheduler.hasCapacityFor(replacing)) throw new IllegalArgumentException("Active effect cap reached");
            if (effects.size()>=16 && !replacing) throw new IllegalArgumentException("Active effect hard ceiling reached");
            if (nextId==Long.MAX_VALUE) throw new IllegalStateException("M8 request ids exhausted");
            long id=nextId++;AnchorBinding binding=new AnchorBinding(owner,ids.viewerId(owner));
            DynamicEffect candidate=null;Entry entry;
            try {
                candidate=factory.create(id,binding);
                if (candidate==null || owned(candidate) || candidate.requestId()!=id || !owner.equals(candidate.owner()) || candidate.kind()!=kind)
                    throw new IllegalStateException("Effect factory returned a mismatched effect");
                entry=new Entry(candidate,binding,id);
                scheduler.ensureRunning();
            } catch (RuntimeException failure) {
                failedStarts++;
                // A defective factory must never cause disposal of an already registered effect.
                if (candidate!=null && !owned(candidate)) disposeQuietly(candidate);
                throw failure;
            }
            effects.put(key,entry); // The single commit point, after all fallible readiness work.
            if (old!=null) disposeQuietly(old.effect);
            return id;
        } finally { mutating=false; }
    }
    private boolean owned(DynamicEffect effect) { return effects.values().stream().anyMatch(e->e.effect==effect); }
    private void disposeQuietly(DynamicEffect effect) {
        try {effect.dispose();} catch (RuntimeException failure) {disposeFailures++;}
    }
    public void stop(UUID owner, EffectKind kind) {
        begin();try {Entry e=effects.remove(new Key(owner,kind));if(e!=null)disposeQuietly(e.effect);} finally {mutating=false;}
        scheduler.effectsChanged();
    }
    public void stopOwner(UUID owner) {
        begin();try {effects.entrySet().removeIf(en->{if(en.getKey().owner().equals(owner)){disposeQuietly(en.getValue().effect);return true;}return false;});}
        finally {mutating=false;}scheduler.effectsChanged();
    }
    public void stopAll() {
        begin();try {effects.values().forEach(e->disposeQuietly(e.effect));effects.clear();} finally {mutating=false;}
        scheduler.effectsChanged();
    }
    public Optional<DynamicEffect> effect(UUID owner, EffectKind kind) {
        thread.check();return Optional.ofNullable(effects.get(new Key(owner,kind))).map(e->e.effect);
    }
    public int countOwner(UUID owner) {thread.check();return (int)effects.keySet().stream().filter(k->k.owner().equals(owner)).count();}
    public long failedStarts() {thread.check();return failedStarts;}
    public long disposeFailures() {thread.check();return disposeFailures;}
    public long identityViolations() {thread.check();return identityViolations;}
    public long updateFailures() {thread.check();return updateFailures;}
    public long rejections(AnchorRejection reason) {thread.check();return rejections.getOrDefault(reason,0L);}
    public Map<AnchorRejection,Long> rejections(UUID owner, EffectKind kind) {
        thread.check();Entry e=effects.get(new Key(owner,kind));return e==null?Map.of():Map.copyOf(e.rejections);
    }
    @Override public List<EffectDraft> drafts(long tick) {
        begin();
        try {
            if (closed) return List.of();
            List<EffectDraft> out=new ArrayList<>();
            List<Map.Entry<Key,Entry>> ordered=new ArrayList<>(effects.entrySet());
            ordered.sort(Comparator.comparingLong(en->en.getValue().id));
            for (var en:ordered) {
                Entry e=en.getValue();
                try {
                    AnchorSnapshot snapshot=anchors.snapshot(e.binding.owner(),tick);
                    AnchorVerdict verdict=identity.validate(snapshot,e.binding,tick);
                    if (verdict.state()!=AnchorState.READY) {
                        e.rejections.merge(verdict.reason(),1L,Long::sum);rejections.merge(verdict.reason(),1L,Long::sum);
                        if (verdict.state()==AnchorState.TERMINATE || ++e.invalidRun>=invalidLimit || e.effect.finished(tick)) {
                            effects.remove(en.getKey());disposeQuietly(e.effect);
                        }
                        continue;
                    }
                    e.invalidRun=0;
                    Optional<EffectDraft> draft=Objects.requireNonNull(e.effect.contribute(tick,snapshot));
                    boolean forged=draft.isPresent() && (!matches(draft.get(),en.getKey(),e,snapshot)
                            || !DraftIdentityGuard.accepts(draft.get(),ids,tick));
                    if (forged) identityViolations++;
                    if (forged || e.effect.finished(tick)) {effects.remove(en.getKey());disposeQuietly(e.effect);continue;}
                    draft.ifPresent(out::add);
                } catch (RuntimeException failure) {
                    updateFailures++;effects.remove(en.getKey());disposeQuietly(e.effect);
                }
            }
            return List.copyOf(out);
        } finally {mutating=false;}
    }
    private static boolean matches(EffectDraft d, Key key, Entry entry, AnchorSnapshot snapshot) {
        return d.requestId()==entry.id && d.kind()==key.kind() && entry.binding.owner().equals(d.owner())
            && d.ownerViewerId()==entry.binding.ownerViewerId() && d.worldId()==snapshot.worldId();
    }
    @Override public boolean active() {thread.check();return !effects.isEmpty();}
    @Override public int activeCount() {thread.check();return effects.size();}
    @Override public void close() {
        thread.check();if(closed)return;
        begin();try {closed=true;effects.values().forEach(e->disposeQuietly(e.effect));effects.clear();} finally {mutating=false;}
        scheduler.effectsChanged();
    }
}
