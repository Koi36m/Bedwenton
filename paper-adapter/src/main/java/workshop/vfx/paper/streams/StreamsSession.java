package workshop.vfx.paper.streams;

import java.util.Objects;
import java.util.UUID;
import workshop.vfx.delivery.EffectRecipe;
import workshop.vfx.paper.core.*;
import workshop.vfx.paper.m8.*;

public final class StreamsSession {
    private final ThreadGuard thread;
    private final SessionIds ids;
    private final AnchorSource anchors;
    private final AnchorIdentityGuard guard;
    private final DynamicEffectRegistry registry;
    private final M8Config config;
    private final EffectRecipe recipe;
    public StreamsSession(ThreadGuard thread,SessionIds ids,AnchorSource anchors,DynamicEffectRegistry registry,M8Config config){
        this.thread=Objects.requireNonNull(thread);this.ids=Objects.requireNonNull(ids);this.anchors=Objects.requireNonNull(anchors);
        this.registry=Objects.requireNonNull(registry);this.config=Objects.requireNonNull(config);guard=new AnchorIdentityGuard(ids,thread);recipe=StreamsRecipe.create();
    }
    public long start(UUID owner,StreamSpeed speed,long tick){
        thread.check();Objects.requireNonNull(owner);Objects.requireNonNull(speed);
        if(!config.enabled())throw new IllegalArgumentException("M8 is disabled");
        if(speed==StreamSpeed.FAST&&!config.heavyAllowed())throw new IllegalArgumentException("Fast preset requires heavyAllowed");
        var binding=new AnchorBinding(owner,ids.viewerId(owner));
        if(guard.validate(anchors.snapshot(owner,tick),binding,tick).state()!=AnchorState.READY)throw new IllegalArgumentException("Stream anchor unavailable");
        return registry.start(owner,EffectKind.STREAMS,(id,bound)->new StreamsEffect(thread,guard,bound,id,tick,recipe,speed));
    }
    public void stop(UUID owner){thread.check();registry.stop(owner,EffectKind.STREAMS);}
    public String status(UUID owner){thread.check();return registry.effect(owner,EffectKind.STREAMS).isPresent()?"streams=running (200 ticks)":"streams=off";}
}
