package workshop.vfx.paper.streams;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import workshop.vfx.delivery.*;
import workshop.vfx.math.core.*;
import workshop.vfx.math.time.*;
import workshop.vfx.paper.core.*;
import workshop.vfx.paper.m8.*;

public final class StreamsEffect implements DynamicEffect {
    private final ThreadGuard thread;
    private final AnchorIdentityGuard guard;
    private final AnchorBinding binding;
    private final long id,start;
    private final EffectRecipe recipe;
    private final StreamSpeed speed;
    private final Playback playback;
    private boolean disposed;
    StreamsEffect(ThreadGuard thread,AnchorIdentityGuard guard,AnchorBinding binding,long id,long start,EffectRecipe recipe,StreamSpeed speed){
        this.thread=Objects.requireNonNull(thread);this.guard=Objects.requireNonNull(guard);this.binding=Objects.requireNonNull(binding);
        this.recipe=Objects.requireNonNull(recipe);this.speed=Objects.requireNonNull(speed);this.id=id;this.start=start;
        playback=Playback.start(TimePoint.gameTick(start));
    }
    public static Vec3 directionA(long age){
        if(age<0)throw new IllegalArgumentException("Negative stream age");
        double theta=Math.PI/2+2*Math.PI*(age%80)/80;
        return new Vec3(StrictMath.cos(theta),StrictMath.sin(theta),0);
    }
    @Override public long requestId(){thread.check();return id;}
    @Override public UUID owner(){thread.check();return binding.owner();}
    @Override public EffectKind kind(){thread.check();return EffectKind.STREAMS;}
    @Override public boolean finished(long tick){thread.check();return disposed||tick<start||tick-start>=200;}
    @Override public Optional<EffectDraft> contribute(long tick,AnchorSnapshot snapshot){
        thread.check();if(finished(tick)||guard.validate(snapshot,binding,tick).state()!=AnchorState.READY)return Optional.empty();
        long age=tick-start;if(age%4!=0)return Optional.empty();
        double half=-StrictMath.toRadians(snapshot.bodyYawDegrees())/2;
        var yaw=new Quat(StrictMath.cos(half),0,StrictMath.sin(half),0);
        var anchor=new Pose(new Vec3(snapshot.x(),snapshot.y(),snapshot.z()),yaw,1);
        var a=yaw.rotate(directionA(age));
        var directions=Map.of(DirectionalEmission.A,new DirectionalEmission(a,speed.value()),
            DirectionalEmission.B,new DirectionalEmission(a.mul(-1),speed.value()));
        return Optional.of(new EffectDraft(EffectKind.STREAMS,id,binding.owner(),binding.ownerViewerId(),snapshot.worldId(),
            anchor,recipe,QualityTier.SIMPLE,playback,TimePoint.gameTick(tick),age,directions));
    }
    @Override public void dispose(){thread.check();disposed=true;}
}
