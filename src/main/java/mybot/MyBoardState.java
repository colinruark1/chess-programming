package mybot;

import com.github.bhlangonijr.chesslib.*;
import com.github.bhlangonijr.chesslib.move.Move;

import java.util.*;

public class MyBoardState {
    private final Board board = new Board();
    private final PieceTracker tracker = new PieceTracker();

    private final Deque<MoveInfo> moveHistory = new ArrayDeque<>();

    public MyBoardState() {
        tracker.initializeFromBoard(board);
    }

    public List<Move> getLegalMoves() {
        return board.legalMoves();
    }

    public void doMove(Move move) {
        Piece moving = board.getPiece(move.getFrom());
        Piece captured = board.getPiece(move.getTo());

        Square oldPos = move.getFrom();

        board.doMove(move);
        tracker.applyMove(move, moving, captured, board);

        moveHistory.push(new MoveInfo(move, moving, captured, oldPos));
    }

    public void undoMove() {
        if (moveHistory.isEmpty()) return;

        MoveInfo info = moveHistory.pop();
        board.undoMove();
        tracker.undoMove(info.move, info.movingPiece, info.capturedPiece, board);
    }

    public int evaluate() {
        return tracker.getMaterialScore(board);
    }

    private static class MoveInfo {
        Move move;
        Piece movingPiece;
        Piece capturedPiece;
        Square oldPosition;

        MoveInfo(Move move, Piece movingPiece, Piece capturedPiece, Square oldPosition) {
            this.move = move;
            this.movingPiece = movingPiece;
            this.capturedPiece = capturedPiece;
            this.oldPosition = oldPosition;
        }
    }

    public Side getSideToMove() {
        return board.getSideToMove();
    }

    public boolean isGameOver() {
        return board.isMated() || board.isDraw();
    }
}
