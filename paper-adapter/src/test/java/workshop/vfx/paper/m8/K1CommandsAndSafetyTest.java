package workshop.vfx.paper.m8;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static workshop.vfx.paper.m8.K1Fixture.*;
import java.util.*;
import workshop.vfx.delivery.*;
import workshop.vfx.paper.core.*;
import workshop.vfx.scene.*;

class K1CommandsAndSafetyTest {
    static final class Endpoint implements M8Commands.Endpoint {
        boolean use=true,player=true,fail;int starts,stops;QualityTier tier;String reply;
        public boolean permitted(){return use;}public boolean player(){return player;}
        public void start(QualityTier t){if(fail)throw new IllegalArgumentException("injected cap");starts++;tier=t;}
        public void stopAura(){stops++;}public void reply(String s){reply=s;}
    }
    M8Commands commands(Map<String,Object> settings){var m=new HashMap<String,Object>(Map.of("enabled",true));m.putAll(settings);return new M8Commands(new ThreadGuard(),M8Config.from(m));}
    @Test void defaultSimpleAndConfiguredBalanced(){for(String tier:List.of("simple","balanced")){var e=new Endpoint();commands(Map.of("defaultTier",tier)).execute(e,new String[]{"aura"});assertEquals(QualityTier.valueOf(tier.toUpperCase(Locale.ROOT)),e.tier);assertEquals(1,e.starts);}}
    @Test void allExplicitTiersAndCaseInsensitiveOff(){var c=commands(Map.of());var e=new Endpoint();for(String tier:List.of("SIMPLE","balanced","Heavy")){c.execute(e,new String[]{"AURA",tier});assertEquals(QualityTier.valueOf(tier.toUpperCase(Locale.ROOT)),e.tier);}c.execute(e,new String[]{"aura","OFF"});assertEquals(3,e.starts);assertEquals(1,e.stops);}
    @Test void disabledCommandsAndSessionCannotStart(){var e=new Endpoint();commands(Map.of("enabled",false)).execute(e,new String[]{"aura"});assertEquals(0,e.starts);assertTrue(e.reply.contains("disabled"));var f=new K1Fixture(Map.of(),Map.of("enabled",false));assertThrows(IllegalArgumentException.class,()->f.balanced());assertEquals(0,f.schedules);}
    @Test void permissionsAndConsoleFailBeforeMutation(){for(boolean permission:List.of(false,true)){var e=new Endpoint();e.use=permission;e.player=false;commands(Map.of()).execute(e,new String[]{"aura","off"});assertEquals(0,e.starts+e.stops);assertTrue(e.reply.contains(permission?"requires a player":"Permission required"));}}
    @Test void malformedGrammarAndFutureCommandsDoNotStart(){for(String[] args:new String[][]{{},{"aura","experimental"},{"aura","simple","extra"},{"trail"},{"activate","heavy"}}){var e=new Endpoint();commands(Map.of()).execute(e,args);assertEquals(0,e.starts+e.stops);}}
    @Test void heavyRefusedBothDefaultAndExplicitAndSessionPreservesOld(){for(String[] args:new String[][]{{"aura"},{"aura","heavy"}}){var e=new Endpoint();commands(Map.of("defaultTier","heavy","heavyAllowed",false)).execute(e,args);assertEquals(0,e.starts);assertTrue(e.reply.contains("disabled"));}var f=new K1Fixture(Map.of(),Map.of("heavyAllowed",false));f.balanced();var old=f.current();assertThrows(IllegalArgumentException.class,()->f.start(QualityTier.HEAVY));assertSame(old,f.current());}
    @Test void commandFailureIsReportedAndSuggestionsDoNotOfferFutureCheckpoints(){var e=new Endpoint();e.fail=true;var c=commands(Map.of("heavyAllowed",false));c.execute(e,new String[]{"aura"});assertTrue(e.reply.contains("injected cap"));assertEquals(List.of("aura"),c.suggest(new String[]{""}));assertEquals(List.of("simple","balanced","off"),c.suggest(new String[]{"aura",""}));assertEquals(List.of(),commands(Map.of("enabled",false)).suggest(new String[]{""}));assertTrue(c.handles(new String[]{"AuRa"}));assertFalse(c.handles(new String[]{"ring"}));}
    @Test void invalidM8SettingsRejectRatherThanClamp(){for(var settings:List.<Map<String,Object>>of(Map.of("auraLifetimeTicks",19),Map.of("auraLifetimeTicks",12001),Map.of("enabled","true"),Map.of("defaultTier","experimental"),Map.of("heavyAllowed",1),Map.of("maxEffectsPerPlayer",4),Map.of("invalidAnchorTicksToTerminate",0),Map.of("auraLifetimeTicks",20.5))){assertThrows(IllegalArgumentException.class,()->M8Config.from(settings));}}
    @Test void strictCyanCatalogueDoesNotOverwriteHistoricalKeysOrM7(){var styles=M8StyleCatalog.whitelist();assertEquals(3,styles.size());DemoStyleCatalog.whitelist().forEach((k,v)->assertEquals(v,styles.get(k)));assertEquals(new ParticleSpec.Dust(0x5CE1FF,0.55f),styles.get(M8StyleCatalog.CYAN_HALO));for(String k:List.of("bedwenton:m8_aura_violet","bedwenton:m8_aura_gold","bedwenton:m8_trail_cyan"))assertFalse(styles.containsKey(StyleKey.of(k)));assertThrows(UnsupportedOperationException.class,()->styles.clear());assertThrows(IllegalArgumentException.class,()->new M8StyleCatalog(Set.of(StyleKey.of("bad:unknown"))));assertThrows(IllegalArgumentException.class,()->new ParticleSpec.Dust(-1,0.7f));assertThrows(IllegalArgumentException.class,()->new ParticleSpec.Dust(0,Float.NaN));assertEquals(Set.of(M8StyleCatalog.CYAN_HALO),new M8StyleCatalog(Set.of(M8StyleCatalog.CYAN_HALO)).supportedStyles());}
    @Test void timedSnapshotsUseInjectedDiagnosticsAndThreadChecks(){var thread=new ThreadGuard();long[] time={0};var source=new TimedAnchorSource(thread,(owner,t)->null,()->time[0]+=7);source.snapshot(A,1);source.snapshot(B,1);assertEquals(14,source.snapshotNanos());assertEquals(2,source.reads());source.snapshot(A,2);assertEquals(7,source.snapshotNanos());assertEquals(1,source.reads());boolean[] primary={true};var guarded=new ThreadGuard(()->primary[0]);var commands=new M8Commands(guarded,M8Config.from(Map.of()));primary[0]=false;assertThrows(IllegalStateException.class,()->commands.handles(new String[]{"aura"}));}
}
