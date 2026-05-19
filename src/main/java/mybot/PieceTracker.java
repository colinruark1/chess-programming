package mybot;

import java.util.*;

public class PieceTracker {
    // Indexed by square (0-63); null means empty
    private final TrackedPiece[] pieceMap = new TrackedPiece[64];
    private final Deque<MoveInfo> moveHistory = new ArrayDeque<>();

    private int materialScore = 0;
    private final Deque<Integer> deltaHistory = new ArrayDeque<>();

    private GamePhase.Phase currentPhase = GamePhase.Phase.OPENING;
    private final Deque<GamePhase.Phase> phaseHistory = new ArrayDeque<>();
    private boolean allowPhaseTransitions = true;

    // -------------------------- Lifecycle --------------------------

    public void initializeFromBoard(Board board) {
        Arrays.fill(pieceMap, null);
        moveHistory.clear();
        deltaHistory.clear();
        phaseHistory.clear();
        materialScore = 0;

        int initialMaterial = 0;
        for (int sq = 0; sq < 64; sq++) {
            int p = board.pieceAt(sq);
            if (p != Piece.EMPTY) initialMaterial += TrackedPiece.getBaseValue(Piece.type(p));
        }
        currentPhase = GamePhase.detectPhase(initialMaterial);

        for (int sq = 0; sq < 64; sq++) {
            int p = board.pieceAt(sq);
            if (p != Piece.EMPTY) {
                int type  = Piece.type(p);
                int color = Piece.color(p);
                int base  = TrackedPiece.getBaseValue(type);
                int pst   = PieceSquareTables.getBonus(type, color, sq, currentPhase);
                pieceMap[sq] = new TrackedPiece(type, color, sq, base, pst);
            }
        }
        for (TrackedPiece tp : pieceMap) if (tp != null) materialScore += tp.getSignedValue();
    }

    // -------------------------- Apply / Undo --------------------------

    /**
     * @param move         int-encoded move
     * @param movingPiece  piece code of the moving piece (before move)
     * @param capturedPiece piece code of captured piece, or Piece.EMPTY
     * @param epBefore     board.epSquare() captured BEFORE board.makeMove() was called
     * @param board        board state AFTER makeMove (used only for phase detection)
     */
    public void applyMove(int move, int movingPiece, int capturedPiece, int epBefore, Board board) {
        int delta = 0;
        int from  = Move.from(move);
        int to    = Move.to(move);
        int flags = Move.flags(move);

        boolean ep     = flags == Move.EN_PASSANT;
        boolean castle = Move.isCastle(move);
        boolean promo  = Move.isPromotion(move);
        int movingColor = Piece.color(movingPiece);

        int epCapSq = Sq.NONE;

        // --- Captures ---
        if (ep) {
            // EP: captured pawn is behind the destination square
            epCapSq = movingColor == Piece.WHITE ? epBefore - 8 : epBefore + 8;
            TrackedPiece epTracked = pieceMap[epCapSq];
            if (epTracked != null) {
                delta -= epTracked.getSignedValue();
                pieceMap[epCapSq] = null;
            }
        } else if (capturedPiece != Piece.EMPTY) {
            TrackedPiece capTracked = pieceMap[to];
            if (capTracked != null) {
                delta -= capTracked.getSignedValue();
                pieceMap[to] = null;
            }
        }

        // --- Move the piece ---
        TrackedPiece tracked = pieceMap[from];
        if (tracked != null) {
            int before = tracked.getSignedValue();
            pieceMap[from] = null;
            tracked.updatePosition(to, currentPhase);
            if (promo) tracked.promoteTo(Move.promoType(move), currentPhase);
            pieceMap[to] = tracked;
            delta += tracked.getSignedValue() - before;
        }

        // --- Castling rook ---
        if (castle) {
            boolean kingSide = flags == Move.CASTLE_K;
            int rookFrom, rookTo;
            if (movingColor == Piece.WHITE) {
                rookFrom = kingSide ? Sq.H1 : Sq.A1;
                rookTo   = kingSide ? Sq.F1 : Sq.D1;
            } else {
                rookFrom = kingSide ? Sq.H8 : Sq.A8;
                rookTo   = kingSide ? Sq.F8 : Sq.D8;
            }
            TrackedPiece rook = pieceMap[rookFrom];
            if (rook != null) {
                int rBefore = rook.getSignedValue();
                pieceMap[rookFrom] = null;
                rook.updatePosition(rookTo, currentPhase);
                pieceMap[rookTo] = rook;
                delta += rook.getSignedValue() - rBefore;
            }
        }

        int prePromoType = promo ? Piece.type(movingPiece) : Piece.NONE;
        moveHistory.addLast(new MoveInfo(move, movingPiece, capturedPiece, prePromoType, castle, ep, epCapSq));

        materialScore += delta;
        deltaHistory.addLast(delta);

        phaseHistory.addLast(currentPhase);
        updatePhase(board);
    }

