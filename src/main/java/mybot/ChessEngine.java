package mybot;

import java.util.*;

public class ChessEngine {
    public Board board;
    public PieceTracker pieceTracker;
    private NNUE       nnue;
    private HCENetwork hceNet;

    // Flat-array TT: two parallel long[] — keys and packed data.
    // Data packing: score(16) | depth(8) | type(2) | unused(22) | move(16)
    //   bits 63-48: score  (short, signed)
    //   bits 47-40: depth  (byte, 0-100)
    //   bits 39-38: type   (0=EXACT, 1=LOWER, 2=UPPER)
    //   bits 37-16: unused
    //   bits 15- 0: bestMove (16 bits, same encoding as Move.of())
    private static final int TT_BITS = 21;               // 2^21 = 2,097,152 entries
    private static final int TT_SIZE = 1 << TT_BITS;
    private static final int TT_MASK = TT_SIZE - 1;
    private static final int TT_EXACT = 0, TT_LOWER = 1, TT_UPPER = 2;
    private final long[] ttKeys = new long[TT_SIZE];
    private final long[] ttData = new long[TT_SIZE];
    private int ttHits, ttMisses;

    private static final int MAX_QUIESCENCE_DEPTH = 20;
    private static final int MAX_PLY           = 100;
    private static final int MIN_GUARANTEED_DEPTH = 4;

    private static final int MATE_SCORE = 30_000;   // fits in short; ±30000 >> max material
    private static final int INF        = 29_500;

    private static final boolean USE_Q  = true;
    private static final boolean USE_TT = true;

    private int movesPlayed = 0;

    private long nodesSearched = 0;
    private static final int NODE_CHECK_INTERVAL = 10_000;
    private volatile boolean searchStopped = false;
    private TimeManager timeManager;

    // int-encoded killer moves; Move.NONE = 0
    private final int[][] killerMoves = new int[MAX_PLY][2];

    // PV tracking
    private final int[][] principalVariation = new int[MAX_PLY][MAX_PLY];
    private final int[]   pvLength           = new int[MAX_PLY];

    // Move-ordering stats
    private int totalBetaCutoffs, firstMoveCutoffs, killerMoveHits, ttMoveHits, totalMovesSearched;
    private final int[] cutoffsByMoveIndex = new int[50];

    public ChessEngine() {
        board = new Board();
        pieceTracker = new PieceTracker();
        pieceTracker.initializeFromBoard(board);
    }

    public void loadNNUE(String path) {
        try {
            nnue = new NNUE();
            nnue.load(path);
        } catch (Exception e) {
            System.err.println("info string NNUE load failed: " + e.getMessage());
            nnue = null;
        }
    }

    public void loadHCENetwork(String path) {
        try {
            hceNet = new HCENetwork();
            hceNet.load(path);
        } catch (Exception e) {
            System.err.println("info string HCENetwork load failed: " + e.getMessage());
            hceNet = null;
        }
    }

    public void printUciId()   { System.out.println("id name MyBot"); System.out.println("id author JavaBotDev"); System.out.println("uciok"); }
    public void printEval()    { System.out.println(evaluate(0)); }
    public void printReadyOk() { System.out.println("readyok"); }

    public boolean isLegalMove(String uci) {
        return MoveGenerator.matchUci(board, uci) != Move.NONE;
    }

    public void newGame() {
        board = new Board();
        pieceTracker.initializeFromBoard(board);
        Arrays.fill(ttKeys, 0L);
        ttHits = ttMisses = 0;
        movesPlayed = 0;
        for (int i = 0; i < MAX_PLY; i++) { killerMoves[i][0] = Move.NONE; killerMoves[i][1] = Move.NONE; }
    }

    public void setTimeControl(long whiteTimeMs, long blackTimeMs, long incMs) {
        // Stored externally in TimeManager at search time; kept for API compat
    }

    public void updateTime(int color, long timeUsedMs) { movesPlayed++; }

    public int getMovesPlayed() { return movesPlayed; }

    // ─── Position loading ─────────────────────────────────────────────────────

