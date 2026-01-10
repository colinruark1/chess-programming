/* ChessEngine.java */
package mybot;

import com.github.bhlangonijr.chesslib.*;
import com.github.bhlangonijr.chesslib.move.Move;
import java.util.*;
import java.util.stream.Collectors;

public class ChessEngine {
    public Board board;
    public PieceTracker pieceTracker;

    private final Map<Long, TranspositionEntry> transpositionTable;
    private int ttHits, ttMisses;

    private static final int MAX_TT_SIZE = 2_000_000;  // Increased from 500K for better hit rate
    private static final int MAX_QUIESCENCE_DEPTH = 20;
    private static final int MAX_PLY = 100;
    private static final int MIN_GUARANTEED_DEPTH = 4;  // Always complete at least this depth in untimed games

    private static final int MATE_SCORE = 100_000;
    private static final int INF        =  99_000; // strictly < MATE_SCORE

    private static int searchCallCounter = 0;
    private static final boolean USE_Q  = true;   // flip for A/B tests
    private static final boolean USE_TT = true;   // flip for A/B tests

    private static final Random rand = new Random();

    // Time control tracking for adaptive depth
    private long whiteTimeRemainingMs = 0;  // 0 = no time control
    private long blackTimeRemainingMs = 0;
    private long incrementMs = 0;
    private int movesPlayed = 0;

    // Iterative deepening infrastructure
    private long nodesSearched = 0;                      // Total nodes searched this move
    private static final int NODE_CHECK_INTERVAL = 10000; // Check time every 10k nodes
    private volatile boolean searchStopped = false;      // Flag to stop search
    private TimeManager timeManager;                     // Time management

    // Killer move heuristic: store 2 killer moves per ply
    private final Move[][] killerMoves = new Move[MAX_PLY][2];

    // Statistics for move ordering analysis
    private int totalBetaCutoffs = 0;
    private int firstMoveCutoffs = 0;
    private int[] cutoffsByMoveIndex = new int[50];
    private int killerMoveHits = 0;
    private int ttMoveHits = 0;
    private int totalMovesSearched = 0;

    // Principal Variation tracking
    private final Move[][] principalVariation = new Move[MAX_PLY][MAX_PLY];
    private final int[] pvLength = new int[MAX_PLY];

    public ChessEngine() {
        board = new Board();
        pieceTracker = new PieceTracker();
        pieceTracker.initializeFromBoard(board);

        transpositionTable = new LinkedHashMap<>() {
            @Override protected boolean removeEldestEntry(Map.Entry<Long, TranspositionEntry> e) {
                return size() > MAX_TT_SIZE;
            }
        };
        ttHits = ttMisses = 0;
    }

    public void printUciId() {
        System.out.println("id name MyBot");
        System.out.println("id author JavaBotDev");
        System.out.println("uciok");
    }

    public void printEval() { System.out.println(evaluate(0)); }
    public void printReadyOk() { System.out.println("readyok"); }

    public boolean isLegalMove(String moveStr) {
        try {
            Move move = parseMove(moveStr);
            return board.legalMoves().contains(move);
        } catch (Exception e) {
            return false;
        }
    }

    public void newGame() {
        board = new Board();
        pieceTracker.initializeFromBoard(board);
        transpositionTable.clear();
        ttHits = ttMisses = 0;
        movesPlayed = 0;
        // Clear killer moves
        for (int i = 0; i < MAX_PLY; i++) {
            killerMoves[i][0] = null;
            killerMoves[i][1] = null;
        }
    }

    /**
     * Set time control parameters for adaptive depth calculation.
     * Call this before the game starts or when time control changes.
     *
     * @param whiteTimeMs Time remaining for white in milliseconds (0 = no time control)
     * @param blackTimeMs Time remaining for black in milliseconds
     * @param incrementMs Increment added per move in milliseconds
     */
    public void setTimeControl(long whiteTimeMs, long blackTimeMs, long incrementMs) {
        this.whiteTimeRemainingMs = whiteTimeMs;
        this.blackTimeRemainingMs = blackTimeMs;
        this.incrementMs = incrementMs;
    }

    /**
     * Update time after a move is made.
     * Should be called after each move in timed games.
     *
     * @param side The side that just moved
     * @param timeUsedMs Time used for the move in milliseconds
     */
    public void updateTime(Side side, long timeUsedMs) {
        if (side == Side.WHITE) {
            whiteTimeRemainingMs -= timeUsedMs;
            whiteTimeRemainingMs += incrementMs;  // Add increment
            if (whiteTimeRemainingMs < 0) whiteTimeRemainingMs = 0;
        } else {
            blackTimeRemainingMs -= timeUsedMs;
            blackTimeRemainingMs += incrementMs;  // Add increment
            if (blackTimeRemainingMs < 0) blackTimeRemainingMs = 0;
        }
        movesPlayed++;
    }

