package workshop.vfx.paper.core;

import java.util.Map;
import java.util.Set;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.scene.EffectFrame;
import workshop.vfx.scene.StyleKey;

/** Narrow adapter-only END_ROD experiment payload. No change to the frozen M1–M6 representation. */
public record DirectionalEmission(Vec3 direction, double speed) {
    public static final StyleKey A = StyleKey.of("bedwenton:streams_end_rod_a");
    public static final StyleKey B = StyleKey.of("bedwenton:streams_end_rod_b");
    public static final Set<StyleKey> STYLES = Set.of(A,B);
    public DirectionalEmission {
        if(direction==null || !direction.isFinite() || Math.abs(direction.length()-1)>1e-9
                || !Double.isFinite(speed) || speed<.10 || speed>.22)
            throw new IllegalArgumentException("Finite unit direction and speed in [0.10,0.22] required");
    }
    public static boolean validPair(EffectFrame frame, Map<StyleKey,DirectionalEmission> motions) {
        if(motions==null||!motions.keySet().equals(STYLES)||frame==null||frame.pointCount()!=2||frame.batches().size()!=2)return false;
        var a=motions.get(A);var b=motions.get(B);
        if(a.speed()!=b.speed() || a.direction().add(b.direction()).length()!=0)return false;
        var first=frame.batches().get(0);var second=frame.batches().get(1);
        return first.size()==1&&second.size()==1&&STYLES.contains(first.style())&&STYLES.contains(second.style())&&!first.style().equals(second.style())
            && first.point(0).isFinite()&&first.point(0).equals(second.point(0));
    }
}
