package workshop.vfx.paper.reference;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import workshop.vfx.delivery.*;
import workshop.vfx.math.core.*;
import workshop.vfx.math.curve.*;
import workshop.vfx.math.sample.*;
import workshop.vfx.math.time.*;
import workshop.vfx.paper.core.DemoStyleCatalog;
import workshop.vfx.scene.*;

/** Immutable, pre-sampled ten-second prototype. All cadence variants reuse the same layer/shape objects. */
public final class ReferenceRecipe {
    public static final int LIFETIME = 200;
    private ReferenceRecipe() { }
    private static Vec3 xy(double x, double y) { return new Vec3(x,y,0); }
    private static Shape circle(double radius, int count) {
        return Shape.of(Sampler.uniformParameter(Spiral.circle(radius),count,Endpoints.EXCLUDE_END,new SampleLimits(72,256)));
    }
    private static Layer layer(String name, Timing timing, Reveal reveal, Motion motion, Shape shape, StyleKey style) {
        return new Layer(name,timing,Visibility.HOLD_AFTER,reveal,motion,shape,style);
    }
    private static List<Layer> layers(int outer, int perCurve, int crown, int stars, int ground, int ambient) {
        var result = new ArrayList<Layer>();
        double a=Math.PI/4;
        Motion disc=Motion.fixed(new Pose(new Vec3(0,1.55,-1.0),new Quat(StrictMath.cos(a),StrictMath.sin(a),0,0),1));
        result.add(layer("disc",Timing.once(64).withDelay(36),Reveal.PREFIX,disc,circle(1.3,outer),DemoStyleCatalog.END_ROD));
        // A continuous four-cubic inward comma/spiral; no control point reaches the outer ring.
        CubicBezier[] curves={
            new CubicBezier(xy(-1.08,.12),xy(-.94,-.84),xy(.92,-1.05),xy(.96,-.18)),
            new CubicBezier(xy(.96,-.18),xy(1.08,.70),xy(.02,1.14),xy(-.48,.52)),
            new CubicBezier(xy(-.48,.52),xy(-.86,.02),xy(.12,-.34),xy(.28,.05)),
            new CubicBezier(xy(.28,.05),xy(.38,.36),xy(-.14,.48),xy(-.22,.22))};
        Motion spin=Motion.fixed(Pose.ofTranslation(new Vec3(0,1.55,-1.0)))
            .then(Motion.spin(Vec3.Z,1,0,Timing.loop(80).withDelay(36),Easing.LINEAR));
        for(int i=0;i<curves.length;i++) {
            // Include each segment end; omit shared starting junctions, except the first segment's start.
            Shape shape=Shape.of(Sampler.uniformParameter(curves[i],i==0?0:1.0/perCurve,1,perCurve,
                Endpoints.INCLUDE_END,new SampleLimits(72,256)));
            result.add(layer("spiral-"+i,Timing.once(14).withDelay(44+14*i),Reveal.PREFIX,spin,shape,DemoStyleCatalog.END_ROD));
        }
        var tips=new ArrayList<Vec3>();Shape crownRing=circle(.43,crown);
        for(int i=0;i<crown;i++){var p=crownRing.point(i);tips.add(new Vec3(p.x(),i%2==0?.10:0,p.z()));}
        Motion head=Motion.fixed(Pose.ofTranslation(new Vec3(0,2.35,0)))
            .then(Motion.translate(Vec3.ZERO,new Vec3(0,.18,0),Timing.pingPong(40).withDelay(20),Easing.SMOOTHSTEP))
            .then(Motion.spin(Vec3.Y,1,0,Timing.loop(120).withDelay(20),Easing.LINEAR));
        result.add(layer("crown",Timing.once(180).withDelay(20),Reveal.ALL,head,Shape.points(tips),ReferenceStyleCatalog.LIME));
        Vec3[] arms={xy(-.09,0),xy(.09,0),xy(0,-.14),xy(0,.14)};
        var star=new ArrayList<Vec3>();
        for(int i=0;i<stars;i++)star.add(stars==1?Vec3.ZERO:arms[i]);
        Shape starShape=Shape.points(star);
        result.add(layer("star-white",Timing.once(160).withDelay(40),Reveal.ALL,
            Motion.fixed(Pose.ofTranslation(new Vec3(-.65,2.55,.05))),starShape,ReferenceStyleCatalog.WHITE));
        result.add(layer("star-gold",Timing.once(160).withDelay(40),Reveal.ALL,
            Motion.fixed(Pose.ofTranslation(new Vec3(.62,2.75,-.10))),starShape,ReferenceStyleCatalog.GOLD));
        Motion feet=Motion.fixed(Pose.ofTranslation(new Vec3(0,.08,0)))
            .then(Motion.scale(.28,1.05,Timing.loop(40).withDelay(20),Easing.LINEAR));
        result.add(layer("ground",Timing.once(180).withDelay(20),Reveal.ALL,feet,circle(1,ground),ReferenceStyleCatalog.GOLD));
        var motes=new ArrayList<Vec3>();
        for(int i=0;i<ambient;i++) {
            double angle=2*Math.PI*(i+.17)/ambient;
            motes.add(new Vec3(.95*StrictMath.cos(angle),.45+.32*i,.95*StrictMath.sin(angle)));
        }
        Motion rise=Motion.translate(Vec3.ZERO,new Vec3(0,.45,0),Timing.pingPong(80),Easing.LINEAR);
        result.add(layer("ambient",Timing.once(200),Reveal.ALL,rise,Shape.points(motes),ReferenceStyleCatalog.WARM));
        return List.copyOf(result);
    }
    private static boolean due(String name, int phase) {
        if(name.equals("crown"))return phase%2==0;
        if(name.equals("star-white"))return phase==0;
        if(name.equals("star-gold"))return phase==8;
        if(name.equals("ambient"))return phase%8==0;
        return phase%4==0; // white disc, white motif and sparse ground ring
    }
    /** Sixteen shared cadence entries; scene time is always the original effect clock, never phase modulo sixteen. */
    public static List<EffectRecipe> create() {
        var full=new EnumMap<QualityTier,List<Layer>>(QualityTier.class);
        full.put(QualityTier.SIMPLE,layers(8,2,3,1,2,1));
        full.put(QualityTier.BALANCED,layers(18,3,6,3,4,2));
        full.put(QualityTier.HEAVY,layers(24,5,8,4,8,4));
        Map<List<String>,EffectRecipe> cache=new HashMap<>();var frames=new ArrayList<EffectRecipe>();
        for(int phase=0;phase<16;phase++) {
            final int p=phase;var names=full.get(QualityTier.HEAVY).stream().filter(l->due(l.name(),p)).map(Layer::name).toList();
            frames.add(cache.computeIfAbsent(names,key->{
                var variants=new EnumMap<QualityTier,EffectScene>(QualityTier.class);var roles=new HashMap<String,LayerRole>();
                for(var tier:QualityTier.values()) {
                    var selected=full.get(tier).stream().filter(l->key.contains(l.name())).toList();
                    variants.put(tier,EffectScene.of(TimeDomain.GAME,LIFETIME,selected,new SceneLimits(10,72)));
                    for(var l:selected)roles.put(l.name(),l.name().equals("disc")||l.name().equals("crown")?LayerRole.ESSENTIAL:
                        l.name().startsWith("spiral-")?LayerRole.STANDARD:LayerRole.DECORATIVE);
                }
                return EffectRecipe.of("bedwenton-reference-10s",EffectPriority.NORMAL,variants,roles);
            }));
        }
        return List.copyOf(frames);
    }
}