    /**
     * Get current move count.
     */
    public int getMovesPlayed() {
        return movesPlayed;
    }

    public void parsePosition(String line) {
        System.out.println("start");
        String[] tokens = line.split("\\s+");
        int idx = 1;

        if (tokens[idx].equals("startpos")) {
            board = new Board();
            idx++;
        } else if (tokens[idx].equals("fen")) {
            board = new Board();
            StringBuilder fenBuilder = new StringBuilder();
            idx++;
            for (int i = 0; i < 6 && idx < tokens.length; i++, idx++) {
                fenBuilder.append(tokens[idx]).append(" ");
            }
            String fen = fenBuilder.toString().trim();
            board.loadFromFen(fen);
            // It is not necessary (and can be risky) to mutate history manually here
            System.out.println("Starting from FEN: " + fen);
        } else {
            System.err.println("Invalid position format.");
            return;
        }

        // Clear TT when loading new position to avoid stale mate scores from previous searches
        transpositionTable.clear();
        ttHits = ttMisses = 0;
        movesPlayed = 0;  // Reset move counter

        pieceTracker.initializeFromBoard(board);
        System.out.println("Side to move: " + board.getSideToMove());
        System.out.println("Raw material score: " + pieceTracker.getMaterialScore(board));

        if (idx < tokens.length && tokens[idx].equals("moves")) {
            idx++;
            while (idx < tokens.length) {
                String moveStr = tokens[idx++];
                try {
                    Move move = parseMove(moveStr);
                    Piece movingPiece = board.getPiece(move.getFrom());
                    Piece capturedPiece = board.getPiece(move.getTo());
                    board.doMove(move);
                    pieceTracker.applyMove(move, movingPiece, capturedPiece, board);
                    movesPlayed++;  // Track moves played
                } catch (Exception e) {
                    System.err.println("Failed to parse or apply move: " + moveStr + " — " + e.getMessage());
                }
            }
        }
        System.out.println("Position loaded. FEN now: " + board.getFen());
    }

    /** Parses SAN-free UCI moves (e.g. "e2e4" or "e7e8q") against this.board */
    private Move parseMove(String moveStr) {
        String s = moveStr.toUpperCase();
        Square from = Square.fromValue(s.substring(0, 2));
        Square to   = Square.fromValue(s.substring(2, 4));

        if (s.length() == 5) {
            PieceType promo = switch (s.charAt(4)) {
                case 'Q' -> PieceType.QUEEN;
                case 'R' -> PieceType.ROOK;
                case 'B' -> PieceType.BISHOP;
                case 'N' -> PieceType.KNIGHT;
                default  -> null;
            };
            if (promo != null) {
                return new Move(from, to, Piece.make(board.getSideToMove(), promo));
            }
        }
        return new Move(from, to);
    }

