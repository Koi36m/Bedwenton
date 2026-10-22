package workshop.vfx.paper.reference;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import workshop.vfx.delivery.*;
import workshop.vfx.math.core.*;
import workshop.vfx.math.time.*;
import workshop.vfx.paper.core.ThreadGuard;
import workshop.vfx.paper.m8.*;

/** Independent experimental registration, sharing the exact K0 identity, planning and lifecycle path. */
public final class ReferenceEffect implements DynamicEffect {
    private final ThreadGuard thread;
    private final AnchorIdentityGuard guard;
    private final AnchorBinding binding;
    private final long id,start;
    private final Playback playback;
    private final List<EffectRecipe> recipes;
    private final QualityTier tier;
    private boolean disposed;
    ReferenceEffect(ThreadGuard thread,AnchorIdentityGuard guard,AnchorBinding binding,long id,long start,
                    List<EffectRecipe> recipes,QualityTier tier) {
        this.thread=Objects.requireNonNull(thread);this.guard=Objects.requireNonNull(guard);this.binding=Objects.requireNonNull(binding);
        this.recipes=Objects.requireNonNull(recipes);this.tier=Objects.requireNonNull(tier);
        if(id<DynamicEffectRegistry.FIRST_ID || recipes.size()!=16)throw new IllegalArgumentException("Invalid reference effect");
        this.id=id;this.start=start;playback=Playback.start(TimePoint.gameTick(start));
    }
    @Override public long requestId(){thread.check();return id;}
    @Override public UUID owner(){thread.check();return binding.owner();}
    @Override public EffectKind kind(){thread.check();return EffectKind.REFERENCE;}
    @Override public boolean finished(long tick){thread.check();return disposed || tick<start || tick-start>=ReferenceRecipe.LIFETIME;}
    @Override public Optional<EffectDraft> contribute(long tick,AnchorSnapshot snapshot) {
        thread.check();if(finished(tick)||guard.validate(snapshot,binding,tick).state()!=AnchorState.READY)return Optional.empty();
        double half=-StrictMath.toRadians(snapshot.bodyYawDegrees())/2;
        Pose anchor=new Pose(new Vec3(snapshot.x(),snapshot.y(),snapshot.z()),new Quat(StrictMath.cos(half),0,StrictMath.sin(half),0),1);
        long age=tick-start;
        return Optional.of(new EffectDraft(EffectKind.REFERENCE,id,binding.owner(),binding.ownerViewerId(),snapshot.worldId(),
            anchor,recipes.get((int)(age%16)),tier,playback,TimePoint.gameTick(tick),age));
    }
    @Override public void dispose(){thread.check();disposed=true;}
}
