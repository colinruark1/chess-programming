package mybot;

public final class Move {
    // Null sentinel — from=0, to=0, flags=0 is not a legal move in any position
    public static final int NONE = 0;

    // Flag constants (4 bits, stored in bits 12-15)
    public static final int QUIET       = 0;
    public static final int DOUBLE_PUSH = 1;
    public static final int CASTLE_K    = 2;  // king-side castle
    public static final int CASTLE_Q    = 3;  // queen-side castle
    public static final int CAPTURE     = 4;
    public static final int EN_PASSANT  = 5;
    // 6, 7 unused
    public static final int PROMO_N     = 8;
    public static final int PROMO_B     = 9;
    public static final int PROMO_R     = 10;
    public static final int PROMO_Q     = 11;
    public static final int PROMO_CAP_N = 12;
    public static final int PROMO_CAP_B = 13;
    public static final int PROMO_CAP_R = 14;
    public static final int PROMO_CAP_Q = 15;

    private Move() {}

    public static int of(int from, int to, int flags) {
        return from | (to << 6) | (flags << 12);
    }

    public static int from(int move)  { return move & 0x3F; }
    public static int to(int move)    { return (move >> 6) & 0x3F; }
    public static int flags(int move) { return (move >> 12) & 0xF; }

    public static boolean isCapture(int move)    { return (flags(move) & 4) != 0; }
    public static boolean isCastle(int move)     { int f = flags(move); return f == CASTLE_K || f == CASTLE_Q; }
    public static boolean isPromotion(int move)  { return (flags(move) & 8) != 0; }
    public static boolean isEnPassant(int move)  { return flags(move) == EN_PASSANT; }
    public static boolean isDoublePush(int move) { return flags(move) == DOUBLE_PUSH; }
    public static boolean isQuiet(int move)      { return flags(move) == QUIET; }

    public static int promoType(int move) {
        return switch (flags(move) & 3) {
            case 0  -> Piece.KNIGHT;
            case 1  -> Piece.BISHOP;
            case 2  -> Piece.ROOK;
            default -> Piece.QUEEN;
        };
    }

    public static String toUci(int move) {
        if (move == NONE) return "0000";
        String s = Sq.name(from(move)) + Sq.name(to(move));
        if (isPromotion(move)) s += "nbrq".charAt(flags(move) & 3);
        return s;
    }

    public static int fromUci(String uci) {
        if (uci == null || uci.length() < 4) return NONE;
        int from = Sq.parse(uci.substring(0, 2));
        int to   = Sq.parse(uci.substring(2, 4));
        if (from == Sq.NONE || to == Sq.NONE) return NONE;
        // Promotion flag determined later by MoveGenerator context; start as QUIET
        // Caller (parseMove in ChessEngine) matches against generated legal moves
        int flags = QUIET;
        if (uci.length() == 5) {
            flags = switch (uci.charAt(4)) {
                case 'n' -> PROMO_N;
                case 'b' -> PROMO_B;
                case 'r' -> PROMO_R;
                default  -> PROMO_Q;
            };
        }
        return of(from, to, flags);
    }

    public static String toString(int move) {
        if (move == NONE) return "none";
        return toUci(move) + "(f=" + flags(move) + ")";
    }
}