    /**
     * Select best move with iterative deepening search.
     * @param goCmd UCI go command parameters (time control, depth limits, etc.)
     * @return best move found
     */
    public Move selectBestMove(GoCommand goCmd) {
        // Backward compatibility: if null, use infinite search
        if (goCmd == null) {
            goCmd = new GoCommand();
            goCmd.infinite = true;
        }

        // Initialize time management
        timeManager = new TimeManager();
        timeManager.planTimeForMove(goCmd, board.getSideToMove(), movesPlayed);

        // Reset search state
        nodesSearched = 0;
        searchStopped = false;
        totalBetaCutoffs = firstMoveCutoffs = 0;
        Arrays.fill(cutoffsByMoveIndex, 0);
        killerMoveHits = ttMoveHits = 0;
        totalMovesSearched = 0;

        Profiler.reset();
        Profiler.start("selectBestMove");

        List<Move> legalMoves = board.legalMoves();
        System.out.println("There are " + legalMoves.size() + " legal moves.");
        if (legalMoves.isEmpty()) return null;

        // Sort moves once at the beginning
        legalMoves.sort((a, b) -> Integer.compare(scoreMove(b, 0), scoreMove(a, 0)));

        // Determine depth bounds
        GamePhase.Phase currentPhase = pieceTracker.getCurrentPhase();
        int absoluteMaterial = pieceTracker.getAbsoluteMaterial();
        int pieceCount = 0;
        for (Square sq : Square.values()) {
            Piece p = board.getPiece(sq);
            if (p != null && p != Piece.NONE && p.getPieceType() != PieceType.KING) {
                pieceCount++;
            }
        }

        long timeRemainingMs = (board.getSideToMove() == Side.WHITE) ?
                               whiteTimeRemainingMs : blackTimeRemainingMs;

        // Determine depth bounds for iterative deepening
        int minDepth;
        int maxDepth;

        if (goCmd.hasDepthLimit()) {
            // User specified depth limit: iterate from 1 to that depth
            minDepth = 1;
            maxDepth = goCmd.maxDepth;
        } else {
            // Always start from depth 1 to ensure at least one iteration completes
            // This prevents falling back to heuristic move ordering
            minDepth = 1;
            maxDepth = 64;
        }

        maxDepth = Math.max(maxDepth, minDepth);

        System.out.printf("Iterative deepening: depth range [%d, %d], allocated time: %dms%n",
                         minDepth, maxDepth, timeManager.getAllocatedTimeMs());

        // Disable phase transitions during search
        pieceTracker.disablePhaseTransitions();

        // Best move tracking across iterations
        Move bestMove = null;
        int bestScore = Integer.MIN_VALUE;
        List<Move> bestPV = new ArrayList<>();

        // Use fixed window (no aspiration windows)
        final int rootAlpha = -INF;
        final int rootBeta = +INF;

        // ==================== ITERATIVE DEEPENING LOOP ====================
        for (int currentDepth = minDepth; currentDepth <= maxDepth; currentDepth++) {
            long iterationStartMs = System.currentTimeMillis();

            // Clear PV for this iteration
            pvLength[0] = 0;

            Move iterationBestMove = null;
            int iterationBestScore = Integer.MIN_VALUE;
            List<Move> iterationPV = new ArrayList<>();

            // Search all root moves at current depth
            for (Move move : legalMoves) {
                // Check if time is up BEFORE starting to search a new root move
                // But always complete at least MIN_GUARANTEED_DEPTH in untimed games
                boolean shouldStopForTime = timeManager != null && timeManager.isTimeUp() &&
                                          (goCmd.hasTimeControl() || currentDepth >= MIN_GUARANTEED_DEPTH);
                if (searchStopped || shouldStopForTime) {
                    searchStopped = true;
                    break;
                }

                Piece movingPiece = board.getPiece(move.getFrom());
                Piece capturedPiece = board.getPiece(move.getTo());

                board.doMove(move);
                pieceTracker.applyMove(move, movingPiece, capturedPiece, board);

                int score = -negamax(currentDepth - 1, -rootBeta, -rootAlpha, 1);

                board.undoMove();
                pieceTracker.undoMove(move, movingPiece, capturedPiece, board);

                // If search was stopped during negamax, this score is unreliable
                if (searchStopped) {
                    break;
                }

                if (score > iterationBestScore) {
                    iterationBestScore = score;
                    iterationBestMove = move;

                    // Build PV for this iteration
                    iterationPV.clear();
                    iterationPV.add(move);
                    for (int i = 0; i < pvLength[1]; i++) {
                        iterationPV.add(principalVariation[1][i]);
                    }
                }
            }

            // Only update best move if iteration COMPLETED (wasn't stopped mid-iteration)
            if (!searchStopped && iterationBestMove != null) {
                bestMove = iterationBestMove;
                bestScore = iterationBestScore;
                bestPV = new ArrayList<>(iterationPV);

                // Output UCI info for this completed iteration
                long totalTimeMs = timeManager.getElapsedMs();
                outputUciInfo(currentDepth, bestScore, totalTimeMs, nodesSearched, bestPV);

                System.out.printf("info string Depth %d completed in %dms (score: %d cp, nodes: %d)%n",
                                 currentDepth, System.currentTimeMillis() - iterationStartMs,
                                 bestScore, nodesSearched);
            } else {
                // Iteration was interrupted, use previous iteration's result
                System.out.printf("info string Depth %d incomplete, using depth %d result%n",
                                 currentDepth, currentDepth - 1);
                break;
            }

            // Stop if time is up (even if iteration completed)
            // But always complete at least MIN_GUARANTEED_DEPTH in untimed games
            if (timeManager != null && timeManager.isTimeUp()) {
                if (goCmd.hasTimeControl() || currentDepth >= MIN_GUARANTEED_DEPTH) {
                    System.out.printf("info string Time limit reached, stopping at depth %d%n", currentDepth);
                    break;
                }
                // In untimed games, continue until MIN_GUARANTEED_DEPTH is reached
            }

            // Stop if we found a mate
            if (isMateScore(bestScore)) {
                System.out.printf("info string Mate found, stopping search%n");
                break;
            }
        }

        // Emergency fallback if no iteration completed (extremely rare)
        if (bestMove == null && !legalMoves.isEmpty()) {
            bestMove = legalMoves.get(0);
            System.out.println("info string WARNING: No iterations completed, using first legal move");
        }

        // Re-enable phase transitions
        pieceTracker.enablePhaseTransitions();

        Profiler.stop("selectBestMove");

        // Output final statistics
        System.out.printf("info string TT hits: %d, misses: %d, hit rate: %.2f%%%n",
                ttHits, ttMisses, (ttHits + ttMisses) > 0 ? 100.0 * ttHits / (ttHits + ttMisses) : 0.0);

        Profiler.printReport();
        printMoveOrderingStats();

        // Keep the TT between moves for strength; clear only if you must.
        // transpositionTable.clear();

        return bestMove;
    }