    public void parsePosition(String line) {
        String[] tokens = line.split("\\s+");
        int idx = 1;

        if (tokens[idx].equals("startpos")) {
            board = new Board();
            idx++;
        } else if (tokens[idx].equals("fen")) {
            idx++;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6 && idx < tokens.length; i++, idx++)
                sb.append(tokens[idx]).append(' ');
            board = new Board(sb.toString().trim());
        } else {
            System.err.println("Invalid position format.");
            return;
        }

        Arrays.fill(ttKeys, 0L);
        movesPlayed = 0;
        pieceTracker.initializeFromBoard(board);

        if (idx < tokens.length && tokens[idx].equals("moves")) {
            idx++;
            while (idx < tokens.length) {
                String uci = tokens[idx++];
                int move = MoveGenerator.matchUci(board, uci);
                if (move == Move.NONE) {
                    System.err.println("Illegal move in position string: " + uci);
                    continue;
                }
                int movingPiece   = board.pieceAt(Move.from(move));
                int capturedPiece = board.pieceAt(Move.to(move));
                int epBefore      = board.epSquare();
                board.makeMove(move);
                pieceTracker.applyMove(move, movingPiece, capturedPiece, epBefore, board);
                movesPlayed++;
            }
        }
    }

    // ─── Best-move selection ──────────────────────────────────────────────────

    public int selectBestMove(GoCommand goCmd) {
        if (goCmd == null) { goCmd = new GoCommand(); goCmd.infinite = true; }

        timeManager = new TimeManager();
        timeManager.planTimeForMove(goCmd, board.sideToMove(), movesPlayed);

        nodesSearched = 0;
        searchStopped = false;
        totalBetaCutoffs = firstMoveCutoffs = killerMoveHits = ttMoveHits = totalMovesSearched = 0;
        Arrays.fill(cutoffsByMoveIndex, 0);

        Profiler.reset();
        Profiler.start("selectBestMove");

        int[] legalMoves = board.generateLegalMoves();
        if (legalMoves.length == 0) return Move.NONE;

        // Initial score array for root ordering
        int[] rootScores = new int[legalMoves.length];
        for (int i = 0; i < legalMoves.length; i++)
            rootScores[i] = scoreMove(legalMoves[i], 0);
        sortMoves(legalMoves, rootScores);

        int minDepth = 1;
        int maxDepth = goCmd.hasDepthLimit() ? goCmd.maxDepth : 64;

        pieceTracker.disablePhaseTransitions();

        int bestMove  = legalMoves[0];
        int bestScore = -INF;
        int[] bestPV  = new int[0];

        for (int depth = minDepth; depth <= maxDepth; depth++) {
            pvLength[0] = 0;

            int iterBestMove  = Move.NONE;
            int iterBestScore = Integer.MIN_VALUE;

            for (int move : legalMoves) {
                if (searchStopped || (timeManager.isTimeUp() && depth > MIN_GUARANTEED_DEPTH)) {
                    searchStopped = true; break;
                }
                int movingPiece   = board.pieceAt(Move.from(move));
                int capturedPiece = board.pieceAt(Move.to(move));
                int epBefore      = board.epSquare();

                board.makeMove(move);
                pieceTracker.applyMove(move, movingPiece, capturedPiece, epBefore, board);
                int score = -negamax(depth - 1, -INF, INF, 1, true);
                board.unmakeMove(move);
                pieceTracker.undoMove(move);

                if (searchStopped) break;

                if (score > iterBestScore) {
                    iterBestScore = score;
                    iterBestMove  = move;
                    bestPV = buildPV(move, 1);
                }
            }

            if (!searchStopped && iterBestMove != Move.NONE) {
                bestMove  = iterBestMove;
                bestScore = iterBestScore;
                outputUciInfo(depth, bestScore, timeManager.getElapsedMs(), nodesSearched, bestPV);
            } else {
                break;
            }

            if (timeManager.isTimeUp() && depth >= MIN_GUARANTEED_DEPTH) break;
            if (isMateScore(bestScore)) break;
        }

        pieceTracker.enablePhaseTransitions();
        Profiler.stop("selectBestMove");

        System.out.printf("info string TT hits: %d, misses: %d%n", ttHits, ttMisses);
        Profiler.printReport();
        printMoveOrderingStats();

        return bestMove;
    }

    public int selectBestMove() {
        GoCommand cmd = new GoCommand(); cmd.infinite = true;
        return selectBestMove(cmd);
    }

    // ─── Search ───────────────────────────────────────────────────────────────

    private int negamax(int depth, int alpha, int beta, int ply, boolean nullMoveAllowed) {
        Profiler.start("negamax");

        if (alpha <= -MATE_SCORE) alpha = -MATE_SCORE + 1;
        if (beta  >=  MATE_SCORE) beta  =  MATE_SCORE - 1;
        if (alpha >= beta) beta = alpha + 1;

        nodesSearched++;
        if (nodesSearched % NODE_CHECK_INTERVAL == 0 && timeManager != null && timeManager.isTimeUp())
            searchStopped = true;
        if (searchStopped) { Profiler.stop("negamax"); return alpha; }

        long key    = board.zobristKey();
        int  ttIdx  = (int)(key & TT_MASK);
        long ttEntry = USE_TT && ttKeys[ttIdx] == key ? ttData[ttIdx] : 0L;
        int  ttDepth = (int)((ttEntry >> 40) & 0xFF);
        int  ttMove  = (int)(ttEntry & 0xFFFF);
        int  ttScore = (short)(ttEntry >> 48);
        int  ttType  = (int)((ttEntry >> 38) & 0x3);
        boolean ttHit = USE_TT && ttKeys[ttIdx] == key && ttDepth >= depth;

        if (ttHit) {
            ttHits++;
            if      (ttType == TT_EXACT) { Profiler.stop("negamax"); return ttScore; }
            else if (ttType == TT_LOWER) alpha = Math.max(alpha, ttScore);
            else                         beta  = Math.min(beta,  ttScore);
            if (alpha >= beta) { Profiler.stop("negamax"); return ttScore; }
        } else { if (USE_TT) ttMisses++; }

        if (board.isDraw())  { pvLength[ply] = 0; Profiler.stop("negamax"); return 0; }
        if (board.isMated()) { pvLength[ply] = 0; Profiler.stop("negamax"); return -MATE_SCORE + ply; }
        if (depth == 0) { pvLength[ply] = 0; Profiler.stop("negamax"); return USE_Q ? quiescence(alpha, beta, ply) : evaluate(ply); }

        pvLength[ply] = 0;

        // Null-move pruning
        if (nullMoveAllowed && depth >= 3 && !board.inCheck()
                && Long.bitCount(board.colorBB(board.sideToMove())
                    & ~board.pieceBB(board.sideToMove(), Piece.PAWN)
                    & ~board.pieceBB(board.sideToMove(), Piece.KING)) > 0) {
            int R = 2 + depth / 6;
            if (board.makeNullMove()) {
                int nullScore = -negamax(depth - 1 - R, -beta, -beta + 1, ply + 1, false);
                board.unmakeNullMove();
                if (nullScore >= beta) { Profiler.stop("negamax"); return beta; }
            }
        }

        int alphaOrig = alpha, betaOrig = beta;
        int bestScore = Integer.MIN_VALUE;
        int bestMove  = Move.NONE;

        int[] moves = board.generateLegalMoves();
        if (moves.length == 0) { Profiler.stop("negamax"); return evaluate(ply); }

        int[] scores = new int[moves.length];
        for (int i = 0; i < moves.length; i++) scores[i] = scoreMove(moves[i], ply);
        sortMoves(moves, scores);

        for (int mi = 0; mi < moves.length; mi++) {
            int move = moves[mi];
            totalMovesSearched++;

            int movingPiece   = board.pieceAt(Move.from(move));
            int capturedPiece = board.pieceAt(Move.to(move));
            int epBefore      = board.epSquare();

            board.makeMove(move);
            Profiler.start("applyMove");
            pieceTracker.applyMove(move, movingPiece, capturedPiece, epBefore, board);
            Profiler.stop("applyMove");

            int score = -negamax(depth - 1, -beta, -alpha, ply + 1, true);

            board.unmakeMove(move);
            Profiler.start("undoMove");
            pieceTracker.undoMove(move);
            Profiler.stop("undoMove");

            if (score > bestScore) {
                bestScore = score;
                bestMove  = move;
                principalVariation[ply][0] = move;
                System.arraycopy(principalVariation[ply + 1], 0, principalVariation[ply], 1, pvLength[ply + 1]);
                pvLength[ply] = pvLength[ply + 1] + 1;
            }
            if (score > alpha) alpha = score;
            if (alpha >= beta) {
                totalBetaCutoffs++;
                if (mi == 0) firstMoveCutoffs++;
                if (mi < cutoffsByMoveIndex.length) cutoffsByMoveIndex[mi]++;
                if (ttMove != Move.NONE && move == ttMove) ttMoveHits++;
                else if (ply < MAX_PLY && (move == killerMoves[ply][0] || move == killerMoves[ply][1])) killerMoveHits++;
                // Store killer if quiet
                if (capturedPiece == Piece.EMPTY && !Move.isPromotion(move) && ply < MAX_PLY) {
                    killerMoves[ply][1] = killerMoves[ply][0];
                    killerMoves[ply][0] = move;
                }
                break;
            }
        }

        if (USE_TT && bestMove != Move.NONE) {
            int type = bestScore <= alphaOrig ? TT_UPPER
                     : bestScore >= betaOrig  ? TT_LOWER
                     :                          TT_EXACT;
            int s    = Math.max(-MATE_SCORE + 1, Math.min(bestScore, MATE_SCORE - 1));
            int storedDepth = (int)((ttData[ttIdx] >> 40) & 0xFF);
            if (ttKeys[ttIdx] != key || depth >= storedDepth) {
                ttKeys[ttIdx] = key;
                ttData[ttIdx] = ((long)(short)s    << 48)
                              | ((long)(depth & 0xFF) << 40)
                              | ((long)(type  & 0x3)  << 38)
                              | (bestMove & 0xFFFFL);
            }
        }

        Profiler.stop("negamax");
        return bestScore;
    }

    // ─── Quiescence ───────────────────────────────────────────────────────────

    private int quiescence(int alpha, int beta, int ply) {
        return quiescence(alpha, beta, ply, 0, new HashSet<>());
    }

    private int quiescence(int alpha, int beta, int ply, int qDepth, Set<Long> visited) {
        Profiler.start("quiescence");
        nodesSearched++;
        if (searchStopped) { Profiler.stop("quiescence"); return alpha; }
        if (qDepth > MAX_QUIESCENCE_DEPTH) { Profiler.stop("quiescence"); return evaluate(ply); }

        long key = board.zobristKey();
        if (!visited.add(key)) { Profiler.stop("quiescence"); return evaluate(ply); }

        int standPat = evaluate(ply);
        if (standPat >= beta) { Profiler.stop("quiescence"); return beta; }
        if (standPat > alpha) alpha = standPat;

        int[] allMoves = board.generateCaptures();
        int[] scores   = new int[allMoves.length];
        for (int i = 0; i < allMoves.length; i++) scores[i] = scoreMove(allMoves[i], -1);
        sortMoves(allMoves, scores);

        for (int move : allMoves) {
            if (Move.isCapture(move) && see(move) < 0) continue;

            int movingPiece   = board.pieceAt(Move.from(move));
            int capturedPiece = board.pieceAt(Move.to(move));
            int epBefore      = board.epSquare();

            board.makeMove(move);
            Profiler.start("applyMove");
            pieceTracker.applyMove(move, movingPiece, capturedPiece, epBefore, board);
            Profiler.stop("applyMove");

            int score = -quiescence(-beta, -alpha, ply, qDepth + 1, visited);

            board.unmakeMove(move);
            Profiler.start("undoMove");
            pieceTracker.undoMove(move);
            Profiler.stop("undoMove");

            if (score >= beta) { Profiler.stop("quiescence"); return beta; }
            if (score > alpha) alpha = score;
        }
        Profiler.stop("quiescence");
        return alpha;
    }

    // ─── Evaluation ───────────────────────────────────────────────────────────

    private int evaluate(int ply) {
        Profiler.start("evaluate");
        int score;
        if (nnue != null && nnue.isReady()) {
            score = nnue.evaluate(board);
        } else if (hceNet != null && hceNet.isReady()) {
            score = hceNet.evaluate(board);
            if (board.sideToMove() != Piece.WHITE) score = -score;
        } else {
            score = pieceTracker.getMaterialScore();
            if (board.sideToMove() != Piece.WHITE) score = -score;
        }
        Profiler.stop("evaluate");
        return score;
    }

    // ─── Static Exchange Evaluation ───────────────────────────────────────────

    private int see(int move) {
        int toSq   = Move.to(move);
        int fromSq = Move.from(move);
        // En passant: victim is the pawn behind the destination square
        if (Move.isEnPassant(move)) return pieceValue(Piece.PAWN);
        int victim = board.pieceAt(toSq);
        if (victim == Piece.EMPTY) return 0;

        long occ = board.allPieces();
        int[] gain = new int[32];
        int d = 0;
        gain[0] = pieceValue(Piece.type(victim));

        int attackerColor = Piece.color(board.pieceAt(fromSq));
        int attackerType  = Piece.type(board.pieceAt(fromSq));
        occ ^= Bitboard.bit(fromSq);

        // Add X-ray after first capture
        occ = addXray(toSq, fromSq, attackerType, occ);

        while (true) {
            d++;
            attackerColor ^= 1;
            gain[d] = pieceValue(attackerType) - gain[d - 1];
            if (Math.max(-gain[d - 1], gain[d]) < 0) break;

            long attackers = board.allAttackersTo(toSq, occ) & occ & board.colorBB(attackerColor);
            if (attackers == 0) break;

            // Find LVA
            int attSq = -1;
            attackerType = -1;
            for (int type = Piece.PAWN; type <= Piece.KING; type++) {
                long byType = attackers & board.pieceBB(attackerColor, type);
                if (byType != 0) {
                    attSq = Bitboard.lsb(byType);
                    attackerType = type;
                    break;
                }
            }
            if (attSq < 0) break;
            occ ^= Bitboard.bit(attSq);
            occ = addXray(toSq, attSq, attackerType, occ);
        }

        while (--d > 0) gain[d - 1] = Math.max(-gain[d], gain[d - 1]);
        return gain[0];
    }

    private long addXray(int toSq, int fromSq, int type, long occ) {
        // Sliders are handled by recomputing allAttackersTo with the updated occ
        return occ;
    }

    private int pieceValue(int type) {
        return switch (type) {
            case Piece.PAWN   -> 100;
            case Piece.KNIGHT -> 320;
            case Piece.BISHOP -> 330;
            case Piece.ROOK   -> 500;
            case Piece.QUEEN  -> 900;
            case Piece.KING   -> 20_000;
            default           -> 0;
        };
    }

    // ─── Move scoring for ordering ────────────────────────────────────────────

    private int scoreMove(int move, int ply) {
        Profiler.start("scoreMove");
        int fromSq = Move.from(move);
        int toSq   = Move.to(move);
        int victim = board.pieceAt(toSq);
        int attacker = board.pieceAt(fromSq);

        int ttMove = Move.NONE;
        if (USE_TT) {
            long k = board.zobristKey();
            int  i = (int)(k & TT_MASK);
            if (ttKeys[i] == k) ttMove = (int)(ttData[i] & 0xFFFF);
        }

        int score = 0;
        boolean isCapture   = Move.isCapture(move);
        boolean isPromotion = Move.isPromotion(move);
        boolean isTTMove    = (ttMove != Move.NONE && move == ttMove);

        if (isTTMove) {
            score = 1_000_000_000;
            if (isPromotion) score += pieceValue(Move.promoType(move));
            else if (isCapture) score += pieceValue(Piece.type(victim)) * 10 - pieceValue(Piece.type(attacker));
        } else if (isPromotion) {
            score = 100_000_000 + pieceValue(Move.promoType(move));
        } else if (isCapture) {
            int s = see(move);
            score = s >= 0 ? 10_000_000 + s : s;
        } else if (ply >= 0 && ply < MAX_PLY) {
            if (move == killerMoves[ply][0]) score = 9_000;
            else if (move == killerMoves[ply][1]) score = 8_000;
        }
        Profiler.stop("scoreMove");
        return score;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static void sortMoves(int[] moves, int[] scores) {
        // Insertion sort descending by score
        for (int i = 1; i < moves.length; i++) {
            int m = moves[i], s = scores[i];
            int j = i - 1;
            while (j >= 0 && scores[j] < s) {
                moves[j + 1] = moves[j];
                scores[j + 1] = scores[j];
                j--;
            }
            moves[j + 1] = m;
            scores[j + 1] = s;
        }
    }

    private int[] buildPV(int rootMove, int startPly) {
        int[] pv = new int[pvLength[startPly] + 1];
        pv[0] = rootMove;
        System.arraycopy(principalVariation[startPly], 0, pv, 1, pvLength[startPly]);
        return pv;
    }

    private boolean givesCheck(int move) {
        board.makeMove(move);
        boolean check = board.inCheck();
        board.unmakeMove(move);
        return check;
    }

    private boolean isMateScore(int score) {
        return score > MATE_SCORE - MAX_PLY || score < -MATE_SCORE + MAX_PLY;
    }

    private void outputUciInfo(int depth, int score, long timeMs, long nodes, int[] pv) {
        StringBuilder sb = new StringBuilder("info depth ").append(depth);
        if (isMateScore(score)) {
            int dist = score > 0 ? (MATE_SCORE - score) : (-MATE_SCORE - score);
            sb.append(" score mate ").append(score > 0 ? (dist + 1) / 2 : -(dist + 1) / 2);
        } else {
            sb.append(" score cp ").append(score);
        }
        sb.append(" time ").append(timeMs).append(" nodes ").append(nodes);
        if (timeMs > 0) sb.append(" nps ").append(nodes * 1000 / timeMs);
        if (pv != null && pv.length > 0) {
            sb.append(" pv");
            for (int m : pv) if (m != Move.NONE) sb.append(' ').append(Move.toUci(m));
        }
        System.out.println(sb);
    }

    private void printMoveOrderingStats() {
        if (totalBetaCutoffs == 0) return;
        System.out.printf("%n========== MOVE ORDERING ===========%n");
        System.out.printf("Beta cutoffs: %d, first-move: %d (%.1f%%)%n",
            totalBetaCutoffs, firstMoveCutoffs, 100.0 * firstMoveCutoffs / totalBetaCutoffs);
        System.out.printf("TT cutoffs: %d, killer cutoffs: %d%n", ttMoveHits, killerMoveHits);
        System.out.println("===================================\n");
    }

    // ─── Manual mode ─────────────────────────────────────────────────────────

    public void runManualMode(Scanner input) {
        board = new Board();
        pieceTracker.initializeFromBoard(board);
        Arrays.fill(ttKeys, 0L);

        System.out.print("Pick your side (white/black): ");
        boolean userIsWhite = input.nextLine().trim().equalsIgnoreCase("white");

        while (!board.isMated() && !board.isDraw()) {
            int stm = board.sideToMove();
            boolean userTurn = (userIsWhite && stm == Piece.WHITE) || (!userIsWhite && stm == Piece.BLACK);

            if (userTurn) {
                System.out.print("Your move: ");
                String uci = input.nextLine().trim();
                int move = MoveGenerator.matchUci(board, uci);
                if (move == Move.NONE) { System.out.println("Illegal move."); continue; }
                int movingPiece   = board.pieceAt(Move.from(move));
                int capturedPiece = board.pieceAt(Move.to(move));
                int epBefore      = board.epSquare();
                board.makeMove(move);
                pieceTracker.applyMove(move, movingPiece, capturedPiece, epBefore, board);
            } else {
                int move = selectBestMove();
                if (move == Move.NONE) { System.out.println("No legal moves for bot."); break; }
                int movingPiece   = board.pieceAt(Move.from(move));
                int capturedPiece = board.pieceAt(Move.to(move));
                int epBefore      = board.epSquare();
                board.makeMove(move);
                pieceTracker.applyMove(move, movingPiece, capturedPiece, epBefore, board);
                System.out.println("Computer plays: " + Move.toUci(move));
            }
        }
        System.out.println(board.isMated() ? "Checkmate!" : "Draw.");
    }
}
