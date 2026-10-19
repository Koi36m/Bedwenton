package workshop.vfx.paper.m8;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import workshop.vfx.math.core.Pose;
import workshop.vfx.math.core.Vec3;
import workshop.vfx.math.time.Playback;
import workshop.vfx.math.time.TimePoint;
import workshop.vfx.scene.EffectFrame;
import workshop.vfx.scene.EffectScene;
import workshop.vfx.scene.FrameBatch;

/** R2 verification improvement: the R1 probe's orientation and animation checks as failing assertions. */
class OrientationAndAnimationTest {
    private static final UUID U = new UUID(0, 1);
    private static final workshop.vfx.paper.core.DefaultSessionIds IDS =
            new workshop.vfx.paper.core.DefaultSessionIds(new workshop.vfx.paper.core.ThreadGuard());
    private static final AnchorBinding BOUND = new AnchorBinding(U, IDS.viewerId(U));
    private static final int WORLD_ID = IDS.worldId(U);
    private static final AnchorRules RULES = new DefaultAnchorRules(IDS);

    private static AnchorSnapshot at(double x, double y, double z, float bodyYaw, float headYaw, float pitch) {
        return new AnchorSnapshot(0, U, BOUND.ownerViewerId(), U, WORLD_ID, x, y, z, bodyYaw, headYaw, pitch, -64, 320, true,
                true, true, false, 0);
    }
    private static AnchorSnapshot state(boolean online, boolean valid, boolean dead) {
        return new AnchorSnapshot(0, U, BOUND.ownerViewerId(), U, WORLD_ID, 0, 0, 0, 0, 0, 0, -64, 320, true, online, valid, dead, 0);
    }
    private static AnchorState validate(AnchorSnapshot s) { return RULES.validate(s, BOUND, 0).state(); }

    @Test
    @DisplayName("Yaw mapping: local +Z -> (-sin yaw, 0, cos yaw), local +X -> (cos yaw, 0, sin yaw), +Y fixed; |error| <= 1e-12")
    void yawMapping() {
        for (double yaw : new double[] {0, 45, 90, 180, 270, -90, 359.5, 720, -1234.25}) {
            AnchorSnapshot s = at(0, 0, 0, (float) yaw, 0, 0);
            double r = Math.toRadians((float) yaw);
            Vec3 f = RULES.toWorld(s, OrientationMode.BODY_YAW, 0, Vec3.Z);
            Vec3 l = RULES.toWorld(s, OrientationMode.BODY_YAW, 0, Vec3.X);
            Vec3 u = RULES.toWorld(s, OrientationMode.BODY_YAW, 0, Vec3.Y);
            assertTrue(f.sub(new Vec3(-Math.sin(r), 0, Math.cos(r))).length() <= 1e-12, "forward yaw " + yaw);
            assertTrue(l.sub(new Vec3(Math.cos(r), 0, Math.sin(r))).length() <= 1e-12, "left yaw " + yaw);
            assertTrue(u.sub(Vec3.Y).length() <= 1e-12, "up yaw " + yaw);
        }
    }

    @Test
    @DisplayName("Pitch never orients an effect; HEAD_YAW and WORLD_FIXED select their own yaw; lift is world-vertical")
    void modes() {
        AnchorSnapshot level = at(10, 64, -3, 90, 30, 0), down = at(10, 64, -3, 90, 30, 89);
        assertEquals(RULES.pose(level, OrientationMode.BODY_YAW, 1.5), RULES.pose(down, OrientationMode.BODY_YAW, 1.5));
        assertTrue(RULES.toWorld(level, OrientationMode.WORLD_FIXED, 0, Vec3.Z).sub(new Vec3(10, 64, -2)).length() <= 1e-12);
        double r = Math.toRadians(30);
        assertTrue(RULES.toWorld(level, OrientationMode.HEAD_YAW, 0, Vec3.Z).sub(new Vec3(10 - Math.sin(r), 64, -3 + Math.cos(r))).length() <= 1e-12);
        assertEquals(65.5, RULES.pose(level, OrientationMode.BODY_YAW, 1.5).translation().y(), 0);
    }