    /**
     * Backward compatibility wrapper for selectBestMove with no parameters.
     * Uses infinite search mode (no time limit).
     * @return best move found
     */
    public Move selectBestMove() {
        GoCommand defaultCmd = new GoCommand();
        defaultCmd.infinite = true;
        return selectBestMove(defaultCmd);
    }

    private boolean givesCheck(Move move) {
        // Safe: do/undo on board only; do NOT touch pieceTracker here.
        board.doMove(move);
        boolean check = board.isKingAttacked();
        board.undoMove();
        return check;
    }

    /**
     * Output UCI info string for a completed iteration.
     * Format: info depth <d> score cp <s> time <t> nodes <n> nps <nps> pv <moves...>
     *
     * @param depth current search depth
     * @param score evaluation score in centipawns
     * @param timeMs time elapsed in milliseconds
     * @param nodes total nodes searched
     * @param pv principal variation (best line)
     */
    private void outputUciInfo(int depth, int score, long timeMs, long nodes, List<Move> pv) {
        StringBuilder sb = new StringBuilder();
        sb.append("info");

        // Depth
        sb.append(" depth ").append(depth);

        // Score (centipawns or mate)
        if (isMateScore(score)) {
            // Calculate mate distance in plies, convert to moves
            int mateDistance = (score > 0) ? (MATE_SCORE - score) : (-MATE_SCORE - score);
            int mateInMoves = (mateDistance + 1) / 2;  // Convert plies to moves
            if (score < 0) mateInMoves = -mateInMoves;
            sb.append(" score mate ").append(mateInMoves);
        } else {
            sb.append(" score cp ").append(score);
        }

        // Time in milliseconds
        sb.append(" time ").append(timeMs);

        // Nodes searched
        sb.append(" nodes ").append(nodes);

        // Nodes per second
        if (timeMs > 0) {
            long nps = (nodes * 1000) / timeMs;
            sb.append(" nps ").append(nps);
        }

        // Principal variation
        if (pv != null && !pv.isEmpty()) {
            sb.append(" pv");
            for (Move m : pv) {
                sb.append(" ").append(m);
            }
        }

        System.out.println(sb.toString());
    }

    /**
     * Scores moves for ORDERING ONLY (search efficiency, not move quality).
     * Higher scores = search first (more likely to cause beta cutoff).
     * This does NOT indicate the move is tactically better!
     *
     * Priority: TT move > Promotions > Captures (MVV-LVA) > Killers > Others
     */
    private int scoreMove(Move move, int ply) {
        Profiler.start("scoreMove");
        Piece attacker = board.getPiece(move.getFrom());
        Piece victim   = board.getPiece(move.getTo());

        // Get TT move for ordering (search it first)
        Move ttMove = null;
        if (USE_TT) {
            TranspositionEntry e = transpositionTable.get(board.getZobristKey());
            if (e != null) ttMove = e.bestMove;
        }

        int score = 0;

        boolean isCapture = (victim != null && victim.getPieceType() != null && victim.getPieceType() != PieceType.NONE);
        boolean isPromotion = (move.getPromotion() != null && move.getPromotion().getPieceType() != null && move.getPromotion().getPieceType() != PieceType.NONE);
        boolean isTTMove = (ttMove != null && move.equals(ttMove));

        // ORDERING PRIORITY (for search efficiency):
        // 1. TT move (highest priority - likely to be best)
        // 2. Promotions (usually strong)
        // 3. Captures (MVV-LVA)
        // 4. Killer moves (quiet moves that caused cutoffs)
        // 5. Other quiet moves

        if (isTTMove) {
            // TT move gets highest priority for ORDERING
            // This doesn't mean it's good, just that we should search it first
            score = 1_000_000_000;
            // Add tactical bonuses on top
            if (isPromotion) score += getPieceValue(move.getPromotion());
            else if (isCapture) {
                int mvv = getPieceValue(victim);
                int lva = getPieceValue(attacker);
                score += (mvv * 10 - lva);
            }
        }
        else if (isPromotion) {
            // Promotions: very high priority
            score = 100_000_000 + getPieceValue(move.getPromotion());
        }
        else if (isCapture) {
            // Captures: MVV-LVA (Most Valuable Victim - Least Valuable Attacker)
            int mvv = getPieceValue(victim);
            int lva = getPieceValue(attacker);
            score = 10_000_000 + (mvv * 10 - lva);
        }
        else {
            // Quiet moves: check killers
            if (ply >= 0 && ply < MAX_PLY) {
                if (move.equals(killerMoves[ply][0])) score = 9_000;
                else if (move.equals(killerMoves[ply][1])) score = 8_000;
            }
            // Other quiet moves get low scores
        }

        Profiler.stop("scoreMove");
        return score;
    }

