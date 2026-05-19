package mybot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Profiler {
    private static final boolean ENABLED = true;

    private static class ProfileData {
        long totalNs  = 0;
        long calls    = 0;
        long startNs  = 0;

        void start() { startNs = System.nanoTime(); calls++; }
        void stop()  { totalNs += System.nanoTime() - startNs; }
        double totalMs() { return totalNs / 1_000_000.0; }
        double avgUs()   { return calls > 0 ? totalNs / 1_000.0 / calls : 0; }
    }

    private static final Map<String, ProfileData> data = new ConcurrentHashMap<>();
    private static long wallStartNs  = 0;
    private static long heapStartBytes = 0;

    public static void reset() {
        data.clear();
        wallStartNs    = System.nanoTime();
        heapStartBytes = usedHeap();
    }

    public static void start(String name) {
        if (!ENABLED) return;
        data.computeIfAbsent(name, k -> new ProfileData()).start();
    }

    public static void stop(String name) {
        if (!ENABLED) return;
        ProfileData d = data.get(name);
        if (d != null) d.stop();
    }

    public static void printReport() {
        if (!ENABLED || data.isEmpty()) return;

        // Wall-clock time is the reference for percentages (avoids double-counting)
        double wallMs   = (System.nanoTime() - wallStartNs) / 1_000_000.0;
        long   heapNow  = usedHeap();
        long   heapDelta = heapNow - heapStartBytes;

        System.out.println("\n========== PROFILER REPORT ==========");

        // Memory
        System.out.printf("Heap used now : %6.1f MB%n", heapNow   / 1_048_576.0);
        System.out.printf("Heap delta    : %+6.1f MB  (since search start)%n", heapDelta / 1_048_576.0);
        System.out.printf("Wall time     : %6.1f ms%n%n", wallMs);

        // Timing table — % relative to wall time, avg in µs for fine-grained methods
        System.out.printf("%-20s %10s %12s %12s %8s%n",
            "Method", "Calls", "Total ms", "Avg µs", "% Wall");
        System.out.println("-".repeat(68));

        data.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue().totalMs(), a.getValue().totalMs()))
            .forEach(e -> {
                ProfileData d = e.getValue();
                double pct = wallMs > 0 ? d.totalMs() / wallMs * 100 : 0;
                System.out.printf("%-20s %10d %12.2f %12.3f %7.1f%%%n",
                    e.getKey(), d.calls, d.totalMs(), d.avgUs(), pct);
            });

        System.out.println("-".repeat(68));
        System.out.printf("%-20s %10s %12.2f%n", "WALL", "", wallMs);
        System.out.println("=====================================\n");
    }

    private static long usedHeap() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }
}
