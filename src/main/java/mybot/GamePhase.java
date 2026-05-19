package mybot;


/**
 * Advanced game phase analyzer and search depth calculator.
 * Determines optimal search depth based on multiple factors:
 * - Time remaining (time management)
 * - Game phase (opening/middlegame/endgame)
 * - Piece count and material balance
 * - Position complexity
 * - Critical situations (check, tactical positions)
 */
public class GamePhase {

    // ==================== PHASE DETECTION ====================

    public enum Phase {
        OPENING,
        MIDDLEGAME,
        ENDGAME
    }

    /**
     * Material thresholds for phase detection.
     * Uses hysteresis to prevent rapid oscillations.
     */
    private static final int OPENING_THRESHOLD = 6400;
    private static final int MIDDLEGAME_THRESHOLD = 3000;

    /**
     * Determines current game phase based on material.
     *
     * @param absoluteMaterial Sum of all piece values (both sides, unsigned)
     * @return The current phase
     */
    public static Phase detectPhase(int absoluteMaterial) {
        if (absoluteMaterial >= OPENING_THRESHOLD) {
            return Phase.OPENING;
        } else if (absoluteMaterial >= MIDDLEGAME_THRESHOLD) {
            return Phase.MIDDLEGAME;
        } else {
            return Phase.ENDGAME;
        }
    }

    /**
     * Alternative phase detection using piece count.
     *
     * @param pieceCount Total number of pieces on board (excluding kings)
     * @return The current phase
     */
    public static Phase detectPhaseByPieceCount(int pieceCount) {
        if (pieceCount >= 28) {  // 30 pieces minus 2 kings = opening
            return Phase.OPENING;
        } else if (pieceCount >= 12) {  // 12-27 pieces = middlegame
            return Phase.MIDDLEGAME;
        } else {  // < 12 pieces = endgame
            return Phase.ENDGAME;
        }
    }

    /**
     * Calculates a continuous game phase value for smooth PST interpolation.
     * Returns a value between 0.0 (pure opening) and 1.0 (pure endgame).
     *
     * This provides a smooth transition for piece-square table evaluation,
     * avoiding the discontinuities of discrete phase detection.
     *
     * @param absoluteMaterial Sum of all piece values (both sides, unsigned)
     * @return Phase value from 0.0 (opening) to 1.0 (endgame)
     */
    public static double calculatePhaseValue(int absoluteMaterial) {
        // Starting material (full board): ~7800 (Q=900, R=500, B=330, N=320, P=100 per piece)
        // Using 6400 as max to account for typical early trades
        final int MAX_MATERIAL = 6400;  // Opening threshold
        final int MIN_MATERIAL = 1500;  // Deep endgame threshold (e.g., K+R vs K+R)

        if (absoluteMaterial >= MAX_MATERIAL) {
            return 0.0;  // Pure opening
        } else if (absoluteMaterial <= MIN_MATERIAL) {
            return 1.0;  // Pure endgame
        } else {
            // Linear interpolation between MIN and MAX
            double phaseValue = 1.0 - ((double)(absoluteMaterial - MIN_MATERIAL) / (MAX_MATERIAL - MIN_MATERIAL));
            return Math.max(0.0, Math.min(1.0, phaseValue));  // Clamp to [0, 1]
        }
    }

    /**
     * Alternative continuous phase calculation using piece count.
     *
     * @param pieceCount Total number of pieces on board (excluding kings)
     * @return Phase value from 0.0 (opening) to 1.0 (endgame)
     */
    public static double calculatePhaseValueByPieceCount(int pieceCount) {
        final int MAX_PIECES = 30;  // Full board (excluding kings)
        final int MIN_PIECES = 6;   // Deep endgame

        if (pieceCount >= MAX_PIECES) {
            return 0.0;  // Pure opening
        } else if (pieceCount <= MIN_PIECES) {
            return 1.0;  // Pure endgame
        } else {
            // Linear interpolation
            double phaseValue = 1.0 - ((double)(pieceCount - MIN_PIECES) / (MAX_PIECES - MIN_PIECES));
            return Math.max(0.0, Math.min(1.0, phaseValue));
        }
    }

    // ==================== DEPTH CALCULATION ====================

    /**
     * Configuration for depth calculation.
     * Easily tunable parameters for different playing styles.
     */
    public static class DepthConfig {
        // Base depths by phase (when time is not a constraint)
        public int openingDepth = 4;
        public int middlegameDepth = 6;
        public int endgameDepth = 8;

        // Time management factors
        public boolean useTimeManagement = true;
        public double timeBufferPercentage = 0.1;  // Reserve 10% of time
        public double criticalTimeThreshold = 10.0;  // Seconds - enter time trouble mode

        // Position complexity adjustments
        public boolean adjustForComplexity = true;
        public int maxComplexityBonus = 2;  // Max extra plies for complex positions
        public int maxComplexityReduction = 1;  // Max reduction for simple positions

