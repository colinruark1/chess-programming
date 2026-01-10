package mybot;

import com.github.bhlangonijr.chesslib.*;
import com.github.bhlangonijr.chesslib.move.Move;
import com.github.bhlangonijr.chesslib.Board;

import java.util.*;

public class TranspositionEntry {
    public final int depth;
    public final int score;
    public final EntryType type;
    public final Move bestMove; // <-- Add this

    public TranspositionEntry(int depth, int score, EntryType type, Move bestMove) {
        this.depth = depth;
        this.score = score;
        this.type = type;
        this.bestMove = bestMove;
    }

    public TranspositionEntry(int depth, int score, EntryType type) {
        this(depth, score, type, null); // Default bestMove = null
    }

    public enum EntryType { EXACT, LOWERBOUND, UPPERBOUND }
}