    private int moveOrder(Move move, int ply) {
        int score = 0;

        Piece attacker = board.getPiece(move.getFrom());
        Piece victim = board.getPiece(move.getTo());

        // Captures (prioritize by MVV-LVA)
        if (victim != null && victim.getPieceType() != null && victim.getPieceType() != PieceType.NONE) {
            int mvv = getPieceValue(victim);
            int lva = getPieceValue(attacker);
            score += 100_000 + (mvv * 10 - lva);
        }

        // NOTE: We've removed checks and "attacks on major pieces" heuristics because they
        // require board manipulation (doMove/undoMove) which can corrupt the board state
        // during move sorting. Captures alone provide sufficient move ordering.

        return score;
    }

    public void runManualMode(Scanner input) {
        // Start a fresh game for manual mode
        board = new Board();
        pieceTracker.initializeFromBoard(board);
        transpositionTable.clear();
        ttHits = ttMisses = 0;
        // Clear killer moves
        for (int i = 0; i < MAX_PLY; i++) {
            killerMoves[i][0] = null;
            killerMoves[i][1] = null;
        }

        System.out.print("Pick your pieces! (white/black): ");
        String side = input.nextLine().trim().toLowerCase();
        boolean userIsWhite = side.equals("white");

        while (!board.isMated() && !board.isDraw()) {
            if (board.getSideToMove().equals(userIsWhite ? Side.WHITE : Side.BLACK)) {
                System.out.println("Your move (e.g. e2e4): ");
                String moveStr = input.nextLine().trim();
                Move move = parseMove(moveStr);

                if (move == null || !board.legalMoves().contains(move)) {
                    System.out.println("Illegal move. Try again.");
                    continue;
                }

                Piece movingPiece = board.getPiece(move.getFrom());
                Piece capturedPiece = board.getPiece(move.getTo());
                board.doMove(move);
                pieceTracker.applyMove(move, movingPiece, capturedPiece, board);

                transpositionTable.clear();
            } else {
                Move botMove = selectBestMove();
                if (botMove == null) {
                    System.out.println("No legal moves for bot.");
                    break;
                }

                Piece movingPiece = board.getPiece(botMove.getFrom());
                Piece capturedPiece = board.getPiece(botMove.getTo());
                board.doMove(botMove);
                pieceTracker.applyMove(botMove, movingPiece, capturedPiece, board);

                // Announce the move so the human can respond
                System.out.println("Computer move: " + botMove);
                // Uncomment for extra context:
                System.out.println("Position: " + board.getFen());
                // System.out.println("Your turn (" + board.getSideToMove() + ")");

                transpositionTable.clear();
            }
        }

        if (board.isMated()) {
            System.out.println("Checkmate! " + (board.getSideToMove() == Side.WHITE ? "Black" : "White") + " wins.");
        } else {
            System.out.println("Game drawn.");
        }
    }

    // ===================== SEARCH =====================

