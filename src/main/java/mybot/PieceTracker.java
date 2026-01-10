package mybot;

import com.github.bhlangonijr.chesslib.*;
import com.github.bhlangonijr.chesslib.move.Move;
import com.github.bhlangonijr.chesslib.File;

import java.util.*;

public class PieceTracker {
    private final Map<Square, TrackedPiece> pieceMap = new HashMap<>(32);
    private final Deque<MoveInfo> moveHistory = new ArrayDeque<>();

    private int materialScore = 0;                 // signed (white +, black -)
    private final Deque<Integer> deltaHistory = new ArrayDeque<>(); // net eval delta per move

    private GamePhase.Phase currentPhase = GamePhase.Phase.OPENING;
    private final Deque<GamePhase.Phase> phaseHistory = new ArrayDeque<>();
    private boolean allowPhaseTransitions = true;  // Disable during search to prevent instability

    // -------------------------- Queries used by ChessEngine --------------------------
    public boolean isEnPassant(Move move, Board board) {
        Piece movingPiece = board.getPiece(move.getFrom());
        if (movingPiece.getPieceType() != PieceType.PAWN) return false;           // only pawns
        if (board.getPiece(move.getTo()) != Piece.NONE) return false;             // dest must be empty

        int fileDiff = Math.abs(move.getTo().getFile().ordinal() - move.getFrom().getFile().ordinal());
        int rankDiff = move.getTo().getRank().ordinal() - move.getFrom().getRank().ordinal();
        if (fileDiff != 1) return false;                                         // diagonal only
        if (movingPiece.getPieceSide() == Side.WHITE && rankDiff != 1) return false;
        if (movingPiece.getPieceSide() == Side.BLACK && rankDiff != -1) return false;

        // Behind the target square must be an enemy pawn
        int tRank = move.getTo().getRank().ordinal();
        Rank capturedPawnRank = movingPiece.getPieceSide() == Side.WHITE
                ? Rank.values()[tRank - 1]
                : Rank.values()[tRank + 1];
        Square capturedSq = Square.encode(capturedPawnRank, move.getTo().getFile());
        Piece captured = board.getPiece(capturedSq);
        return captured.getPieceType() == PieceType.PAWN && captured.getPieceSide() != movingPiece.getPieceSide();
    }

    public boolean isPromotion(Move move, Board board) {
        return move.getPromotion() != Piece.NONE;
    }

    public boolean isCastling(Move move, Board board) {
        return board.getContext().isCastleMove(move);
    }

    public boolean isCapture(Move move, Board board) {
        return board.getPiece(move.getTo()) != Piece.NONE || isEnPassant(move, board);
    }

    // -------------------------- Lifecycle --------------------------
    public void initializeFromBoard(Board board) {
        pieceMap.clear();
        moveHistory.clear();
        deltaHistory.clear();
        phaseHistory.clear();
        materialScore = 0;

        // Determine initial phase
        int initialMaterial = 0;
        for (Square sq : Square.values()) {
            Piece p = board.getPiece(sq);
            if (p != Piece.NONE) {
                initialMaterial += TrackedPiece.getBaseValue(p.getPieceType());
            }
        }
        currentPhase = GamePhase.detectPhase(initialMaterial);

        // Initialize pieces with phase-aware PST
        for (Square sq : Square.values()) {
            Piece p = board.getPiece(sq);
            if (p != Piece.NONE) {
                PieceType type = p.getPieceType();
                Side side = p.getPieceSide();
                int base = TrackedPiece.getBaseValue(type);
                int pst = PieceSquareTables.getBonus(type, side, sq, currentPhase);
                pieceMap.put(sq, new TrackedPiece(type, side, sq, base, pst));
            }
        }
        for (TrackedPiece tp : pieceMap.values()) materialScore += tp.getSignedValue();
    }

