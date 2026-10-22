package workshop.vfx.paper.reference;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import workshop.vfx.delivery.QualityTier;
import workshop.vfx.paper.core.ThreadGuard;
import workshop.vfx.paper.m8.M8Config;

/** Separate opt-in command; existing use/player/config/heavy gates, with no changes to /vfxdemo grammar. */
public final class ReferenceCommands {
    public interface Endpoint {
        boolean permitted();boolean player();void start(QualityTier tier);void stop();String status();void reply(String text);
    }
    private final ThreadGuard thread;
    private final M8Config config;
    public ReferenceCommands(ThreadGuard thread,M8Config config){this.thread=Objects.requireNonNull(thread);this.config=Objects.requireNonNull(config);}
    public void execute(Endpoint e,String[] args) {
        thread.check();
        if(!e.permitted()){e.reply("Permission required: bedwenton.vfxdemo.use");return;}
        if(!e.player()){e.reply("This command requires a player.");return;}
        if(!config.enabled()){e.reply("M8 is disabled in configuration.");return;}
        if(args.length>1){usage(e);return;}
        if(args.length==1&&args[0].equalsIgnoreCase("off")){e.stop();e.reply("Reference prototype stopped.");return;}
        if(args.length==1&&args[0].equalsIgnoreCase("status")){e.reply(e.status());return;}
        QualityTier tier=config.defaultTier();
        if(args.length==1)try{tier=QualityTier.valueOf(args[0].toUpperCase(Locale.ROOT));}catch(IllegalArgumentException bad){usage(e);return;}
        if(tier==QualityTier.HEAVY&&!config.heavyAllowed()){e.reply("Reference refused: heavy tier disabled.");return;}
        try{e.start(tier);e.reply("Experimental 200-tick reference VFX started: "+tier.name().toLowerCase(Locale.ROOT));}
        catch(IllegalArgumentException|IllegalStateException bad){e.reply("Reference refused: "+bad.getMessage());}
    }
    private static void usage(Endpoint e){e.reply("Usage: /vfxreference [simple|balanced|heavy|off|status]");}
    public List<String> suggest(String[] args) {
        thread.check();if(!config.enabled()||args.length!=1)return List.of();
        return (config.heavyAllowed()?List.of("simple","balanced","heavy","off","status"):List.of("simple","balanced","off","status"))
            .stream().filter(s->s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }
}
