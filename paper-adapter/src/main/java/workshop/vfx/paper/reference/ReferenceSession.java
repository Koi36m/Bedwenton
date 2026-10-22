package workshop.vfx.paper.reference;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import workshop.vfx.delivery.*;
import workshop.vfx.paper.core.*;
import workshop.vfx.paper.m8.*;

/** Validates all fallible readiness before the existing transactional registry replacement. No automatic starts. */
public final class ReferenceSession {
    private final ThreadGuard thread;
    private final SessionIds ids;
    private final AnchorSource anchors;
    private final AnchorIdentityGuard guard;
    private final DynamicEffectRegistry registry;
    private final M8Config config;
    private final List<EffectRecipe> recipes;
    public ReferenceSession(ThreadGuard thread,SessionIds ids,AnchorSource anchors,DynamicEffectRegistry registry,M8Config config) {
        this.thread=Objects.requireNonNull(thread);this.ids=Objects.requireNonNull(ids);this.anchors=Objects.requireNonNull(anchors);
        this.registry=Objects.requireNonNull(registry);this.config=Objects.requireNonNull(config);
        guard=new AnchorIdentityGuard(ids,thread);recipes=ReferenceRecipe.create();
    }
    public long start(UUID owner,QualityTier tier,long tick) {
        thread.check();Objects.requireNonNull(owner);Objects.requireNonNull(tier);
        if(!config.enabled())throw new IllegalArgumentException("M8 is disabled in configuration");
        if(tier==QualityTier.HEAVY&&!config.heavyAllowed())throw new IllegalArgumentException("Heavy tier is disabled");
        var binding=new AnchorBinding(owner,ids.viewerId(owner));
        if(guard.validate(anchors.snapshot(owner,tick),binding,tick).state()!=AnchorState.READY)
            throw new IllegalArgumentException("Reference anchor unavailable");
        return registry.start(owner,EffectKind.REFERENCE,(id,bound)->new ReferenceEffect(thread,guard,bound,id,tick,recipes,tier));
    }
    public void stop(UUID owner){thread.check();registry.stop(owner,EffectKind.REFERENCE);}
    public String status(UUID owner){thread.check();return registry.effect(owner,EffectKind.REFERENCE).isPresent()?"reference=running (200 ticks)":"reference=off";}
}
