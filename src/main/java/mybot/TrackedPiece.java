package mybot;

import com.github.bhlangonijr.chesslib.*;

public class TrackedPiece {
    public PieceType type;
    public final Side side;
    public Square position;
    public Piece piece;

    public int baseValue;
    public int pstBonus;

    // --- Preferred constructor for full control
    public TrackedPiece(PieceType type, Side side, Square position, int baseValue, int pstBonus) {
        this.type = type;
        this.side = side;
        this.position = position;
        this.baseValue = baseValue;
        this.pstBonus = pstBonus;
        this.piece = Piece.make(side, type);
    }

    // --- Convenience constructor from Board state (deprecated - use full constructor with phase)
    @Deprecated
    public TrackedPiece(Piece piece, Square position) {
        this(piece.getPieceType(), piece.getPieceSide(), position,
             getBaseValue(piece.getPieceType()),
             PieceSquareTables.getBonus(piece.getPieceType(), piece.getPieceSide(), position, GamePhase.Phase.OPENING));
    }

    public void updatePosition(Square newPosition, GamePhase.Phase phase) {
        this.position = newPosition;
        this.pstBonus = PieceSquareTables.getBonus(type, side, newPosition, phase);
    }

    public void promoteTo(PieceType newType, GamePhase.Phase phase) {
        this.type = newType;
        this.baseValue = getBaseValue(newType);
        this.piece = Piece.make(this.side, newType);
        this.pstBonus = PieceSquareTables.getBonus(newType, side, position, phase);
    }

    public void demoteTo(PieceType originalType, GamePhase.Phase phase) {
        this.type = originalType;
        this.baseValue = getBaseValue(originalType);
        this.piece = Piece.make(this.side, originalType);
        this.pstBonus = PieceSquareTables.getBonus(originalType, side, position, phase);
    }


    public int getTotalValue() {
        return baseValue + pstBonus;
    }

    // Returns signed value for material tracking (White perspective)
    // Base material: White positive, Black negative
    // PST bonuses: Applied symmetrically - good positions are positive for both sides
    //              but signed for White-perspective material score
    public int getSignedValue() {
        if (side == Side.WHITE) {
            return baseValue + pstBonus;
        } else {
            // For Black: negate base value, negate PST bonus
            // (PST bonuses represent "goodness" from that piece's perspective)
            return -(baseValue + pstBonus);
        }
    }

    public Square getPosition() {
        return position;
    }

    public Piece getPiece() {
        return piece;
    }

    public static int getBaseValue(PieceType type) {
        return switch (type) {
            case PAWN -> 100;
            case KNIGHT -> 320;
            case BISHOP -> 330;
            case ROOK -> 500;
            case QUEEN -> 900;
            case KING -> 0;
            default -> 0;
        };
    }
}
