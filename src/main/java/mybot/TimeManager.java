/* TimeManager.java */
package mybot;

import com.github.bhlangonijr.chesslib.Side;

/**
 * Manages time allocation and deadline tracking for iterative deepening.
 */
public class TimeManager {
    private long deadlineMs;
    private long allocatedTimeMs;
    private long startTimeMs;

    public TimeManager() {
        this.deadlineMs = Long.MAX_VALUE;
        this.allocatedTimeMs = 0;
        this.startTimeMs = System.currentTimeMillis();
    }

    /**
     * Plan time allocation for the current move based on UCI go command.
     *
     * @param goCmd UCI go command with time controls
     * @param sideToMove which side is moving
     * @param movesPlayed number of moves played in the game
     */
    public void planTimeForMove(GoCommand goCmd, Side sideToMove, int movesPlayed) {
        startTimeMs = System.currentTimeMillis();

        // Handle movetime (fixed time per move)
        if (goCmd.moveTimeMs > 0) {
            allocatedTimeMs = (long)(goCmd.moveTimeMs * 0.95);  // 95% safety buffer
            deadlineMs = startTimeMs + allocatedTimeMs;
            return;
        }

        // Handle infinite search
        if (goCmd.infinite || !goCmd.hasTimeControl()) {
            allocatedTimeMs = Long.MAX_VALUE;
            deadlineMs = Long.MAX_VALUE;
            return;
        }

        // Handle time control (wtime/btime with optional increment)
        long timeRemainingMs = (sideToMove == Side.WHITE) ? goCmd.whiteTimeMs : goCmd.blackTimeMs;
        long incrementMs = (sideToMove == Side.WHITE) ? goCmd.whiteIncrementMs : goCmd.blackIncrementMs;

        // Calculate base allocation based on time situation
        if (timeRemainingMs < 500) {
            // Emergency: use almost all remaining time
            allocatedTimeMs = Math.max(50, timeRemainingMs - 50);
        } else if (timeRemainingMs < 2_000) {
            // Very low time: use about 1/6 of remaining + full increment
            allocatedTimeMs = timeRemainingMs / 6 + incrementMs;
        } else if (timeRemainingMs < 10_000) {
            // Low time: different strategy for sudden death vs increment
            if (incrementMs > 0) {
                allocatedTimeMs = timeRemainingMs / 8 + incrementMs;
            } else {
                allocatedTimeMs = timeRemainingMs / 10;  // Sudden death - be more careful
            }
        } else if (timeRemainingMs < 60_000) {
            // Medium time: use 1/15 of remaining + most of increment
            allocatedTimeMs = timeRemainingMs / 15 + (incrementMs * 4 / 5);
        } else {
            // Plenty of time: use 1/20 of remaining + most of increment
            allocatedTimeMs = timeRemainingMs / 20 + (incrementMs * 4 / 5);
        }

        // Apply safety buffer (95% of calculated time)
        allocatedTimeMs = (long)(allocatedTimeMs * 0.95);

        // Ensure we don't exceed available time
        allocatedTimeMs = Math.min(allocatedTimeMs, timeRemainingMs - 100);
        allocatedTimeMs = Math.max(50, allocatedTimeMs);  // Minimum 50ms

        deadlineMs = startTimeMs + allocatedTimeMs;
    }

    /**
     * Check if we've exceeded our time budget.
     */
    public boolean isTimeUp() {
        return System.currentTimeMillis() >= deadlineMs;
    }

    /**
     * Get the allocated time in milliseconds.
     */
    public long getAllocatedTimeMs() {
        return allocatedTimeMs;
    }

    /**
     * Get elapsed time since search started.
     */
    public long getElapsedMs() {
        return System.currentTimeMillis() - startTimeMs;
    }
}
