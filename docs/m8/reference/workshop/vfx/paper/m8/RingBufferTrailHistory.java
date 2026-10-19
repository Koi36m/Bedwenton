package workshop.vfx.paper.m8;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** M8 R2 THROWAWAY REFERENCE (design verification only; never production). */
public final class RingBufferTrailHistory implements TrailHistory {
    private final TrailRules rules;
    private final TrailSample[] buffer;
    private int head, size;            // head = index of the next write
    private long segment = -1, serial;
    private UUID world;
    private double lx, ly, lz;
    private boolean seen;

    public RingBufferTrailHistory(TrailRules rules) {
        this.rules = Objects.requireNonNull(rules);
        if (rules.capacity() < 1 || rules.capacity() > 48) throw new IllegalArgumentException("capacity 1..48");
        if (!(rules.minSpacing() > 0 && rules.minSpacing() <= 4) || !(rules.maxStep() > rules.minSpacing() && rules.maxStep() <= 64)
                || rules.maxSamplesPerTick() < 1 || rules.maxSamplesPerTick() > 4 || rules.maxAgeTicks() < 1 || rules.maxAgeTicks() > 200)
            throw new IllegalArgumentException("trail rules out of range");
        buffer = new TrailSample[rules.capacity()];
    }
    private void add(long tick, double x, double y, double z) {
        buffer[head] = new TrailSample(tick, x, y, z, segment);
        head = (head + 1) % buffer.length; size = Math.min(size + 1, buffer.length);
        lx = x; ly = y; lz = z;
    }
    @Override public void observe(AnchorSnapshot s) {
        boolean worldChanged = seen && !s.world().equals(world);
        if (!seen || worldChanged || s.discontinuitySerial() != serial) {
            if (worldChanged) clear();
            seen = true; world = s.world(); serial = s.discontinuitySerial(); segment++;
            add(s.tick(), s.x(), s.y(), s.z()); return;
        }
        double dx = s.x() - lx, dy = s.y() - ly, dz = s.z() - lz, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d > rules.maxStep()) { segment++; add(s.tick(), s.x(), s.y(), s.z()); return; }
        if (d < rules.minSpacing()) return;
        int n = (int) Math.min(rules.maxSamplesPerTick(), Math.floor(d / rules.minSpacing()));
        double sx = lx, sy = ly, sz = lz;
        for (int k = 1; k <= n; k++) add(s.tick(), sx + dx * k / n, sy + dy * k / n, sz + dz * k / n);
    }
    @Override public List<TrailSample> live(long tick) {
        List<TrailSample> out = new ArrayList<>(size);
        for (int k = 1; k <= size; k++) {
            TrailSample t = buffer[Math.floorMod(head - k, buffer.length)];
            if (tick - t.tick() <= rules.maxAgeTicks()) out.add(t);
        }
        return out;
    }
    @Override public int size() { return size; }
    @Override public int capacity() { return buffer.length; }
    @Override public void clear() { java.util.Arrays.fill(buffer, null); head = 0; size = 0; seen = false; }
}
