package workshop.vfx.paper.m8;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** M8 R3 THROWAWAY REFERENCE (design verification only; never production). */
abstract class AbstractEffect implements DynamicEffect {
    protected final long requestId, startTick, lifetimeTicks;
    protected final AnchorBinding binding;
    protected final UUID owner;
    protected final AnchorRules rules;
    private final int invalidLimit;
    private final Map<AnchorRejection, Integer> rejections = new EnumMap<>(AnchorRejection.class);
    private int invalidRun;
    private boolean terminated, disposed;

    AbstractEffect(long requestId, AnchorBinding binding, long startTick, long lifetimeTicks, AnchorRules rules, int invalidLimit) {
        this.requestId = requestId; this.binding = Objects.requireNonNull(binding); this.owner = binding.owner();
        this.startTick = startTick; this.lifetimeTicks = lifetimeTicks; this.rules = Objects.requireNonNull(rules);
        this.invalidLimit = invalidLimit;
    }
    @Override public long requestId() { return requestId; }
    @Override public UUID owner() { return owner; }
    /** The effect clock (F3): local ticks since the effect started. */
    protected long age(long tick) { return tick - startTick; }
    /** R3 F6: the snapshot is validated against the binding and the planning tick before any of its fields is read. */
    @Override public final Optional<EffectDraft> contribute(long tick, AnchorSnapshot s) {
        if (disposed || terminated || age(tick) >= lifetimeTicks || age(tick) < 0) return Optional.empty();
        AnchorVerdict v = rules.validate(s, binding, tick);
        if (v.state() != AnchorState.READY) rejections.merge(v.reason(), 1, Integer::sum);
        if (v.state() == AnchorState.TERMINATE) { terminated = true; return Optional.empty(); }
        if (v.state() == AnchorState.SKIP_TICK) { if (++invalidRun >= invalidLimit) terminated = true; return Optional.empty(); }
        invalidRun = 0;
        return draft(tick, s);
    }
    /** Builds the draft from a READY snapshot; owner and owner viewer id always come from {@link #binding}. */
    protected abstract Optional<EffectDraft> draft(long tick, AnchorSnapshot s);
    public int rejections(AnchorRejection reason) { return rejections.getOrDefault(reason, 0); }
    public boolean disposed() { return disposed; }
    @Override public boolean finished(long tick) { return disposed || terminated || age(tick) >= lifetimeTicks; }
    @Override public void dispose() { if (disposed) return; disposed = true; onDispose(); }
    protected void onDispose() { }
}
