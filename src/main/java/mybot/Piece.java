package mybot;

public final class Piece {
    // Colors
    public static final int WHITE = 0;
    public static final int BLACK = 1;

    // Piece types (0 = none/empty)
    public static final int NONE   = 0;
    public static final int PAWN   = 1;
    public static final int KNIGHT = 2;
    public static final int BISHOP = 3;
    public static final int ROOK   = 4;
    public static final int QUEEN  = 5;
    public static final int KING   = 6;

    // Colored pieces: (color << 3) | type
    public static final int EMPTY    = 0;
    public static final int W_PAWN   = (WHITE << 3) | PAWN;    // 1
    public static final int W_KNIGHT = (WHITE << 3) | KNIGHT;  // 2
    public static final int W_BISHOP = (WHITE << 3) | BISHOP;  // 3
    public static final int W_ROOK   = (WHITE << 3) | ROOK;    // 4
    public static final int W_QUEEN  = (WHITE << 3) | QUEEN;   // 5
    public static final int W_KING   = (WHITE << 3) | KING;    // 6
    public static final int B_PAWN   = (BLACK << 3) | PAWN;    // 9
    public static final int B_KNIGHT = (BLACK << 3) | KNIGHT;  // 10
    public static final int B_BISHOP = (BLACK << 3) | BISHOP;  // 11
    public static final int B_ROOK   = (BLACK << 3) | ROOK;    // 12
    public static final int B_QUEEN  = (BLACK << 3) | QUEEN;   // 13
    public static final int B_KING   = (BLACK << 3) | KING;    // 14

    private Piece() {}

    public static int make(int color, int type) { return (color << 3) | type; }
    public static int type(int piece)  { return piece & 7; }
    public static int color(int piece) { return piece >> 3; }

    public static char toChar(int piece) {
        char c = switch (type(piece)) {
            case PAWN   -> 'p';
            case KNIGHT -> 'n';
            case BISHOP -> 'b';
            case ROOK   -> 'r';
            case QUEEN  -> 'q';
            case KING   -> 'k';
            default     -> '.';
        };
        return color(piece) == WHITE ? Character.toUpperCase(c) : c;
    }

    public static int fromChar(char c) {
        int color = Character.isUpperCase(c) ? WHITE : BLACK;
        int type = switch (Character.toLowerCase(c)) {
            case 'p' -> PAWN;
            case 'n' -> KNIGHT;
            case 'b' -> BISHOP;
            case 'r' -> ROOK;
            case 'q' -> QUEEN;
            case 'k' -> KING;
            default  -> NONE;
        };
        return make(color, type);
    }

    public static int baseValue(int type) {
        return switch (type) {
            case PAWN   -> 100;
            case KNIGHT -> 320;
            case BISHOP -> 330;
            case ROOK   -> 500;
            case QUEEN  -> 900;
            case KING   -> 20000;
            default     -> 0;
        };
    }
}
