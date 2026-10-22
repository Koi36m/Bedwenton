package workshop.vfx.paper.streams;

import java.util.HashMap;
import java.util.Map;
import workshop.vfx.paper.core.*;
import workshop.vfx.paper.reference.ReferenceStyleCatalog;
import workshop.vfx.scene.StyleKey;

public final class StreamsStyleCatalog {
    private StreamsStyleCatalog(){ }
    public static Map<StyleKey,ParticleSpec> whitelist(){
        var all=new HashMap<>(ReferenceStyleCatalog.whitelist());
        all.put(DirectionalEmission.A,new ParticleSpec.Plain("minecraft:end_rod"));all.put(DirectionalEmission.B,new ParticleSpec.Plain("minecraft:end_rod"));
        return Map.copyOf(all);
    }
}