    public void undoMove(int move) {
        MoveInfo last = moveHistory.pollLast();
        if (last == null) {
            System.err.println("undoMove: empty history");
            return;
        }

        int from  = Move.from(move);
        int to    = Move.to(move);

        // 1) Move piece back
        TrackedPiece tracked = pieceMap[to];
        if (tracked != null) {
            pieceMap[to] = null;
            tracked.updatePosition(from, currentPhase);
            if (last.promotionBefore != Piece.NONE) tracked.demoteTo(last.promotionBefore, currentPhase);
            pieceMap[from] = tracked;
        }

        // 2) Restore captured piece
        if (last.capturedPiece != Piece.EMPTY) {
            int restoreSq = last.wasEnPassant ? last.epCaptureSquare : to;
            int type  = Piece.type(last.capturedPiece);
            int color = Piece.color(last.capturedPiece);
            int base  = TrackedPiece.getBaseValue(type);
            int pst   = PieceSquareTables.getBonus(type, color, restoreSq, currentPhase);
            pieceMap[restoreSq] = new TrackedPiece(type, color, restoreSq, base, pst);
        }

        // 3) Castle rook back
        if (last.wasCastling) {
            int movingColor = Piece.color(last.movingPiece);
            boolean kingSide = Move.flags(move) == Move.CASTLE_K;
            int rookFrom, rookTo;
            if (movingColor == Piece.WHITE) {
                rookFrom = kingSide ? Sq.F1 : Sq.D1;
                rookTo   = kingSide ? Sq.H1 : Sq.A1;
            } else {
                rookFrom = kingSide ? Sq.F8 : Sq.D8;
                rookTo   = kingSide ? Sq.H8 : Sq.A8;
            }
            TrackedPiece rook = pieceMap[rookFrom];
            if (rook != null) {
                pieceMap[rookFrom] = null;
                rook.updatePosition(rookTo, currentPhase);
                pieceMap[rookTo] = rook;
            }
        }

        // 4) Revert material
        if (!deltaHistory.isEmpty()) materialScore -= deltaHistory.pollLast();

        // 5) Restore phase
        if (!phaseHistory.isEmpty()) {
            GamePhase.Phase oldPhase = phaseHistory.pollLast();
            if (oldPhase != currentPhase) {
                currentPhase = oldPhase;
                recalcAllPst(currentPhase);
            }
        }
    }

    // -------------------------- API used by evaluation --------------------------

    public int getMaterialScore() { return materialScore; }

    public Collection<TrackedPiece> getTrackedPieces() {
        List<TrackedPiece> list = new ArrayList<>(32);
        for (TrackedPiece tp : pieceMap) if (tp != null) list.add(tp);
        return list;
    }

    public int getAbsoluteMaterial() {
        int total = 0;
        for (TrackedPiece tp : pieceMap) if (tp != null) total += tp.baseValue;
        return total;
    }

    public GamePhase.Phase getCurrentPhase() { return currentPhase; }

    public void enablePhaseTransitions()  { allowPhaseTransitions = true; }
    public void disablePhaseTransitions() { allowPhaseTransitions = false; }

    public MoveInfo getLastMove() { return moveHistory.peekLast(); }
    public Deque<MoveInfo> getMoveHistory() { return moveHistory; }

    public void reset(Board board) { initializeFromBoard(board); }

    public boolean validateMaterialScore() {
        int computed = 0;
        for (TrackedPiece tp : pieceMap) if (tp != null) computed += tp.getSignedValue();
        return computed == materialScore;
    }

    public void printMaterialDebug() {
        System.out.println("=== Material Debug ===");
        int wTotal = 0, bTotal = 0;
        for (TrackedPiece tp : pieceMap) {
            if (tp == null) continue;
            int signed = tp.getSignedValue();
            System.out.printf("  %s %s at %s: base=%d pst=%d signed=%d%n",
                tp.color == Piece.WHITE ? "W" : "B",
                switch(tp.type){case Piece.PAWN->"P";case Piece.KNIGHT->"N";case Piece.BISHOP->"B";case Piece.ROOK->"R";case Piece.QUEEN->"Q";default->"K";}, Sq.name(tp.position),
                tp.baseValue, tp.pstBonus, signed);
            if (tp.color == Piece.WHITE) wTotal += tp.getTotalValue();
            else bTotal += tp.getTotalValue();
        }
        System.out.printf("White total: %d, Black total: %d, materialScore: %d%n",
            wTotal, bTotal, materialScore);
    }

    // -------------------------- Private helpers --------------------------

    private boolean updatePhase(Board board) {
        if (!allowPhaseTransitions) return false;
        GamePhase.Phase newPhase = GamePhase.detectPhase(getAbsoluteMaterial());
        if (newPhase != currentPhase) {
            recalcAllPst(newPhase);
            currentPhase = newPhase;
            return true;
        }
        return false;
    }

    private void recalcAllPst(GamePhase.Phase phase) {
        materialScore = 0;
        for (TrackedPiece tp : pieceMap) {
            if (tp == null) continue;
            tp.pstBonus = PieceSquareTables.getBonus(tp.type, tp.color, tp.position, phase);
            materialScore += tp.getSignedValue();
        }
    }
}
