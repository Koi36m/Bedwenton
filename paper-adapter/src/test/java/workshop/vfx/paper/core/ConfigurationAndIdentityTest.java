package workshop.vfx.paper.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import workshop.vfx.scene.StyleKey;

class ConfigurationAndIdentityTest {
    @Test void allIntegerBoundariesAndHardCeilings() {
        Map<String,int[]> ranges=Map.ofEntries(
            Map.entry("maxActiveEffects",new int[]{1,16}),Map.entry("maxViewersPerEffect",new int[]{1,16}),
            Map.entry("ringLifetimeTicks",new int[]{20,6000}),Map.entry("generatedPointBudget",new int[]{0,1152}),
            Map.entry("placementBudget",new int[]{0,4096}),Map.entry("worldPlacementBudget",new int[]{0,4096}),
            Map.entry("viewerPlacementBudget",new int[]{0,1024}),Map.entry("simpleKeptCap",new int[]{0,72}),
            Map.entry("balancedKeptCap",new int[]{0,72}),Map.entry("heavyKeptCap",new int[]{0,72}),
            Map.entry("maxCallsPerTick",new int[]{1,4096}),Map.entry("maxCallsPerViewerPerTick",new int[]{1,1024}));
        ranges.forEach((key,bounds)-> {
            for(int n: new int[]{bounds[0],bounds[1],bounds[0]-1,bounds[1]+1}) {
                Map<String,Object> m=new HashMap<>();
                if(key.endsWith("KeptCap")) for(String cap:List.of("simpleKeptCap","balancedKeptCap","heavyKeptCap"))
                    m.put(cap,Math.clamp(n,0,72));
                m.put(key,n);
                if(n<bounds[0]||n>bounds[1]) assertThrows(IllegalArgumentException.class,()->DemoConfig.from(m),key);
                else assertDoesNotThrow(()->DemoConfig.from(m).policy(),key);
            }
        });
    }
    @Test void radiusIsFiniteAndBoundedBeforeRules() {
        for(double n:new double[]{Double.NaN,Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY,Math.nextDown(4.0),Math.nextUp(64.0),1e200})
            assertThrows(IllegalArgumentException.class,()->DemoConfig.from(Map.of("viewRadius",n)));
        for(double n:new double[]{4.0,64.0,32.0})assertEquals(n,DemoConfig.from(Map.of("viewRadius",n)).viewRadius());
    }
    @Test void invalidTypesAndCapsRefuseInsteadOfDefaulting() {
        for(Object value:new Object[]{"4",4.1,Double.NaN,Long.MAX_VALUE,true})
            assertThrows(IllegalArgumentException.class,()->DemoConfig.from(Map.of("maxActiveEffects",value)));
        assertThrows(IllegalArgumentException.class,()->DemoConfig.from(Map.of("viewRadius",".nan")));
        assertThrows(IllegalArgumentException.class,()->DemoConfig.from(Map.of("defaultTier","unknown")));
        assertThrows(IllegalArgumentException.class,()->DemoConfig.from(Map.of("debug","false")));
        assertThrows(IllegalArgumentException.class,()->DemoConfig.from(Map.of("simpleKeptCap",49)));
        assertThrows(IllegalArgumentException.class,()->DemoConfig.from(Map.of("heavyKeptCap",47)));
    }
    @Test void budgetsAlignWithoutRaisingRequestedBudgets() {
        DemoConfig config=DemoConfig.from(Map.of("placementBudget",4000,"worldPlacementBudget",1000,"viewerPlacementBudget",800,
            "maxCallsPerTick",100,"maxCallsPerViewerPerTick",25));
        var policy=config.policy();assertEquals(100,policy.placementBudget());assertEquals(100,policy.worldPlacementBudget());
        assertEquals(25,policy.viewerPlacementBudget());assertEquals(16,policy.maxViewerEntries());
        assertEquals(0,policy.ambientGeneratedReserve());assertEquals(0,policy.ambientPlacementReserve());
        assertEquals(0,DemoConfig.from(Map.of("placementBudget",0)).policy().placementBudget());
    }
    @Test void idsUseBothUuidHalvesAndNeverReuseReleasedNumbers() {
        var ids=new DefaultSessionIds(new ThreadGuard()); UUID a=new UUID(1,2),b=new UUID(2,1),c=new UUID(3,0);
        assertEquals(a.hashCode(),b.hashCode());assertEquals(0,ids.viewerId(a));assertEquals(1,ids.viewerId(b));
        assertEquals(2,ids.viewerId(c));assertEquals(a,ids.viewer(0));assertEquals(0,ids.viewerId(a));
        ids.release(a);assertNull(ids.viewer(0));assertTrue(ids.existingViewerId(a).isEmpty());assertEquals(3,ids.viewerId(a));
        assertEquals(0,ids.worldId(a));assertEquals(1,ids.worldId(b));ids.forgetWorld(a);assertNull(ids.world(0));
        assertEquals(2,ids.worldId(a));ids.clear();assertNull(ids.viewer(1));assertNull(ids.world(1));assertEquals(4,ids.viewerId(b));
    }
    @Test void threadOwnershipRefusesCrossThreadAndFalsePrimary() throws Exception {
        var guard=new ThreadGuard();var ids=new DefaultSessionIds(guard);var health=new SessionStyleHealth(DemoFixture.CATALOGUE,guard);
        AtomicReference<Throwable> seen=new AtomicReference<>();Thread other=new Thread(()->{
            try {ids.viewerId(UUID.randomUUID());}catch(Throwable e){seen.set(e);}
        });other.start();other.join();assertInstanceOf(IllegalStateException.class,seen.get());
        var denied=new ThreadGuard(()->false);
        assertThrows(IllegalStateException.class,()->new DefaultSessionIds(denied).worldId(UUID.randomUUID()));
        assertThrows(IllegalStateException.class,()->new SessionStyleHealth(DemoFixture.CATALOGUE,denied).enabledStyles());
        assertEquals(DemoFixture.CATALOGUE,health.enabledStyles());
    }
    @Test void viewerInputMustBeUniqueSortedAndNonnegative() {
        var select=new OwnerFirstViewerSelection();
        for(long[] bad:new long[][]{{1,1},{2,1},{-1,2}})
            assertThrows(IllegalArgumentException.class,()->select.select(9,true,bad,2));
        assertArrayEquals(new long[]{2,9},select.select(9,true,new long[]{2,3,9},2));
        assertArrayEquals(new long[0],select.select(9,true,new long[]{2},0));
    }
    @Test void whitelistAndDustValidationAreClosedAndImmutable() {
        var catalog=new DemoStyleCatalog(Set.of(DemoStyleCatalog.END_ROD));
        assertEquals(Set.of(DemoStyleCatalog.END_ROD),catalog.supportedStyles());assertNull(catalog.spec(DemoStyleCatalog.DUST));
        assertNull(catalog.spec(StyleKey.of("other:unknown")));
        assertThrows(UnsupportedOperationException.class,()->catalog.asMap().clear());
        assertThrows(IllegalArgumentException.class,()->new DemoStyleCatalog(Set.of(StyleKey.of("other:unknown"))));
        for(float n:new float[]{Float.NaN,Float.POSITIVE_INFINITY,0,Math.nextUp(4.0f)})
            assertThrows(IllegalArgumentException.class,()->new ParticleSpec.Dust(0xFF3030,n));
        assertThrows(IllegalArgumentException.class,()->new ParticleSpec.Dust(0x1000000,1));
        assertThrows(IllegalArgumentException.class,()->new ParticleSpec.Dust(-1,1));
        assertThrows(IllegalArgumentException.class,()->new ParticleSpec.Plain("minecraft:dust"));
        assertDoesNotThrow(()->new ParticleSpec.Dust(0,0.01f));assertDoesNotThrow(()->new ParticleSpec.Dust(0xFFFFFF,4));
    }
    @Test void emissionReportsAreDefensiveSnapshots() {
        var map=new EnumMap<SkipReason,Long>(SkipReason.class);map.put(SkipReason.POINT_OUTSIDE_WORLD,2L);
        var report=new EmissionReport(1,2,0,0,0,Map.of(),Map.of(),map,0,0,Set.of(),0,0);map.clear();
        assertEquals(2L,report.skippedPoints().get(SkipReason.POINT_OUTSIDE_WORLD));
        assertThrows(UnsupportedOperationException.class,()->report.skippedPoints().clear());
    }
}