    private int negamax(int depth, int alpha, int beta, int ply) {
        Profiler.start("negamax");

        // Clamp to mate window to avoid overflow on sign flip
        if (alpha <= -MATE_SCORE) alpha = -MATE_SCORE + 1;
        if (beta  >=  MATE_SCORE) beta  =  MATE_SCORE - 1;

        if (alpha >= beta) {
            System.err.printf("Window error: alpha(%d) >= beta(%d) at depth %d, fen=%s%n",
                    alpha, beta, depth, board.getFen());
            beta = alpha + 1;
        }

        // Node counting and time checking for iterative deepening
        nodesSearched++;
        if (nodesSearched % NODE_CHECK_INTERVAL == 0) {
            if (timeManager != null && timeManager.isTimeUp()) {
                searchStopped = true;
            }
        }

        // Early exit if search stopped
        if (searchStopped) {
            Profiler.stop("negamax");
            return alpha;
        }

        long key = board.getZobristKey();
        TranspositionEntry cached = USE_TT ? transpositionTable.get(key) : null;

        // ─── 1) TT Lookup ───────────────────────────────────────────────────
        if (cached != null && cached.depth >= depth) {
            ttHits++;
            switch (cached.type) {
                case EXACT:
                    // EXACT score at same or greater depth - we can return immediately
                    // This is safe because EXACT means we fully searched this position
                    Profiler.stop("negamax");
                    return cached.score;
                case LOWERBOUND:
                    // The actual score is AT LEAST cached.score
                    // Update alpha to reflect this knowledge
                    alpha = Math.max(alpha, cached.score);
                    if (alpha >= beta) {
                        // Beta cutoff based on TT lowerbound - this is safe
                        Profiler.stop("negamax");
                        return cached.score;
                    }
                    break;
                case UPPERBOUND:
                    // The actual score is AT MOST cached.score
                    // Update beta to reflect this knowledge
                    beta  = Math.min(beta, cached.score);
                    if (alpha >= beta) {
                        // Alpha cutoff based on TT upperbound - this is safe
                        Profiler.stop("negamax");
                        return cached.score;
                    }
                    break;
            }
            // TT info used to narrow window, but we continue searching if no cutoff
        } else {
            if (USE_TT) ttMisses++;
        }

        // ─── Terminal & frontier ────────────────────────────────────────────
        if (board.isMated() || board.isDraw()) {
            pvLength[ply] = 0;  // No PV at terminal nodes
            return evaluate(ply);
        }
        if (depth == 0) {
            pvLength[ply] = 0;  // No PV at leaf nodes
            // Both quiescence and evaluate return scores from current side's perspective
            // Do NOT negate quiescence - it's not a recursive negamax call
            return USE_Q ? quiescence(alpha, beta, ply) : evaluate(ply);
        }

        pvLength[ply] = 0;  // Initialize PV length for this ply
        int alphaOrig = alpha;
        int betaOrig  = beta;
        int bestScore = Integer.MIN_VALUE;
        Move bestMove = null;

        Profiler.start("legalMoves");
        List<Move> legalMoves = board.legalMoves();
        Profiler.stop("legalMoves");
        if (legalMoves.isEmpty()) {
            Profiler.stop("negamax");
            return evaluate(ply); // stalemate handled in evaluate()
        }

        // Move ordering
        Profiler.start("moveOrdering");
        legalMoves.sort((a, b) -> Integer.compare(scoreMove(b, ply), scoreMove(a, ply)));
        Profiler.stop("moveOrdering");

        // Get TT move for statistics tracking
        Move ttMove = null;
        if (USE_TT) {
            TranspositionEntry e = transpositionTable.get(key);
            if (e != null) ttMove = e.bestMove;
        }

        for (int moveIndex = 0; moveIndex < legalMoves.size(); moveIndex++) {
            Move move = legalMoves.get(moveIndex);
            totalMovesSearched++;

            Piece movingPiece = board.getPiece(move.getFrom());
            Piece capturedPiece = board.getPiece(move.getTo());

            // Debug: Check if this is g6g8 at ply 1
            boolean isG6G8 = move.toString().equals("g6g8") && ply == 1;
            if (isG6G8) {
                System.out.println("\n>>> FOUND g6g8 at ply=1, depth=" + depth);
                System.out.println("    FEN before g6g8: " + board.getFen());
            }

            board.doMove(move);
            Profiler.start("applyMove");
            pieceTracker.applyMove(move, movingPiece, capturedPiece, board);
            Profiler.stop("applyMove");

            if (isG6G8) {
                System.out.println("    FEN after g6g8: " + board.getFen());
                System.out.println("    Is mated? " + board.isMated());
                System.out.println("    About to search with depth=" + (depth-1) + ", ply=" + (ply+1));
            }

            int score = -negamax(depth - 1, -beta, -alpha, ply + 1);

            if (isG6G8) {
                System.out.println("    Score returned: " + score);
                System.out.println("<<< END g6g8\n");
            }

            board.undoMove();
            Profiler.start("undoMove");
            pieceTracker.undoMove(move, movingPiece, capturedPiece, board);
            Profiler.stop("undoMove");

            if (score > bestScore) {
                bestScore = score;
                bestMove  = move;

                // Update principal variation when we find a new best move
                principalVariation[ply][0] = move;
                // Copy the PV from the child node
                for (int i = 0; i < pvLength[ply + 1]; i++) {
                    principalVariation[ply][i + 1] = principalVariation[ply + 1][i];
                }
                pvLength[ply] = pvLength[ply + 1] + 1;

            }
            if (score > alpha) {
                alpha = score;
            }
            if (alpha >= beta) {
                // Record beta cutoff statistics
                totalBetaCutoffs++;
                if (moveIndex == 0) firstMoveCutoffs++;
                if (moveIndex < cutoffsByMoveIndex.length) {
                    cutoffsByMoveIndex[moveIndex]++;
                }

                // Track if this was a TT or killer move
                if (ttMove != null && move.equals(ttMove)) {
                    ttMoveHits++;
                } else if (ply >= 0 && ply < MAX_PLY &&
                          (move.equals(killerMoves[ply][0]) || move.equals(killerMoves[ply][1]))) {
                    killerMoveHits++;
                }

                // Store killer move if it's a quiet move (not a capture)
                if (capturedPiece == null || capturedPiece == Piece.NONE) {
                    if (ply < MAX_PLY) {
                        killerMoves[ply][1] = killerMoves[ply][0];
                        killerMoves[ply][0] = move;
                    }
                }
                break; // β-cutoff
            }
        }

        // ─── Store TT ───────────────────────────────────────────────────────
        if (USE_TT) {
            TranspositionEntry.EntryType type;
            if      (bestScore <= alphaOrig) type = TranspositionEntry.EntryType.UPPERBOUND;
            else if (bestScore >= betaOrig)  type = TranspositionEntry.EntryType.LOWERBOUND;
            else                             type = TranspositionEntry.EntryType.EXACT;

            TranspositionEntry old = transpositionTable.get(key);
            int s = Math.max(-MATE_SCORE + 1, Math.min(bestScore, MATE_SCORE - 1));
            if (old == null || depth >= old.depth) {
                transpositionTable.put(key, new TranspositionEntry(depth, s, type, bestMove));
            }
        }

        searchCallCounter++;
        Profiler.stop("negamax");
        return bestScore;
    }

