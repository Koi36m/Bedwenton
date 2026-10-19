package workshop.vfx.paper.m8;

/** No visual starts exist in K0. Disabled M8 leaves the original M7 command routing untouched. */
public final class K0CommandRouting {
    private K0CommandRouting() { }
    public static boolean handles(boolean enabled,String[] args) {
        return enabled && args.length>0 && (args[0].equalsIgnoreCase("aura") || args[0].equalsIgnoreCase("trail") || args[0].equalsIgnoreCase("activate"));
    }
    public static String unavailable(boolean permitted,boolean player) {
        if(!permitted)return "Permission required: bedwenton.vfxdemo.use";
        if(!player)return "This command requires a player.";
        return "M8 visuals are not implemented in K0; only the shared foundation is available.";
    }
}
