package workshop.vfx.paper.streams;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import workshop.vfx.paper.core.ThreadGuard;
import workshop.vfx.paper.m8.M8Config;

public final class StreamsCommands {
    public interface Endpoint{boolean permitted();boolean player();void start(StreamSpeed speed);void stop();String status();void reply(String text);}
    private final ThreadGuard thread;
    private final M8Config config;
    public StreamsCommands(ThreadGuard thread,M8Config config){this.thread=Objects.requireNonNull(thread);this.config=Objects.requireNonNull(config);}
    public void execute(Endpoint e,String[] args){
        thread.check();if(!e.permitted()){e.reply("Permission required: bedwenton.vfxdemo.use");return;}
        if(!e.player()){e.reply("This command requires a player.");return;}
        if(!config.enabled()){e.reply("M8 is disabled in configuration.");return;}
        if(args.length>1){e.reply("Usage: /vfxstreams [slow|normal|fast|off|stop|status]");return;}
        if(args.length==1&&(args[0].equalsIgnoreCase("off")||args[0].equalsIgnoreCase("stop"))){e.stop();e.reply("Streams stopped.");return;}
        if(args.length==1&&args[0].equalsIgnoreCase("status")){e.reply(e.status());return;}
        StreamSpeed speed=StreamSpeed.NORMAL;
        if(args.length==1)try{speed=StreamSpeed.valueOf(args[0].toUpperCase(Locale.ROOT));}catch(IllegalArgumentException bad){e.reply("Usage: /vfxstreams [slow|normal|fast|off|stop|status]");return;}
        if(speed==StreamSpeed.FAST&&!config.heavyAllowed()){e.reply("Fast preset requires heavyAllowed.");return;}
        try{e.start(speed);e.reply("Dual-stream experiment started: "+speed.name().toLowerCase(Locale.ROOT));}
        catch(IllegalArgumentException|IllegalStateException bad){e.reply("Streams refused: "+bad.getMessage());}
    }
    public List<String> suggest(String[] args){thread.check();if(!config.enabled()||args.length!=1)return List.of();
        return (config.heavyAllowed()?List.of("slow","normal","fast","off","stop","status"):List.of("slow","normal","off","stop","status"))
            .stream().filter(s->s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();}
}