        // Critical situation adjustments
        public boolean adjustForCheck = true;
        public int checkDepthBonus = 1;  // Extra depth when in check

        // Piece count adjustments
        public boolean adjustForPieceCount = true;
        public int fewPiecesThreshold = 8;  // Consider position "simple" below this
        public int simplificationBonus = 1;  // Extra depth in simplified positions
    }

    private static final DepthConfig DEFAULT_CONFIG = new DepthConfig();

    /**
     * Calculate optimal search depth based on all factors.
     *
     * @param board Current board position
     * @param absoluteMaterial Total material on board
     * @param pieceCount Number of pieces (excluding kings)
     * @param timeRemainingMs Time remaining in milliseconds (0 = no time control)
     * @param incrementMs Increment per move in milliseconds
     * @param movesPlayed Number of moves played so far
     * @return Recommended search depth in plies
     */
    public static int calculateDepth(Board board, int absoluteMaterial, int pieceCount,
                                     long timeRemainingMs, long incrementMs, int movesPlayed) {
        return calculateDepth(board, absoluteMaterial, pieceCount, timeRemainingMs,
                            incrementMs, movesPlayed, DEFAULT_CONFIG);
    }

    /**
     * Calculate optimal search depth with custom configuration.
     *
     * @param board Current board position
     * @param absoluteMaterial Total material on board
     * @param pieceCount Number of pieces (excluding kings)
     * @param timeRemainingMs Time remaining in milliseconds (0 = no time control)
     * @param incrementMs Increment per move in milliseconds
     * @param movesPlayed Number of moves played so far
     * @param config Custom depth configuration
     * @return Recommended search depth in plies
     */
    public static int calculateDepth(Board board, int absoluteMaterial, int pieceCount,
                                     long timeRemainingMs, long incrementMs, int movesPlayed,
                                     DepthConfig config) {
        // Step 1: Determine base depth from game phase
        Phase phase = detectPhase(absoluteMaterial);
        int baseDepth = getBaseDepth(phase, config);

        // Step 2: Apply time management adjustments
        int depth = baseDepth;
        if (config.useTimeManagement && timeRemainingMs > 0) {
            depth = adjustForTimeControl(depth, timeRemainingMs, incrementMs, movesPlayed, config);
        }

        // Step 3: Adjust for position complexity
        if (config.adjustForComplexity) {
            depth = adjustForComplexity(depth, board, pieceCount, config);
        }

        // Step 4: Adjust for critical situations
        if (config.adjustForCheck && board.inCheck()) {
            depth += config.checkDepthBonus;
        }

        // Step 5: Adjust for simplified positions (few pieces = easier to search deeper)
        if (config.adjustForPieceCount && pieceCount <= config.fewPiecesThreshold) {
            depth += config.simplificationBonus;
        }

        // Step 6: Ensure depth is within reasonable bounds
        depth = Math.max(1, Math.min(depth, 12));  // Clamp between 1 and 12

        return depth;
    }

    /**
     * Get base search depth for a phase.
     */
    private static int getBaseDepth(Phase phase, DepthConfig config) {
        return switch (phase) {
            case OPENING -> config.openingDepth;
            case MIDDLEGAME -> config.middlegameDepth;
            case ENDGAME -> config.endgameDepth;
        };
    }

    /**
     * Adjust depth based on time control.
     * Uses smart time allocation to avoid time trouble while maximizing thinking time.
     */
    private static int adjustForTimeControl(int baseDepth, long timeRemainingMs,
                                           long incrementMs, int movesPlayed,
                                           DepthConfig config) {
        double timeRemainingSec = timeRemainingMs / 1000.0;
        double incrementSec = incrementMs / 1000.0;

        // Critical time trouble - reduce depth significantly
        if (timeRemainingSec < config.criticalTimeThreshold) {
            return Math.max(1, baseDepth - 2);
        }

        // Estimate moves remaining (conservative estimate)
        int estimatedMovesRemaining = 40 - Math.min(movesPlayed, 30);
        estimatedMovesRemaining = Math.max(10, estimatedMovesRemaining);

        // Calculate time budget per move
        double reservedTime = timeRemainingSec * config.timeBufferPercentage;
        double availableTime = timeRemainingSec - reservedTime;
        double timePerMove = (availableTime / estimatedMovesRemaining) + incrementSec;

        // Adjust depth based on time budget
        // Rough heuristic: depth 4 = ~0.1s, depth 6 = ~1s, depth 8 = ~10s
        // Each +2 depth ≈ 10x time increase
        if (timePerMove > 30.0) {
            return baseDepth + 2;  // Lots of time, search deeper
        } else if (timePerMove > 10.0) {
            return baseDepth + 1;  // Good amount of time
        } else if (timePerMove > 3.0) {
            return baseDepth;  // Normal time
        } else if (timePerMove > 1.0) {
            return baseDepth - 1;  // Limited time
        } else {
            return baseDepth - 2;  // Very limited time
        }
    }

