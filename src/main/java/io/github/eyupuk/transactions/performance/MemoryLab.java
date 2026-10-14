
package io.github.eyupuk.transactions.performance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class MemoryLab {

    // Intentionally retains references for this exercise.
    private static volatile List<byte[]> retainedData =
            List.of();

    private static volatile long checksum;

    private MemoryLab() {}

    public static void main(String[] args)
            throws InterruptedException {

        String mode = args.length == 0
                ? "temporary"
                : args[0];

        if (!mode.equals("temporary")
                && !mode.equals("retained")) {
            throw new IllegalArgumentException(
                    "Use temporary or retained"
            );
        }

        boolean retain = mode.equals("retained");

        int allocations = retain ? 1200 : 10000;
        int bytesPerAllocation = 32 * 1024;

        List<byte[]> kept = new ArrayList<>();

        long start = System.nanoTime();

        for (int i = 0; i < allocations; i++) {
            byte[] data = new byte[bytesPerAllocation];

            Arrays.fill(data, (byte) (i % 127));
            checksum += data[0];

            if (retain) {
                kept.add(data);
            }
        }

        // Keep retained objects reachable for inspection.
        retainedData = List.copyOf(kept);

        double elapsedMs =
                (System.nanoTime() - start) / 1_000_000.0;

        // Pause for diagnostic attachment only.
        // Never use sleeps to make concurrency tests pass.
        Thread.sleep(12_000);
    }
}