    @Test
    @DisplayName("Snapshot validation: absent/offline/invalid/dead terminate; non-finite position or yaw skips the tick")
    void validation() {
        assertEquals(AnchorState.TERMINATE, validate(null));
        assertEquals(AnchorState.TERMINATE, validate(state(false, true, false)));
        assertEquals(AnchorState.TERMINATE, validate(state(true, false, false)));
        assertEquals(AnchorState.TERMINATE, validate(state(true, true, true)));
        for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertEquals(AnchorState.SKIP_TICK, validate(at(bad, 0, 0, 0, 0, 0)));
            assertEquals(AnchorState.SKIP_TICK, validate(at(0, bad, 0, 0, 0, 0)));
            assertEquals(AnchorState.SKIP_TICK, validate(at(0, 0, bad, 0, 0, 0)));
            assertEquals(AnchorState.SKIP_TICK, validate(at(0, 0, 0, (float) bad, 0, 0)));
        }
        assertEquals(AnchorState.READY, validate(at(1, 2, 3, 4, 5, 6)));
    }

    @Test
    @DisplayName("Aura geometry with a moving, turning anchor equals an independent formula (<= 1e-9 blocks) at known ticks")
    void auraGeometry() {
        EffectScene scene = M8Recipes.auraScene(18, 9, 1200);
        int checked = 0;
        for (long t : new long[] {1000, 1001, 1015, 1030, 1059, 1060, 1123}) {
            double px = 100.5 + 0.28 * (t - 1000), pz = -20.5 + 0.1 * (t - 1000), yaw = 3.0 * (t - 1000);
            AnchorSnapshot s = at(px, 64, pz, (float) yaw, 0, 0);
            EffectFrame frame = scene.evaluate(RULES.pose(s, OrientationMode.BODY_YAW, 0), Playback.start(TimePoint.gameTick(1000)), TimePoint.gameTick(t));
            assertEquals(27, frame.pointCount());
            FrameBatch orbit = frame.batches().get(0);
            for (int i = 0; i < orbit.size(); i++) {
                double a = 2 * Math.PI * i / orbit.size() + 2 * Math.PI * ((t - 1000) % 60) / 60.0;
                double lx = 0.9 * Math.cos(a), lz = -0.9 * Math.sin(a), yr = -Math.toRadians((float) yaw);
                Vec3 expect = new Vec3(px + lx * Math.cos(yr) + lz * Math.sin(yr), 65.0, pz - lx * Math.sin(yr) + lz * Math.cos(yr));
                assertTrue(orbit.point(i).sub(expect).length() <= 1e-9, "tick " + t + " point " + i);
                checked++;
            }
        }
        assertEquals(126, checked);
    }

    /** M5 PREFIX reveal: floor(progress * n) points, progress = local / 20; an empty batch is omitted from the frame. */
    private static String prefix(long local, int n) {
        int k = (int) Math.floor((local / 20.0) * n);
        return k == 0 ? "" : "form=" + k;
    }

    @Test
    @DisplayName("Activation phase boundaries are exact for all tiers (materialize, buildup, release, dissolution, end)")
    void activationPhases() {
        int[][] tiers = {{16, 12, 16, 8}, {24, 18, 24, 12}, {32, 24, 32, 16}};
        for (int[] n : tiers) {
            EffectScene act = M8Recipes.activationScene(n[0], n[1], n[2], n[3]);
            assertEquals(n[0] + n[1] + n[2] + n[3], act.maxPointsPerFrame());
            Map<Long, String> expected = new LinkedHashMap<>();
            expected.put(0L, ""); expected.put(1L, prefix(1, n[0])); expected.put(10L, prefix(10, n[0]));
            expected.put(19L, prefix(19, n[0]));
            expected.put(20L, "column=" + n[1]); expected.put(49L, "column=" + n[1]);
            expected.put(50L, "burst=" + n[2]); expected.put(59L, "burst=" + n[2]);
            expected.put(60L, "embers=" + n[3]); expected.put(79L, "embers=" + n[3]); expected.put(80L, "");
            for (Map.Entry<Long, String> e : expected.entrySet()) {
                EffectFrame f = act.evaluate(Pose.ofTranslation(new Vec3(0.5, 64, 0.5)), Playback.start(TimePoint.gameTick(0)), TimePoint.gameTick(e.getKey()));
                StringBuilder sb = new StringBuilder();
                for (FrameBatch b : f.batches()) sb.append(sb.isEmpty() ? "" : " ").append(b.layer()).append('=').append(b.size());
                assertEquals(e.getValue(), sb.toString(), "tier " + n[0] + " local " + e.getKey());
            }
        }
    }
}