    // ===================== QUIESCENCE =====================

    private int quiescence(int alpha, int beta, int ply, int qDepth, Set<Long> visited) {
        Profiler.start("quiescence");

        // Node counting and early exit if search stopped
        nodesSearched++;
        if (searchStopped) {
            Profiler.stop("quiescence");
            return alpha;
        }

        if (qDepth > MAX_QUIESCENCE_DEPTH) {
            Profiler.stop("quiescence");
            return evaluate(ply);
        }

        long key = board.getZobristKey();
        if (!visited.add(key)) {
            Profiler.stop("quiescence");
            return evaluate(ply);
        }

        int standPat = evaluate(ply);
        if (standPat >= beta) {
            Profiler.stop("quiescence");
            return beta;
        }
        if (alpha < standPat) alpha = standPat;

        // Get all legal moves
        Profiler.start("legalMoves");
        List<Move> allMoves = board.legalMoves();
        Profiler.stop("legalMoves");
        List<Move> moves = new ArrayList<>(allMoves.size());

        // Manual filter for captures, promotions, and checks
        // Only search checks at qDepth 0 to avoid explosion
        boolean hasG6G8 = false;
        for (Move m : allMoves) {
            boolean isCapture = pieceTracker.isCapture(m, board);
            boolean isPromotion = pieceTracker.isPromotion(m, board);
            boolean isCheck = (qDepth == 0 && givesCheck(m));

            if (m.toString().equals("g6g8") && ply == 1 && qDepth == 0) {
                hasG6G8 = true;
                System.out.println("\n*** QUIESCENCE: g6g8 found! ply=" + ply + ", qDepth=" + qDepth);
                System.out.println("    isCapture=" + isCapture + ", isPromotion=" + isPromotion + ", isCheck=" + isCheck);
                System.out.println("    FEN: " + board.getFen());

                // Check what score g6g8 would get
                board.doMove(m);
                boolean isMate = board.isMated();
                int evalScore = evaluate(ply);
                board.undoMove();
                System.out.println("    After g6g8: isMated=" + isMate + ", eval=" + evalScore);
            }

            if (isCapture || isPromotion) {
                moves.add(m);
            } else if (isCheck) {
                // Only search checking moves at the first quiescence level
                moves.add(m);
                if (m.toString().equals("g6g8") && ply == 1 && qDepth == 0) {
                    System.out.println("    g6g8 ADDED to quiescence search list");
                }
            }
        }

        if (ply == 1 && qDepth == 0 && !hasG6G8) {
            System.out.println("\n*** WARNING: g6g8 NOT in legal moves at ply=1, qDepth=0!");
            System.out.println("    FEN: " + board.getFen());
            System.out.println("    Legal moves: " + allMoves);
        }

        // Sort by move score (use -1 for quiescence since it's not part of main search tree)
        Profiler.start("moveOrdering");
        moves.sort((a, b) -> Integer.compare(scoreMove(b, -1), scoreMove(a, -1)));
        Profiler.stop("moveOrdering");

        for (Move move : moves) {
            Piece attacker = board.getPiece(move.getFrom());
            Piece victim   = board.getPiece(move.getTo());

            // Gate very bad trades, but allow non-captures (checks) through
            boolean isCapture = (victim != null && victim != Piece.NONE);
            if (isCapture && !isWorthCapturing(move, attacker, victim)) {
                continue;
            }

            boolean isG6G8InQ = move.toString().equals("g6g8") && ply == 1;
            if (isG6G8InQ) {
                System.out.println("\n>>> SEARCHING g6g8 in quiescence! ply=" + ply + ", qDepth=" + qDepth);
            }

            board.doMove(move);
            Profiler.start("applyMove");
            pieceTracker.applyMove(move, attacker, victim, board);
            Profiler.stop("applyMove");

            int score = -quiescence(-beta, -alpha, ply, qDepth + 1, visited);

            if (isG6G8InQ) {
                System.out.println("    g6g8 score from quiescence: " + score);
                System.out.println("<<< END g6g8 quiescence search\n");
            }

            board.undoMove();
            Profiler.start("undoMove");
            pieceTracker.undoMove(move, attacker, victim, board);
            Profiler.stop("undoMove");

            if (score >= beta) {
                Profiler.stop("quiescence");
                return beta;
            }
            if (score > alpha) alpha = score;
        }
        Profiler.stop("quiescence");
        return alpha;
    }

