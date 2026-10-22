package workshop.vfx.paper.streams;
import workshop.vfx.paper.reference.*;
import workshop.vfx.paper.m8.*;
import java.util.*;
import java.util.function.UnaryOperator;
import workshop.vfx.delivery.*;
import workshop.vfx.math.core.*;
import workshop.vfx.paper.core.*;
import workshop.vfx.scene.*;

/** Exercises real aura, registry, driver, planner and emitter. No visual or security behaviour is faked. */
final class StreamsFixture {
    static final UUID A=new UUID(0,1), B=new UUID(0,2), C=new UUID(0,3), W=new UUID(0,4), W2=new UUID(0,5);
    final ThreadGuard thread=new ThreadGuard();
    final DefaultSessionIds ids=new DefaultSessionIds(thread);
    final Map<UUID,double[]> position=new HashMap<>();
    final Map<UUID,UUID> worlds=new HashMap<>();
    final Set<UUID> offline=new HashSet<>(), dead=new HashSet<>(), noUse=new HashSet<>(), canView=new HashSet<>();
    final List<UUID> players=new ArrayList<>(List.of(A,B));
    final SessionStyleHealth health=new SessionStyleHealth(StreamsStyleCatalog.whitelist().keySet(),thread);
    final DynamicEffectRegistry registry;
    final AuraSession auras;
    final ReferenceSession reference;
    final StreamsSession streams;
    final TimedAnchorSource snapshots;
    final RingDriver driver;
    final DemoConfig config;
    long tick=100;int schedules,cancels,plans,reads;
    Runnable callback,afterPlan=()->{};
    boolean failSchedule,failCyan,failRod;
    UnaryOperator<AnchorSnapshot> tamper=UnaryOperator.identity();
    List<DeliveryRequest> requests=List.of();DeliveryPlan plan;
    record Send(long viewer,int world,StyleKey style,double x,double y,double z){}
    final List<Send> sends=new ArrayList<>();
    record Directed(long viewer,int world,StyleKey style,Vec3 origin,DirectionalEmission motion){}
    final List<Directed> directional=new ArrayList<>();
    boolean failDirection;
    StreamsFixture(){this(Map.of(),Map.of());}
    StreamsFixture(Map<String,Object> settings,Map<String,Object> m8){
        config=DemoConfig.from(settings);
        var m=new HashMap<String,Object>(Map.of("enabled",true));m.putAll(m8);M8Config mc=M8Config.from(m);
        for(UUID p:List.of(A,B,C)){position.put(p,new double[]{0,64,0,0,0,0});worlds.put(p,W);}
        snapshots=new TimedAnchorSource(thread,this::snapshot,System::nanoTime);
        registry=new DynamicEffectRegistry(thread,snapshots,ids,mc.maxEffectsPerPlayer(),mc.invalidAnchorTicksToTerminate());
        RingDriver.Host host=new RingDriver.Host(){
            public long tick(){return tick;}public long nanoTime(){return System.nanoTime();}
            public boolean worldReady(UUID w,double x,double y,double z){return true;}
            public ViewerFacts facts(UUID p){
                if(p==null||offline.contains(p)||!position.containsKey(p))return null;
                double[] q=position.get(p);return new ViewerFacts(true,true,ids.worldId(worlds.get(p)),q[0],q[1],q[2],!noUse.contains(p),canView.contains(p));
            }
            public List<UUID> players(){return List.copyOf(players);}
            public Runnable schedule(Runnable body){callback=body;if(failSchedule)throw new IllegalStateException("injected refusal");schedules++;return ()->cancels++;}
        };
        driver=new RingDriver(thread,host,config,ids,health,(w,x,y,z)->Optional.empty(),new ParticleSink(){
            public void send(long v,int w,StyleKey s,double x,double y,double z){sends.add(new Send(v,w,s,x,y,z));}
            public void sendDirectional(long v,int w,StyleKey s,double x,double y,double z,DirectionalEmission m){
                if(failDirection)throw new IllegalStateException("injected directional failure");
                directional.add(new Directed(v,w,s,new Vec3(x,y,z),m));
            }
        },(t,r,s,p)->{plans++;requests=r;plan=DeliveryPlanner.plan(t,r,s,p);afterPlan.run();return plan;},registry);
        registry.bind(driver);auras=new AuraSession(thread,ids,snapshots,registry,mc);reference=new ReferenceSession(thread,ids,snapshots,registry,mc);streams=new StreamsSession(thread,ids,snapshots,registry,mc);
    }
    AnchorSnapshot snapshot(UUID owner,long t){
        reads++;if(offline.contains(owner))return null;double[] p=position.get(owner);UUID world=worlds.get(owner);
        return tamper.apply(new AnchorSnapshot(t,owner,ids.existingViewerId(owner).orElse(-1),world,ids.worldId(world),
            p[0],p[1],p[2],(float)p[3],(float)p[4],(float)p[5],-64,320,true,true,true,dead.contains(owner),0));
    }
    long start(StreamSpeed speed){return streams.start(A,speed,tick);}
    StreamsEffect current(){return (StreamsEffect)registry.effect(A,EffectKind.STREAMS).orElseThrow();}
    void run(){sends.clear();directional.clear();callback.run();}
    void step(){tick++;run();}
    void ring(UUID owner){driver.start(owner,worlds.get(owner),0,64,0,QualityTier.BALANCED);}
    EffectDraft draft(UUID owner){return registry.drafts(tick).stream().filter(d->d.owner().equals(owner)).findFirst().orElseThrow();}
    static EffectFrame frame(EffectDraft d){return d.recipe().variant(d.tier()).evaluate(d.anchor(),d.playback(),d.now());}
    long sendsTo(UUID p){long id=ids.viewerId(p);return sends.stream().filter(s->s.viewer()==id).count();}
    void normal(){start(StreamSpeed.NORMAL);}
    void account(){EmissionReport r=driver.lastReport();org.junit.jupiter.api.Assertions.assertEquals(r.plannedPlacements(),
        r.attemptedCalls()+r.skippedPoints().values().stream().mapToLong(Long::longValue).sum()+r.placementsInSkippedRecipients()+r.placementsInSkippedDeliveries());
        org.junit.jupiter.api.Assertions.assertEquals(r.attemptedCalls(),r.succeededCalls()+r.failedCalls());}
    static AnchorSnapshot replace(AnchorSnapshot s,Long tick,UUID owner,Long viewer,UUID world,Integer worldId,Double x,Double y,Double z,Float body,Float head,Boolean loaded){
        return new AnchorSnapshot(tick==null?s.tick():tick,owner==null?s.owner():owner,viewer==null?s.ownerViewerId():viewer,
            world==null?s.world():world,worldId==null?s.worldId():worldId,x==null?s.x():x,y==null?s.y():y,z==null?s.z():z,
            body==null?s.bodyYawDegrees():body,head==null?s.headYawDegrees():head,s.pitchDegrees(),s.minHeight(),s.maxHeight(),
            loaded==null?s.chunkLoaded():loaded,s.online(),s.valid(),s.dead(),s.discontinuitySerial());
    }
}
