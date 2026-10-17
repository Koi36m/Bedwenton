package workshop.vfx.paper.core;
import java.util.Arrays;
import java.util.Objects;
/** Owner admission precedes capping; all other inputs must be unique and sorted. */
public final class OwnerFirstViewerSelection implements ViewerSelection {
    @Override public long[] select(long owner, boolean ownerEligible, long[] others, int cap) {
        Objects.requireNonNull(others);
        if (owner < 0 || cap < 0 || cap > 16) throw new IllegalArgumentException("Invalid owner or viewer cap");
        long previous = -1;
        for (long viewer : others) {
            if (viewer < 0 || viewer <= previous) throw new IllegalArgumentException("Viewers must be unique and sorted");
            previous = viewer;
        }
        long[] result = new long[cap]; int count = 0;
        if (ownerEligible && cap > 0) result[count++] = owner;
        for (long viewer : others) {
            if (count == cap) break;
            if (viewer != owner) result[count++] = viewer;
        }
        result = Arrays.copyOf(result, count); Arrays.sort(result); return result;
    }
}