    private int quiescence(int alpha, int beta, int ply) {
        int result = quiescence(alpha, beta, ply, 0, new HashSet<>());
        return result;
    }

    // ===================== EVALUATION =====================

    private int evaluate(int ply) {
        Profiler.start("evaluate");
        if (board.isMated()) {
            Profiler.stop("evaluate");
            return -MATE_SCORE + ply;
        }
        if (board.isDraw()) {
            Profiler.stop("evaluate");
            return 0;
        }

        // materialScore includes base material + PST bonuses (White perspective)
        int materialScore = pieceTracker.getMaterialScore(board);

        // Cache legal moves once
        Profiler.start("legalMoves");
        List<Move> currentMoves = board.legalMoves();
        Profiler.stop("legalMoves");

        int mobilityScore = currentMoves.size();
        boolean success = board.doNullMove();
        if (success) {
            Profiler.start("legalMoves");
            mobilityScore -= board.legalMoves().size();
            Profiler.stop("legalMoves");
            board.undoMove();
        } else {
            mobilityScore = 0;
        }
        mobilityScore *= 10;

        Profiler.stop("evaluate");

        // Material score (including PST) is from White's perspective - convert to side-to-move
        int materialForSideToMove = (board.getSideToMove() == Side.WHITE) ? materialScore : -materialScore;

        // Mobility is already from side-to-move perspective
        return materialForSideToMove + mobilityScore;
    }

    private void printMoveOrderingStats() {
        if (totalBetaCutoffs == 0) {
            System.out.println("\n========== MOVE ORDERING STATISTICS ==========");
            System.out.println("No beta cutoffs occurred.");
            System.out.println("==============================================\n");
            return;
        }

        System.out.println("\n========== MOVE ORDERING STATISTICS ==========");
        System.out.printf("Total beta cutoffs: %d%n", totalBetaCutoffs);
        System.out.printf("First move cutoffs: %d (%.1f%%)%n",
            firstMoveCutoffs,
            100.0 * firstMoveCutoffs / totalBetaCutoffs);

        System.out.println("\nCutoff distribution by move index:");
        for (int i = 0; i < 10 && i < cutoffsByMoveIndex.length; i++) {
            if (cutoffsByMoveIndex[i] > 0) {
                System.out.printf("  Move %d: %d (%.1f%%)%n",
                    i, cutoffsByMoveIndex[i],
                    100.0 * cutoffsByMoveIndex[i] / totalBetaCutoffs);
            }
        }

        System.out.printf("\nTT move cutoffs: %d (%.1f%%)%n",
            ttMoveHits, 100.0 * ttMoveHits / totalBetaCutoffs);
        System.out.printf("Killer move cutoffs: %d (%.1f%%)%n",
            killerMoveHits, 100.0 * killerMoveHits / totalBetaCutoffs);

        // Calculate average branch factor
        double avgBranchFactor = (double) totalMovesSearched / totalBetaCutoffs;
        System.out.printf("\nAverage branch factor: %.2f moves/cutoff%n", avgBranchFactor);
        System.out.printf("Total moves searched: %d%n", totalMovesSearched);
        System.out.println("==============================================\n");
    }

    private boolean isMateScore(int score) {
        return score > MATE_SCORE - MAX_PLY || score < -MATE_SCORE + MAX_PLY;
    }

    private boolean isWorthCapturing(Move move, Piece attacker, Piece victim) {
        // Allow all promotions to pass; otherwise use MVV-LVA heuristic
        if (move.getPromotion() != null && move.getPromotion().getPieceType() != PieceType.NONE) return true;
        int gain = getPieceValue(victim) - getPieceValue(attacker);
        return gain >= -100;
    }

    private int getPieceValue(Piece piece) {
        if (piece == null || piece == Piece.NONE) return 0;
        return switch (piece.getPieceType()) {
            case PAWN -> 100;
            case KNIGHT -> 320;
            case BISHOP -> 330;
            case ROOK -> 500;
            case QUEEN -> 900;
            case KING -> 20000;
            default -> 0;
        };
    }
}
