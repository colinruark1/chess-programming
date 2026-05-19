package mybot;

public class PieceSquareTables {

    // Tables laid out rank 8→1, file a→h (index 0 = a8, index 63 = h1)
    // White index = sq (A1=0 ... H8=63 flipped: use 63-sq to get rank-8-first order)
    // Actually tables are rank-8-first so: White uses (56 ^ sq) to flip rank, Black uses sq
    // Wait — original code: White = newPos.ordinal(), Black = 63 - newPos.ordinal()
    // With chesslib A1=0 ordering that means White uses sq directly, Black mirrors.
    // We preserve that: White index = sq, Black index = 63 - sq.

    private static final short[] PawnTable = {
        0,  0,  0,  0,  0,  0,  0,  0,
       50, 50, 50, 50, 50, 50, 50, 50,
       10, 10, 20, 30, 30, 20, 10, 10,
        5,  5, 10, 27, 27, 10,  5,  5,
        0,  0,  0, 25, 25,  0,  0,  0,
        5, -5,-10,  0,  0,-10, -5,  5,
        5, 10, 10,-25,-25, 10, 10,  5,
        0,  0,  0,  0,  0,  0,  0,  0
    };

    private static final short[] KnightTable = {
       -50,-40,-30,-30,-30,-30,-40,-50,
       -40,-20,  0,  0,  0,  0,-20,-40,
       -30,  0, 10, 15, 15, 10,  0,-30,
       -30,  5, 15, 20, 20, 15,  5,-30,
       -30,  0, 15, 20, 20, 15,  0,-30,
       -30,  5, 10, 15, 15, 10,  5,-30,
       -40,-20,  0,  5,  5,  0,-20,-40,
       -50,-40,-20,-30,-30,-20,-40,-50,
    };

    private static final short[] BishopTable = {
       -20,-10,-10,-10,-10,-10,-10,-20,
       -10,  0,  0,  0,  0,  0,  0,-10,
       -10,  0,  5, 10, 10,  5,  0,-10,
       -10,  5,  5, 10, 10,  5,  5,-10,
       -10,  0, 10, 10, 10, 10,  0,-10,
       -10, 10, 10, 10, 10, 10, 10,-10,
       -10,  5,  0,  0,  0,  0,  5,-10,
       -20,-10,-40,-10,-10,-40,-10,-20,
    };

    private static final short[] KingTable = {
       -30,-40,-40,-50,-50,-40,-40,-30,
       -30,-40,-40,-50,-50,-40,-40,-30,
       -30,-40,-40,-50,-50,-40,-40,-30,
       -30,-40,-40,-50,-50,-40,-40,-30,
       -20,-30,-30,-40,-40,-30,-30,-20,
       -10,-20,-20,-20,-20,-20,-20,-10,
        20, 20,  0,  0,  0,  0, 20, 20,
        20, 30, 10,  0,  0, 10, 30, 20
    };

    private static final short[] KingTableEndGame = {
       -50,-40,-30,-20,-20,-30,-40,-50,
       -30,-20,-10,  0,  0,-10,-20,-30,
       -30,-10, 20, 30, 30, 20,-10,-30,
       -30,-10, 30, 40, 40, 30,-10,-30,
       -30,-10, 30, 40, 40, 30,-10,-30,
       -30,-10, 20, 30, 30, 20,-10,-30,
       -30,-30,  0,  0,  0,  0,-30,-30,
       -50,-30,-30,-30,-30,-30,-30,-50
    };

    private static final short[] QueenTableMiddlegame = {
       -20,-10,-10, -5, -5,-10,-10,-20,
       -10,  0,  0,  0,  0,  0,  0,-10,
       -10,  0,  5,  5,  5,  5,  0,-10,
        -5,  0,  5,  5,  5,  5,  0, -5,
         0,  0,  5,  5,  5,  5,  0, -5,
       -10,  5,  5,  5,  5,  5,  0,-10,
       -10,  0,  5,  0,  0,  0,  0,-10,
       -20,-10,-10, -5, -5,-10,-10,-20
    };

