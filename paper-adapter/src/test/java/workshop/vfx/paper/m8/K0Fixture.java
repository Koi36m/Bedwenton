package workshop.vfx.paper.m8;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import workshop.vfx.delivery.*;
import workshop.vfx.math.core.*;
import workshop.vfx.math.time.*;
import workshop.vfx.paper.core.*;
import workshop.vfx.scene.*;

/** Scripted K0 effects only. This fixture is not an Aura, Trail or Activation implementation. */
final class K0Fixture {
    static final UUID A=new UUID(0,1), B=new UUID(0,2), WORLD=new UUID(0,3), WORLD2=new UUID(0,4), C=new UUID(0,5);
    static final Set<StyleKey> STYLES=DemoStyleCatalog.whitelist().keySet();
    final ThreadGuard thread=new ThreadGuard();
    final DefaultSessionIds ids=new DefaultSessionIds(thread);
    final Map<UUID,double[]> positions=new HashMap<>();
    final Map<UUID,UUID> worlds=new HashMap<>();
    final Set<UUID> offline=new HashSet<>(),unloadedChunk=new HashSet<>();
    final List<UUID> players=new ArrayList<>(List.of(A,B));
    UnaryOperator<AnchorSnapshot> tamper;
    long tick=100,nanos;
    boolean failNextSchedule;
    int schedules,cancels,plans,scheduleFailures;
    Runnable callback;
    List<DeliveryRequest> lastRequests=List.of();
    DeliveryPlan lastPlan;
    final List<long[]> sends=new ArrayList<>();
    final DynamicEffectRegistry registry;
    final RingDriver driver;
    final AnchorIdentityGuard rules=new AnchorIdentityGuard(ids,thread);
    K0Fixture(){this(Map.of(),2);}
    K0Fixture(Map<String,Object> settings,int perPlayer){this(settings,perPlayer,null);}
    K0Fixture(Map<String,Object> settings,int perPlayer,TickContributor contributor){
        for(UUID p:List.of(A,B,C)){positions.put(p,new double[]{0.5,64,0.5,0});worlds.put(p,WORLD);}
        registry=new DynamicEffectRegistry(thread,this::snapshot,ids,perPlayer,20);
        RingDriver.Host host=new RingDriver.Host(){
            public long tick(){return tick;}public long nanoTime(){return nanos+=10;}
            public boolean worldReady(UUID w,double x,double y,double z){return true;}
            public ViewerFacts facts(UUID p){
                if(offline.contains(p)||!positions.containsKey(p))return null;
                double[] q=positions.get(p);return new ViewerFacts(true,true,ids.worldId(worlds.get(p)),q[0],q[1],q[2],true,false);
            }
            public List<UUID> players(){return List.copyOf(players);}
            public Runnable schedule(Runnable body){
                if(failNextSchedule){failNextSchedule=false;scheduleFailures++;callback=body;throw new IllegalStateException("injected scheduler refusal");}
                callback=body;schedules++;return ()->cancels++;
            }
        };
        driver=new RingDriver(thread,host,DemoConfig.from(settings),ids,new SessionStyleHealth(STYLES,thread),
            (w,x,y,z)->Optional.empty(),(v,w,s,x,y,z)->sends.add(new long[]{v,w}),
            (t,requests,styles,policy)->{plans++;lastRequests=requests;lastPlan=DeliveryPlanner.plan(t,requests,styles,policy);return lastPlan;},
            contributor==null?registry:contributor);
        registry.bind(driver);
    }
    AnchorSnapshot honest(UUID owner,long tick){
        if(offline.contains(owner))return null;
        double[] q=positions.get(owner);UUID world=worlds.get(owner);
        return new AnchorSnapshot(tick,owner,ids.existingViewerId(owner).orElse(-1),world,ids.worldId(world),
            q[0],q[1],q[2],(float)q[3],(float)q[3],0,-64,320,!unloadedChunk.contains(owner),true,true,false,0);
    }
    AnchorSnapshot snapshot(UUID owner,long tick){var s=honest(owner,tick);return tamper==null?s:tamper.apply(s);}
    void ring(UUID owner){driver.start(owner,WORLD,0.5,64.1,0.5,QualityTier.BALANCED);}
    long start(UUID owner,EffectKind kind,long lifetime){return registry.start(owner,kind,(id,b)->new FakeEffect(id,b,tick,lifetime,kind));}
    long aura(UUID owner){return start(owner,EffectKind.AURA,1200);}
    long trail(UUID owner){return start(owner,EffectKind.TRAIL,1200);}
    long activation(UUID owner){return start(owner,EffectKind.ACTIVATION,80);}
    FakeEffect current(UUID owner,EffectKind kind){return (FakeEffect)registry.effect(owner,kind).orElseThrow();}
    void run(){callback.run();}void step(){tick++;run();}
    long sendsTo(UUID owner){long id=ids.viewerId(owner);return sends.stream().filter(s->s[0]==id).count();}
    /** 27 existing-style points keep the design scheduling assertions; no visual recipe enters production. */
    static EffectRecipe probeRecipe(){
        List<Vec3> points=new ArrayList<>();for(int i=0;i<27;i++)points.add(new Vec3(i/100.0,0,0));
        Layer layer=new Layer("probe",Timing.loop(40),Visibility.ACTIVE_ONLY,Reveal.ALL,Motion.IDENTITY,Shape.points(points),DemoStyleCatalog.END_ROD);
        return EffectRecipe.of("k0-test-probe",EffectPriority.NORMAL,
            Map.of(QualityTier.BALANCED,EffectScene.of(TimeDomain.GAME,1200,List.of(layer),new SceneLimits(1,27))),Map.of("probe",LayerRole.ESSENTIAL));
    }
    static class FakeEffect implements DynamicEffect {
        final long id,start,lifetime;final AnchorBinding binding;final EffectKind kind;
        boolean emit=true,disposed;int disposals,contributions;Runnable disposal=()->{};
        BiFunction<Long,AnchorSnapshot,Optional<EffectDraft>> producer;
        FakeEffect(long id,AnchorBinding binding,long start,long lifetime,EffectKind kind){this.id=id;this.binding=binding;this.start=start;this.lifetime=lifetime;this.kind=kind;}
        public long requestId(){return id;}public UUID owner(){return binding.owner();}public EffectKind kind(){return kind;}
        public Optional<EffectDraft> contribute(long tick,AnchorSnapshot s){
            contributions++;if(finished(tick)||!emit)return Optional.empty();
            return producer==null?draft(tick,s):producer.apply(tick,s);
        }
        protected Optional<EffectDraft> draft(long tick,AnchorSnapshot s){
            return Optional.of(new EffectDraft(kind,id,binding.owner(),binding.ownerViewerId(),s.worldId(),
                Pose.ofTranslation(new Vec3(s.x(),s.y(),s.z())),probeRecipe(),QualityTier.BALANCED,
                Playback.start(TimePoint.gameTick(start)),TimePoint.gameTick(tick),tick-start));
        }
        public boolean finished(long tick){return disposed||tick-start>=lifetime;}
        public void dispose(){if(disposed)return;disposed=true;disposals++;onDispose();}
        protected void onDispose(){disposal.run();}
        boolean disposed(){return disposed;}
    }
}
