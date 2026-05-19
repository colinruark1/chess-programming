package mybot;

public final class Bitboard {
    // Rank masks
    public static final long RANK_1 = 0x00000000000000FFL;
    public static final long RANK_2 = 0x000000000000FF00L;
    public static final long RANK_3 = 0x0000000000FF0000L;
    public static final long RANK_4 = 0x00000000FF000000L;
    public static final long RANK_5 = 0x000000FF00000000L;
    public static final long RANK_6 = 0x0000FF0000000000L;
    public static final long RANK_7 = 0x00FF000000000000L;
    public static final long RANK_8 = 0xFF00000000000000L;

    // File masks
    public static final long FILE_A = 0x0101010101010101L;
    public static final long FILE_B = 0x0202020202020202L;
    public static final long FILE_C = 0x0404040404040404L;
    public static final long FILE_D = 0x0808080808080808L;
    public static final long FILE_E = 0x1010101010101010L;
    public static final long FILE_F = 0x2020202020202020L;
    public static final long FILE_G = 0x4040404040404040L;
    public static final long FILE_H = 0x8080808080808080L;

    // Edge exclusion masks (for shift helpers)
    public static final long NOT_FILE_A  = ~FILE_A;
    public static final long NOT_FILE_H  = ~FILE_H;
    public static final long NOT_FILE_AB = ~(FILE_A | FILE_B);
    public static final long NOT_FILE_GH = ~(FILE_G | FILE_H);

    // Square color masks
    public static final long LIGHT_SQUARES = 0x55AA55AA55AA55AAL;
    public static final long DARK_SQUARES  = ~LIGHT_SQUARES;

    private Bitboard() {}

    public static long bit(int sq)      { return 1L << sq; }
    public static int  lsb(long bb)     { return Long.numberOfTrailingZeros(bb); }
    public static int  msb(long bb)     { return 63 - Long.numberOfLeadingZeros(bb); }
    public static int  popcount(long bb){ return Long.bitCount(bb); }
    public static long popLsb(long bb)  { return bb & (bb - 1); }

    // Direction shift helpers (wrap-safe)
    public static long northOne(long b) { return b << 8; }
    public static long southOne(long b) { return b >>> 8; }
    public static long eastOne(long b)  { return (b & NOT_FILE_H) << 1; }
    public static long westOne(long b)  { return (b & NOT_FILE_A) >>> 1; }
    public static long noEaOne(long b)  { return (b & NOT_FILE_H) << 9; }
    public static long noWeOne(long b)  { return (b & NOT_FILE_A) << 7; }
    public static long soEaOne(long b)  { return (b & NOT_FILE_H) >>> 7; }
    public static long soWeOne(long b)  { return (b & NOT_FILE_A) >>> 9; }

    public static long rankMask(int rank) {
        return RANK_1 << (rank * 8);
    }

    public static long fileMask(int file) {
        return FILE_A << file;
    }

    public static String pretty(long bb) {
        StringBuilder sb = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            sb.append(rank + 1).append("  ");
            for (int file = 0; file < 8; file++) {
                int sq = rank * 8 + file;
                sb.append((bb & bit(sq)) != 0 ? "1 " : ". ");
            }
            sb.append('\n');
        }
        sb.append("   a b c d e f g h");
        return sb.toString();
    }
}
