package mybot;

public class TranspositionEntry {
    public final int depth;
    public final int score;
    public final EntryType type;
    public final int bestMove; // Move.NONE = 0 when absent

    public TranspositionEntry(int depth, int score, EntryType type, int bestMove) {
        this.depth = depth;
        this.score = score;
        this.type = type;
        this.bestMove = bestMove;
    }

    public TranspositionEntry(int depth, int score, EntryType type) {
        this(depth, score, type, Move.NONE);
    }

    public enum EntryType { EXACT, LOWERBOUND, UPPERBOUND }
}
