package workshop.vfx.paper.streams;

import java.util.List;
import java.util.Map;
import workshop.vfx.delivery.*;
import workshop.vfx.math.core.*;
import workshop.vfx.math.time.*;
import workshop.vfx.paper.core.DirectionalEmission;
import workshop.vfx.scene.*;

/** Exactly two M6-budgeted spawn origins; no plotted circle, curve or trail geometry. */
public final class StreamsRecipe {
    private StreamsRecipe(){ }
    public static EffectRecipe create(){
        var point=Shape.point(Vec3.ZERO);var origin=Motion.fixed(Pose.ofTranslation(new Vec3(0,1.55,-1)));
        var a=new Layer("stream-a",Timing.once(200),Visibility.ACTIVE_ONLY,Reveal.ALL,origin,point,DirectionalEmission.A);
        var b=new Layer("stream-b",Timing.once(200),Visibility.ACTIVE_ONLY,Reveal.ALL,origin,point,DirectionalEmission.B);
        return EffectRecipe.of("bedwenton-dual-streams",EffectPriority.NORMAL,
            Map.of(QualityTier.SIMPLE,EffectScene.of(TimeDomain.GAME,200,List.of(a,b),new SceneLimits(2,2))),
            Map.of("stream-a",LayerRole.ESSENTIAL,"stream-b",LayerRole.ESSENTIAL));
    }
}
