package mybot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple profiling tool to track method execution times and call counts.
 * Usage:
 *   Profiler.start("methodName");
 *   // ... code to profile ...
 *   Profiler.stop("methodName");
 *
 *   // At end of search:
 *   Profiler.printReport();
 *   Profiler.reset();
 */
public class Profiler {
    private static final boolean ENABLED = true; // Set to false to disable profiling

    private static class ProfileData {
        long totalTime = 0;
        long callCount = 0;
        long startTime = 0;

        void start() {
            startTime = System.nanoTime();
            callCount++;
        }

        void stop() {
            totalTime += System.nanoTime() - startTime;
        }

        double getAverageMs() {
            return callCount > 0 ? (totalTime / 1_000_000.0) / callCount : 0;
        }

        double getTotalMs() {
            return totalTime / 1_000_000.0;
        }
    }

    private static final Map<String, ProfileData> profiles = new ConcurrentHashMap<>();

    public static void start(String name) {
        if (!ENABLED) return;
        profiles.computeIfAbsent(name, k -> new ProfileData()).start();
    }

    public static void stop(String name) {
        if (!ENABLED) return;
        ProfileData data = profiles.get(name);
        if (data != null) {
            data.stop();
        }
    }

    public static void reset() {
        profiles.clear();
    }

    public static void printReport() {
        if (!ENABLED || profiles.isEmpty()) return;

        System.out.println("\n========== PROFILER REPORT ==========");
        System.out.printf("%-30s %10s %15s %15s %10s%n",
            "Method", "Calls", "Total (ms)", "Avg (ms)", "% Time");
        System.out.println("-".repeat(85));

        // Calculate total time for percentage
        double totalTime = profiles.values().stream()
            .mapToDouble(ProfileData::getTotalMs)
            .sum();

        // Sort by total time descending
        profiles.entrySet().stream()
            .sorted((a, b) -> Double.compare(
                b.getValue().getTotalMs(),
                a.getValue().getTotalMs()))
            .forEach(entry -> {
                String name = entry.getKey();
                ProfileData data = entry.getValue();
                double percent = totalTime > 0 ? (data.getTotalMs() / totalTime * 100) : 0;

                System.out.printf("%-30s %10d %15.2f %15.6f %9.1f%%%n",
                    name,
                    data.callCount,
                    data.getTotalMs(),
                    data.getAverageMs(),
                    percent);
            });

        System.out.println("-".repeat(85));
        System.out.printf("%-30s %10s %15.2f%n", "TOTAL", "", totalTime);
        System.out.println("=====================================\n");
    }
}