    private static final short[] QueenTableEndgame = {
       -20,-10,-10, -5, -5,-10,-10,-20,
       -10,  0,  5,  5,  5,  5,  0,-10,
       -10,  5,  5,  5,  5,  5,  5,-10,
        -5,  0,  5,  5,  5,  5,  0, -5,
        -5,  0,  5,  5,  5,  5,  0, -5,
       -10,  0,  5,  5,  5,  5,  0,-10,
       -10,  0,  0,  0,  0,  0,  0,-10,
       -20,-10,-10, -5, -5,-10,-10,-20
    };

    private static final short[] RookTableMiddlegame = {
         0,  0,  0,  0,  0,  0,  0,  0,
        50, 50, 50, 50, 50, 50, 50, 50,
        -5,  0,  0,  0,  0,  0,  0, -5,
        -5,  0,  0,  0,  0,  0,  0, -5,
        -5,  0,  0,  0,  0,  0,  0, -5,
        -5,  0,  0,  0,  0,  0,  0, -5,
        -5,  0,  0,  0,  0,  0,  0, -5,
         0,  0,  0,  5,  5,  0,  0,  0
    };

    private static final short[] RookTableEndgame = {
         0,  0,  0,  0,  0,  0,  0,  0,
         5,  5,  5,  5,  5,  5,  5,  5,
        -5,  0,  0,  0,  0,  0,  0, -5,
        -5,  0,  0,  0,  0,  0,  0, -5,
        -5,  0,  0,  0,  0,  0,  0, -5,
        -5,  0,  0,  0,  0,  0,  0, -5,
        -5,  0,  0,  0,  0,  0,  0, -5,
         0,  0,  0,  0,  0,  0,  0,  0
    };

    private static final short[] PawnTableEndgame = {
          0,  0,  0,  0,  0,  0,  0,  0,
        150,150,150,150,150,150,150,150,
         80, 80, 80, 80, 80, 80, 80, 80,
         40, 40, 40, 50, 50, 40, 40, 40,
         20, 20, 20, 30, 30, 20, 20, 20,
         10, 10, 10, 15, 15, 10, 10, 10,
          5,  5,  5,  5,  5,  5,  5,  5,
          0,  0,  0,  0,  0,  0,  0,  0
    };

    /**
     * @param type   Piece.PAWN .. Piece.KING
     * @param color  Piece.WHITE or Piece.BLACK
     * @param sq     Square 0-63 (A1=0, H8=63)
     * @param phase  Current game phase
     */
    public static short getBonus(int type, int color, int sq, GamePhase.Phase phase) {
        int index = color == Piece.WHITE ? sq : 63 - sq;
        return switch (type) {
            case Piece.PAWN   -> (phase == GamePhase.Phase.ENDGAME) ? PawnTableEndgame[index]      : PawnTable[index];
            case Piece.KNIGHT -> KnightTable[index];
            case Piece.BISHOP -> BishopTable[index];
            case Piece.ROOK   -> (phase == GamePhase.Phase.ENDGAME) ? RookTableEndgame[index]      : RookTableMiddlegame[index];
            case Piece.QUEEN  -> (phase == GamePhase.Phase.ENDGAME) ? QueenTableEndgame[index]     : QueenTableMiddlegame[index];
            case Piece.KING   -> (phase == GamePhase.Phase.ENDGAME) ? KingTableEndGame[index]      : KingTable[index];
            default -> 0;
        };
    }

    /** Linearly interpolated bonus: phaseValue 0.0 = opening, 1.0 = endgame. */
    public static int getBonusInterpolated(int type, int color, int sq, double phaseValue) {
        phaseValue = Math.max(0.0, Math.min(1.0, phaseValue));
        int index = color == Piece.WHITE ? sq : 63 - sq;

        int opening, endgame;
        switch (type) {
            case Piece.PAWN:   opening = PawnTable[index];           endgame = PawnTableEndgame[index];  break;
            case Piece.KNIGHT: opening = KnightTable[index];         endgame = KnightTable[index];        break;
            case Piece.BISHOP: opening = BishopTable[index];         endgame = BishopTable[index];        break;
            case Piece.ROOK:   opening = RookTableMiddlegame[index]; endgame = RookTableEndgame[index];   break;
            case Piece.QUEEN:  opening = QueenTableMiddlegame[index];endgame = QueenTableEndgame[index];  break;
            case Piece.KING:   opening = KingTable[index];           endgame = KingTableEndGame[index];   break;
            default: return 0;
        }
        return (int) Math.round(opening * (1.0 - phaseValue) + endgame * phaseValue);
    }
}
