package mybot;

import com.github.bhlangonijr.chesslib.*;
import com.github.bhlangonijr.chesslib.move.Move;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.File;


import java.util.*;

public class MoveInfo {
    public final Move move;
    public final Piece movingPiece;
    public final Piece capturedPiece;
    public final PieceType promotionBefore;  // what it was before promotion (if any)
    public final boolean wasCastling;
    public final boolean wasEnPassant;
    public final Square epCaptureSquare;     // null for non-EP moves

    public MoveInfo(Move move, Piece movingPiece, Piece capturedPiece,
                    PieceType promotionBefore, boolean wasCastling, boolean wasEnPassant,
                    Square epCaptureSquare) {
        this.move = move;
        this.movingPiece = movingPiece;
        this.capturedPiece = capturedPiece;
        this.promotionBefore = promotionBefore;
        this.wasCastling = wasCastling;
        this.wasEnPassant = wasEnPassant;
        this.epCaptureSquare = epCaptureSquare;
    }

    public String toString() {
        String pieceType = switch(movingPiece.getPieceType()) {
            case QUEEN -> "Q";
            case ROOK -> "R";
            case BISHOP -> "B";
            case KNIGHT -> "N";
            case PAWN -> "";
            case KING -> "K";
            default -> "";
        };
        return pieceType + move.getTo();
    }

}
