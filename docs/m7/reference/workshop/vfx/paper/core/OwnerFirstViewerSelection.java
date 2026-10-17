package workshop.vfx.paper.core;

import java.util.Arrays;

/** M7 R3 THROWAWAY REFERENCE (design verification only; compiled by docs/m7/verification, never production). */
public final class OwnerFirstViewerSelection implements ViewerSelection {
    @Override public long[] select(long owner, boolean ownerEligible, long[] others, int cap) {
        long[] out = new long[cap];
        int n = 0;
        if (ownerEligible && cap > 0) out[n++] = owner;
        for (long v : others) { if (n == cap) break; if (v != owner) out[n++] = v; }
        long[] r = Arrays.copyOf(out, n);
        Arrays.sort(r);
        return r;
    }
}