    /**
     * Adjust depth based on position complexity.
     * More complex positions (more legal moves, more pieces) may warrant deeper search
     * or shallower search depending on tactical vs positional nature.
     */
    private static int adjustForComplexity(int baseDepth, Board board, int pieceCount,
                                          DepthConfig config) {
        int legalMoveCount = board.generateLegalMoves().length;

        // High branching factor = complex tactical position
        // May need deeper search to see through tactics, but also more expensive
        if (legalMoveCount > 40) {
            // Very complex - slightly reduce to stay in time
            return Math.max(baseDepth - 1, baseDepth - config.maxComplexityReduction);
        } else if (legalMoveCount < 10) {
            // Very simple - can afford to search deeper
            return Math.min(baseDepth + 2, baseDepth + config.maxComplexityBonus);
        } else if (legalMoveCount < 20) {
            // Somewhat simple - slight depth increase
            return Math.min(baseDepth + 1, baseDepth + config.maxComplexityBonus);
        }

        return baseDepth;  // Normal complexity
    }

    // ==================== HELPER METHODS ====================

    /**
     * Estimate position complexity score (0-100).
     * Higher = more complex.
     *
     * Factors:
     * - Number of legal moves
     * - Number of pieces
     * - Material imbalance
     * - Pawn structure complexity
     */
    public static int estimateComplexity(Board board, int pieceCount, int materialBalance) {
        int complexity = 0;

        // Legal moves factor (0-40 points)
        int legalMoves = board.generateLegalMoves().length;
        complexity += Math.min(40, legalMoves);

        // Piece count factor (0-30 points)
        complexity += Math.min(30, pieceCount);

        // Material imbalance (0-20 points)
        // More imbalanced = more complex tactical considerations
        int imbalanceScore = Math.min(20, Math.abs(materialBalance) / 50);
        complexity += imbalanceScore;

        // Check/checkmate threat (0-10 points)
        if (board.inCheck()) {
            complexity += 10;
        }

        return Math.min(100, complexity);
    }

    /**
     * Calculate recommended move time based on time control.
     *
     * @param timeRemainingMs Time remaining in milliseconds
     * @param incrementMs Increment per move in milliseconds
     * @param movesPlayed Moves played so far
     * @param isComplexPosition Whether position is tactically complex
     * @return Recommended thinking time in milliseconds
     */
    public static long calculateMoveTime(long timeRemainingMs, long incrementMs,
                                        int movesPlayed, boolean isComplexPosition) {
        double timeRemainingSec = timeRemainingMs / 1000.0;
        double incrementSec = incrementMs / 1000.0;

        // Emergency time - move quickly
        if (timeRemainingSec < 5.0) {
            return 500;  // Half a second
        }

        // Critical time - move fast
        if (timeRemainingSec < 15.0) {
            return 2000;  // 2 seconds
        }

        // Estimate remaining moves
        int estimatedMovesRemaining = 40 - Math.min(movesPlayed, 30);
        estimatedMovesRemaining = Math.max(10, estimatedMovesRemaining);

        // Reserve 10% buffer
        double availableTime = timeRemainingSec * 0.9;
        double baseTimePerMove = (availableTime / estimatedMovesRemaining) + incrementSec;

        // Adjust for complexity
        double multiplier = isComplexPosition ? 1.5 : 1.0;
        double targetTime = baseTimePerMove * multiplier;

        // Cap at reasonable maximum
        targetTime = Math.min(targetTime, timeRemainingSec * 0.2);  // Never use more than 20% of remaining time

        return (long) (targetTime * 1000);
    }

    /**
     * Determine if we're in a critical game phase requiring extra calculation.
     *
     * @param board Current position
     * @param absoluteMaterial Total material
     * @return true if position warrants extra thinking time
     */
    public static boolean isCriticalPosition(Board board, int absoluteMaterial) {
        // In check
        if (board.inCheck()) {
            return true;
        }

        // Near checkmate (very low material)
        if (absoluteMaterial < 2000) {
            return true;
        }

        // Very few legal moves (forced play)
        if (board.generateLegalMoves().length < 5) {
            return true;
        }

        return false;
    }

    // ==================== BACKWARD COMPATIBILITY ====================

    /**
     * Simple depth lookup for backward compatibility.
     * Uses only material to determine phase.
     *
     * @param absoluteMaterial Total material on board
     * @return Search depth
     * @deprecated Use calculateDepth() for more sophisticated depth selection
     */
    @Deprecated
    public static int getSearchDepth(int absoluteMaterial) {
        Phase phase = detectPhase(absoluteMaterial);
        return getBaseDepth(phase, DEFAULT_CONFIG);
    }

    /**
     * Legacy method for getting phase-based depth.
     *
     * @param phase Game phase
     * @return Search depth
     * @deprecated Use calculateDepth() for more sophisticated depth selection
     */
    @Deprecated
    public static int getDepthForPhase(Phase phase) {
        return getBaseDepth(phase, DEFAULT_CONFIG);
    }
}
