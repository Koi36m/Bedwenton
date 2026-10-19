package workshop.vfx.paper.m8;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import workshop.vfx.delivery.QualityTier;
import workshop.vfx.paper.core.ThreadGuard;

/** K1 grammar only; K2/K3 prefixes remain explicitly unavailable. */
public final class M8Commands {
    public interface Endpoint {
        boolean permitted();
        boolean player();
        void start(QualityTier tier);
        void stopAura();
        void reply(String message);
    }
    private final ThreadGuard thread;
    private final M8Config config;
    public M8Commands(ThreadGuard thread, M8Config config) {
        this.thread = Objects.requireNonNull(thread); this.config = Objects.requireNonNull(config);
    }
    public boolean handles(String[] args) {
        thread.check();
        return args.length > 0 && List.of("aura", "trail", "activate").contains(args[0].toLowerCase(Locale.ROOT));
    }
    public void execute(Endpoint e, String[] args) {
        thread.check();
        if (!e.permitted()) { e.reply("Permission required: bedwenton.vfxdemo.use"); return; }
        if (!e.player()) { e.reply("This command requires a player."); return; }
        if (!config.enabled()) { e.reply("M8 is disabled in configuration."); return; }
        if (args.length > 0 && !args[0].equalsIgnoreCase("aura")) {
            e.reply("Only aura is available in K1; trail and activation are not implemented."); return;
        }
        if (args.length < 1 || args.length > 2) { usage(e); return; }
        if (args.length == 2 && args[1].equalsIgnoreCase("off")) { e.stopAura(); e.reply("Your aura stopped."); return; }
        QualityTier tier = config.defaultTier();
        if (args.length == 2) {
            try { tier = QualityTier.valueOf(args[1].toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException bad) { usage(e); return; }
        }
        if (tier == QualityTier.HEAVY && !config.heavyAllowed()) { e.reply("Aura refused: M8 heavy tier is disabled."); return; }
        try { e.start(tier); e.reply("Arcane Halo aura started: " + tier.name().toLowerCase(Locale.ROOT)); }
        catch (IllegalArgumentException | IllegalStateException bad) { e.reply("Aura refused: " + bad.getMessage()); }
    }
    private static void usage(Endpoint e) { e.reply("Usage: /vfxdemo aura [simple|balanced|heavy|off]"); }
    public List<String> suggest(String[] args) {
        thread.check();
        if (!config.enabled()) return List.of();
        List<String> choices = args.length == 1 ? List.of("aura")
            : args.length == 2 && args[0].equalsIgnoreCase("aura")
                ? config.heavyAllowed() ? List.of("simple", "balanced", "heavy", "off") : List.of("simple", "balanced", "off")
                : List.of();
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(s -> s.startsWith(prefix)).toList();
    }
}