    // -------------------------- Apply / Undo (material tracked by deltas) --------------------------
    public void applyMove(Move move, Piece movingPiece, Piece capturedPiece, Board board) {
        int delta = 0; // how materialScore changes this ply
        Square from = move.getFrom();
        Square to   = move.getTo();

        boolean ep = isEnPassant(move, board);
        boolean castle = isCastling(move, board);
        boolean promo = isPromotion(move, board);

        Square epSq = null; // Store EP capture square if needed

        // --- Handle captures (incl. en passant) ---
        if (ep) {
            epSq = getEnPassantCaptureSquare(move, movingPiece);
            Piece epPawn = board.getPiece(epSq); // should be enemy pawn
            if (epPawn != Piece.NONE) {
                TrackedPiece epTracked = getTrackedPieceAt(epSq);
                int signedValue = epTracked != null ? epTracked.getSignedValue() : 0;
                removeTrackedPieceAt(epSq);
                delta -= signedValue; // removing opponent piece subtracts its signed value
            }
        } else if (capturedPiece != null && capturedPiece != Piece.NONE) {
            TrackedPiece capTracked = getTrackedPieceAt(to);
            int signedValue = capTracked != null ? capTracked.getSignedValue() : 0;
            removeTrackedPieceAt(to);
            delta -= signedValue;
        }

        // --- Move the piece (and handle promotion) ---
        TrackedPiece tracked = getTrackedPieceAt(from);
        if (tracked != null) {
            int before = tracked.getSignedValue();
            pieceMap.remove(from);  // Remove from old position
            tracked.updatePosition(to, currentPhase);
            if (promo) {
                PieceType newType = move.getPromotion().getPieceType();
                tracked.promoteTo(newType, currentPhase); // Updates type, baseValue, and PST
            }
            pieceMap.put(to, tracked);  // Add to new position
            int after = tracked.getSignedValue();
            delta += (after - before); // Captures move PST change + promotion value increase
        }

        // --- Castling rook move (with PST delta) ---
        if (castle) {
            Side side = movingPiece.getPieceSide();
            boolean kingSide = to.getFile() == File.FILE_G;
            Square rookFrom, rookTo;
            if (side == Side.WHITE) {
                rookFrom = kingSide ? Square.H1 : Square.A1;
                rookTo   = kingSide ? Square.F1 : Square.D1;
            } else {
                rookFrom = kingSide ? Square.H8 : Square.A8;
                rookTo   = kingSide ? Square.F8 : Square.D8;
            }
            TrackedPiece rook = getTrackedPieceAt(rookFrom);
            if (rook != null) {
                int rBefore = rook.getSignedValue();
                pieceMap.remove(rookFrom);
                rook.updatePosition(rookTo, currentPhase);
                pieceMap.put(rookTo, rook);
                int rAfter  = rook.getSignedValue();
                delta += (rAfter - rBefore);
            }
        }

        // Record for undo
        PieceType prePromo = promo ? movingPiece.getPieceType() : null; // usually PAWN
        moveHistory.addLast(new MoveInfo(move, movingPiece, capturedPiece, prePromo, castle, ep, epSq));

        materialScore += delta;
        deltaHistory.addLast(delta);

        // Check for phase transition AFTER applying move
        GamePhase.Phase phaseBefore = currentPhase;
        phaseHistory.addLast(phaseBefore);
        updatePhase(board);
    }

    public void undoMove(Move move, Piece movingPiece, Piece capturedPiece, Board board) {
        MoveInfo last = moveHistory.pollLast();
        if (last == null || !last.move.equals(move)) {
            System.err.println("undoMove: move mismatch or empty history");
            return;
        }

        Square from = move.getFrom();
        Square to   = move.getTo();

        // 1) Move the (possibly promoted) piece back
        TrackedPiece tracked = getTrackedPieceAt(to);
        if (tracked != null) {
            pieceMap.remove(to);
            tracked.updatePosition(from, currentPhase);
            if (last.promotionBefore != null) {
                // We promoted on the way forward; restore original type (usually pawn)
                tracked.demoteTo(last.promotionBefore, currentPhase);
            }
            pieceMap.put(from, tracked);
        }

        // 2) Restore captured piece (EP or normal)
        if (last.capturedPiece != null && last.capturedPiece != Piece.NONE) {
            Square restoreSq = last.wasEnPassant ? last.epCaptureSquare : to;
            PieceType type = last.capturedPiece.getPieceType();
            Side side      = last.capturedPiece.getPieceSide();
            int base = TrackedPiece.getBaseValue(type);
            int pst  = PieceSquareTables.getBonus(type, side, restoreSq, currentPhase);
            pieceMap.put(restoreSq, new TrackedPiece(type, side, restoreSq, base, pst));
        }

        // 3) If castling, move rook back
        if (last.wasCastling) {
            Side side = last.movingPiece.getPieceSide();
            boolean kingSide = to.getFile() == File.FILE_G;
            Square rookFrom, rookTo; // from=castled square, to=original rook square
            if (side == Side.WHITE) {
                rookFrom = kingSide ? Square.F1 : Square.D1;
                rookTo   = kingSide ? Square.H1 : Square.A1;
            } else {
                rookFrom = kingSide ? Square.F8 : Square.D8;
                rookTo   = kingSide ? Square.H8 : Square.A8;
            }
            TrackedPiece rook = getTrackedPieceAt(rookFrom);
            if (rook != null) {
                pieceMap.remove(rookFrom);
                rook.updatePosition(rookTo, currentPhase);
                pieceMap.put(rookTo, rook);
            }
        }

        // 4) Revert material change recorded on apply
        if (!deltaHistory.isEmpty()) materialScore -= deltaHistory.pollLast();

        // 5) Restore previous phase
        if (!phaseHistory.isEmpty()) {
            GamePhase.Phase oldPhase = phaseHistory.pollLast();
            if (oldPhase != currentPhase) {
                currentPhase = oldPhase;
                updatePhaseInternal(currentPhase);
            }
        }
    }

