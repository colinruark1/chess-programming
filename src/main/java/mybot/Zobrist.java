package mybot;

import java.util.Random;

public final class Zobrist {
    // PIECE[pieceCode][square] — piece codes 0-14 per Piece constants
    public static final long[][] PIECE    = new long[15][64];
    // EP_FILE[file] — en passant file (0-7)
    public static final long[]   EP_FILE  = new long[8];
    // CASTLING[rights] — all 16 combinations of castling rights (4 bits)
    public static final long[]   CASTLING = new long[16];
    // XOR in when black is to move
    public static final long     BLACK_MOVE;

    static {
        // Fixed seed for reproducibility across sessions
        Random rng = new Random(0x123456789ABCDEFL);
        for (int p = 0; p < 15; p++)
            for (int sq = 0; sq < 64; sq++)
                PIECE[p][sq] = rng.nextLong();
        for (int f = 0; f < 8; f++)
            EP_FILE[f] = rng.nextLong();
        for (int c = 0; c < 16; c++)
            CASTLING[c] = rng.nextLong();
        BLACK_MOVE = rng.nextLong();
    }

    private Zobrist() {}
}
