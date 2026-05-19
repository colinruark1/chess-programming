package mybot;

public class MoveInfo {
    public final int move;            // int-encoded move
    public final int movingPiece;     // Piece code (color<<3|type)
    public final int capturedPiece;   // Piece.EMPTY if none
    public final int promotionBefore; // piece type before promotion, Piece.NONE if not a promotion
    public final boolean wasCastling;
    public final boolean wasEnPassant;
    public final int epCaptureSquare; // Sq.NONE for non-EP moves

    public MoveInfo(int move, int movingPiece, int capturedPiece,
                    int promotionBefore, boolean wasCastling, boolean wasEnPassant,
                    int epCaptureSquare) {
        this.move = move;
        this.movingPiece = movingPiece;
        this.capturedPiece = capturedPiece;
        this.promotionBefore = promotionBefore;
        this.wasCastling = wasCastling;
        this.wasEnPassant = wasEnPassant;
        this.epCaptureSquare = epCaptureSquare;
    }

    @Override
    public String toString() {
        String prefix = switch (Piece.type(movingPiece)) {
            case Piece.QUEEN  -> "Q";
            case Piece.ROOK   -> "R";
            case Piece.BISHOP -> "B";
            case Piece.KNIGHT -> "N";
            case Piece.KING   -> "K";
            default           -> "";
        };
        return prefix + Sq.name(Move.to(move));
    }
}