    // -------------------------- Helpers --------------------------
    private void removeTrackedPieceAt(Square square) {
        pieceMap.remove(square);
    }

    private TrackedPiece getTrackedPieceAt(Square square) {
        return pieceMap.get(square);
    }

    private Square getEnPassantCaptureSquare(Move move, Piece movingPiece) {
        int offset = (movingPiece.getPieceSide() == Side.WHITE) ? -8 : 8;
        return Square.squareAt(move.getTo().ordinal() + offset);
    }

    // -------------------------- API used by evaluation --------------------------
    public int getMaterialScore(Board board) { return materialScore; }
    public Collection<TrackedPiece> getTrackedPieces() { return pieceMap.values(); }

    public void printMaterialDebug() {
        System.out.println("=== Material Debug ===");
        int whiteTotal = 0, blackTotal = 0;
        for (TrackedPiece tp : pieceMap.values()) {
            int signed = tp.getSignedValue();
            System.out.printf("  %s %s at %s: base=%d pst=%d signed=%d%n",
                tp.side, tp.type, tp.position, tp.baseValue, tp.pstBonus, signed);
            if (tp.side == Side.WHITE) whiteTotal += tp.getTotalValue();
            else blackTotal += tp.getTotalValue();
        }
        System.out.printf("White total: %d, Black total: %d, materialScore: %d%n",
            whiteTotal, blackTotal, materialScore);
    }

    /**
     * Returns absolute total material (sum of all piece values, unsigned).
     * Used for phase detection.
     */
    public int getAbsoluteMaterial() {
        int total = 0;
        for (TrackedPiece tp : pieceMap.values()) {
            total += tp.baseValue;
        }
        return total;
    }

    /**
     * Returns current game phase.
     */
    public GamePhase.Phase getCurrentPhase() {
        return currentPhase;
    }

    /**
     * Enable phase transitions (call before search).
     */
    public void enablePhaseTransitions() {
        allowPhaseTransitions = true;
    }

    /**
     * Disable phase transitions (call at start of search).
     */
    public void disablePhaseTransitions() {
        allowPhaseTransitions = false;
    }

    /**
     * Detects and updates game phase. If phase changed, recalculates all PST bonuses.
     * Returns true if phase transition occurred.
     * Only transitions if allowPhaseTransitions is true.
     */
    private boolean updatePhase(Board board) {
        if (!allowPhaseTransitions) {
            return false;  // Phase transitions disabled during search
        }

        GamePhase.Phase newPhase = GamePhase.detectPhase(getAbsoluteMaterial());

        if (newPhase != currentPhase) {
            System.out.printf("Phase transition: %s -> %s (material=%d)%n",
                             currentPhase, newPhase, getAbsoluteMaterial());

            // Recalculate all PST bonuses for new phase
            materialScore = 0;
            for (TrackedPiece tp : pieceMap.values()) {
                tp.pstBonus = PieceSquareTables.getBonus(tp.type, tp.side, tp.position, newPhase);
                materialScore += tp.getSignedValue();
            }

            currentPhase = newPhase;
            return true;
        }
        return false;
    }

    /**
     * Internal method to set phase and recalculate PST bonuses.
     * Used during undo operations.
     */
    private void updatePhaseInternal(GamePhase.Phase newPhase) {
        materialScore = 0;
        for (TrackedPiece tp : pieceMap.values()) {
            tp.pstBonus = PieceSquareTables.getBonus(tp.type, tp.side, tp.position, newPhase);
            materialScore += tp.getSignedValue();
        }
    }

    public void reset(Board board) {
        initializeFromBoard(board);
    }

    public MoveInfo getLastMove() { return moveHistory.peekLast(); }
    public Deque<MoveInfo> getMoveHistory() { return moveHistory; }

    // -------------------------- Validation (for debugging) --------------------------
    public boolean validateMaterialScore() {
        int computed = 0;
        for (TrackedPiece tp : pieceMap.values()) {
            computed += tp.getSignedValue();
        }
        return computed == materialScore;
    }
}
