package workshop.vfx.paper.core;
import java.util.List;
import java.util.Locale;
import workshop.vfx.delivery.QualityTier;
/** Testable command grammar; the Paper layer supplies permission and player context. */
public final class DemoCommands {
    public interface Endpoint {
        boolean permitted();
        boolean admin();
        boolean player();
        void start(QualityTier tier);
        void stop();
        void stopAll();
        String status();
        void reply(String text);
    }
    private final ThreadGuard thread;
    private final QualityTier defaultTier;
    public DemoCommands(ThreadGuard thread, QualityTier defaultTier) { this.thread=thread; this.defaultTier=defaultTier; }
    public void execute(Endpoint e, String[] args) {
        thread.check();
        if (!e.permitted()) { e.reply("Permission required: bedwenton.vfxdemo.use"); return; }
        if (args.length == 1 && args[0].equalsIgnoreCase("status")) { e.reply(e.status()); return; }
        if (args.length >= 1 && args[0].equalsIgnoreCase("ring") && args.length <= 2) {
            if (!e.player()) { e.reply("This command requires a player."); return; }
            QualityTier tier = defaultTier;
            if (args.length == 2) {
                try { tier = QualityTier.valueOf(args[1].toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException bad) { usage(e); return; }
            }
            try { e.start(tier); e.reply("Static ring started: " + tier.name().toLowerCase(Locale.ROOT)); }
            catch (IllegalArgumentException | IllegalStateException bad) { e.reply("Ring refused: " + bad.getMessage()); }
            return;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("stop")) {
            if (!e.player()) { e.reply("This command requires a player."); return; }
            e.stop(); e.reply("Your ring stopped."); return;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("stop") && args[1].equalsIgnoreCase("all")) {
            if (!e.admin()) { e.reply("Permission required: bedwenton.vfxdemo.admin"); return; }
            e.stopAll(); e.reply("All rings stopped."); return;
        }
        usage(e);
    }
    private static void usage(Endpoint e) { e.reply("Usage: /vfxdemo ring [simple|balanced|heavy] | stop [all] | status"); }
    public List<String> suggest(String[] args, boolean admin) {
        thread.check();
        List<String> choices = args.length == 1 ? List.of("ring","stop","status")
            : args.length == 2 && args[0].equalsIgnoreCase("ring") ? List.of("simple","balanced","heavy")
            : args.length == 2 && args[0].equalsIgnoreCase("stop") && admin ? List.of("all") : List.of();
        String prefix = args.length == 0 ? "" : args[args.length-1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(s -> s.startsWith(prefix)).toList();
    }
}
