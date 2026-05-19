package mybot;

public class TrackedPiece {
    public int type;     // Piece.PAWN .. Piece.KING
    public final int color; // Piece.WHITE or Piece.BLACK
    public int position;    // square 0-63
    public int piece;       // full piece code (color<<3|type)

    public int baseValue;
    public int pstBonus;

    public TrackedPiece(int type, int color, int position, int baseValue, int pstBonus) {
        this.type = type;
        this.color = color;
        this.position = position;
        this.baseValue = baseValue;
        this.pstBonus = pstBonus;
        this.piece = Piece.make(color, type);
    }

    public void updatePosition(int newPosition, GamePhase.Phase phase) {
        this.position = newPosition;
        this.pstBonus = PieceSquareTables.getBonus(type, color, newPosition, phase);
    }

    public void promoteTo(int newType, GamePhase.Phase phase) {
        this.type = newType;
        this.baseValue = getBaseValue(newType);
        this.piece = Piece.make(color, newType);
        this.pstBonus = PieceSquareTables.getBonus(newType, color, position, phase);
    }

    public void demoteTo(int originalType, GamePhase.Phase phase) {
        this.type = originalType;
        this.baseValue = getBaseValue(originalType);
        this.piece = Piece.make(color, originalType);
        this.pstBonus = PieceSquareTables.getBonus(originalType, color, position, phase);
    }

    public int getTotalValue() {
        return baseValue + pstBonus;
    }

    /** Returns signed value from White's perspective: positive for White, negative for Black. */
    public int getSignedValue() {
        int val = baseValue + pstBonus;
        return color == Piece.WHITE ? val : -val;
    }

    public static int getBaseValue(int type) {
        return switch (type) {
            case Piece.PAWN   -> 100;
            case Piece.KNIGHT -> 320;
            case Piece.BISHOP -> 330;
            case Piece.ROOK   -> 500;
            case Piece.QUEEN  -> 900;
            default           -> 0;
        };
    }
}
