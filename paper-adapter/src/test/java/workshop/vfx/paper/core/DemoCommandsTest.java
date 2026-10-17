package workshop.vfx.paper.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import workshop.vfx.delivery.QualityTier;
class DemoCommandsTest {
    static final class Endpoint implements DemoCommands.Endpoint {
        boolean use=true,admin=false,player=true;int starts,stops,all;QualityTier tier;String reply;boolean refuse;
        public boolean permitted(){return use;}public boolean admin(){return admin;}public boolean player(){return player;}
        public void start(QualityTier tier){if(refuse)throw new IllegalArgumentException("Active ring cap reached");starts++;this.tier=tier;}
        public void stop(){stops++;}public void stopAll(){all++;}public String status(){return "idle; /reload unsupported";}
        public void reply(String s){reply=s;}
    }
    private final DemoCommands command=new DemoCommands(new ThreadGuard(),QualityTier.BALANCED);
    @Test void tiersAndDefaultRouteToProductionController() {
        var e=new Endpoint();command.execute(e,new String[]{"ring"});assertEquals(QualityTier.BALANCED,e.tier);
        for(var t:QualityTier.values()){command.execute(e,new String[]{"ring",t.name().toLowerCase(Locale.ROOT)});assertEquals(t,e.tier);}
        assertEquals(4,e.starts);
    }
    @Test void unknownAndExtraArgumentsHaveNoSideEffects() {
        var e=new Endpoint();
        for(String[] args:new String[][]{{},{"unknown"},{"ring","unknown"},{"ring","simple","extra"},{"stop","mine"},{"status","extra"}}) {
            command.execute(e,args);assertTrue(e.reply.startsWith("Usage:"));
        }
        assertEquals(0,e.starts+e.stops+e.all);
    }
    @Test void permissionAndPlayerBoundariesAreEnforced() {
        var e=new Endpoint();e.use=false;command.execute(e,new String[]{"ring"});assertEquals(0,e.starts);
        command.execute(e,new String[]{"status"});assertTrue(e.reply.contains("Permission required"));
        e.use=true;e.player=false;command.execute(e,new String[]{"ring"});command.execute(e,new String[]{"stop"});
        assertEquals(0,e.starts+e.stops);command.execute(e,new String[]{"status"});assertTrue(e.reply.contains("idle"));
    }
    @Test void adminStopAllIsSeparateFromOwnerStop() {
        var e=new Endpoint();command.execute(e,new String[]{"stop"});assertEquals(1,e.stops);
        command.execute(e,new String[]{"stop","all"});assertEquals(0,e.all);assertTrue(e.reply.contains("admin"));
        e.admin=true;command.execute(e,new String[]{"stop","all"});assertEquals(1,e.all);
    }
    @Test void capsAreReportedAndRepeatedCommandsNeverAddHiddenOperations() {
        var e=new Endpoint();e.refuse=true;command.execute(e,new String[]{"ring"});assertEquals(0,e.starts);assertTrue(e.reply.contains("cap reached"));
        e.refuse=false;for(int n=0;n<5;n++)command.execute(e,new String[]{"ring","heavy"});
        assertEquals(5,e.starts);assertEquals(0,e.stops+e.all);
    }
    @Test void suggestionsRespectGrammarAndAdminPermission() {
        assertEquals(List.of("ring"),command.suggest(new String[]{"r"},false));
        assertEquals(List.of("balanced"),command.suggest(new String[]{"ring","b"},false));
        assertTrue(command.suggest(new String[]{"stop",""},false).isEmpty());
        assertEquals(List.of("all"),command.suggest(new String[]{"stop",""},true));
        assertTrue(command.suggest(new String[]{"ring","heavy",""},true).isEmpty());
    }
}
