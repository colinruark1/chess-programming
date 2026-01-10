package mybot;

import com.github.bhlangonijr.chesslib.*;
import com.github.bhlangonijr.chesslib.move.Move;
import com.github.bhlangonijr.chesslib.Board;
import com.github.bhlangonijr.chesslib.File;


import java.util.*;


public class PieceSquareTables {

    private static final short[] PawnTable = new short[]
    {
        0,  0,  0,  0,  0,  0,  0,  0,
        50, 50, 50, 50, 50, 50, 50, 50,
        10, 10, 20, 30, 30, 20, 10, 10,
        5,  5, 10, 27, 27, 10,  5,  5,
        0,  0,  0, 25, 25,  0,  0,  0,
        5, -5,-10,  0,  0,-10, -5,  5,
        5, 10, 10,-25,-25, 10, 10,  5,
        0,  0,  0,  0,  0,  0,  0,  0
    };

    private static final short[] KnightTable = new short[]
    {
        -50,-40,-30,-30,-30,-30,-40,-50,
        -40,-20,  0,  0,  0,  0,-20,-40,
        -30,  0, 10, 15, 15, 10,  0,-30,
        -30,  5, 15, 20, 20, 15,  5,-30,
        -30,  0, 15, 20, 20, 15,  0,-30,
        -30,  5, 10, 15, 15, 10,  5,-30,
        -40,-20,  0,  5,  5,  0,-20,-40,
        -50,-40,-20,-30,-30,-20,-40,-50,
    };

    private static final short[] BishopTable = new short[]
    {
        -20,-10,-10,-10,-10,-10,-10,-20,
        -10,  0,  0,  0,  0,  0,  0,-10,
        -10,  0,  5, 10, 10,  5,  0,-10,
        -10,  5,  5, 10, 10,  5,  5,-10,
        -10,  0, 10, 10, 10, 10,  0,-10,
        -10, 10, 10, 10, 10, 10, 10,-10,
        -10,  5,  0,  0,  0,  0,  5,-10,
        -20,-10,-40,-10,-10,-40,-10,-20,
    };

    private static final short[] KingTable = new short[]
    {
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -20, -30, -30, -40, -40, -30, -30, -20,
        -10, -20, -20, -20, -20, -20, -20, -10, 
        20,  20,   0,   0,   0,   0,  20,  20,
        20,  30,  10,   0,   0,  10,  30,  20
    };
    private static final short[] KingTableEndGame = new short[]
    {
        -50,-40,-30,-20,-20,-30,-40,-50,
        -30,-20,-10,  0,  0,-10,-20,-30,
        -30,-10, 20, 30, 30, 20,-10,-30,
        -30,-10, 30, 40, 40, 30,-10,-30,
        -30,-10, 30, 40, 40, 30,-10,-30,
        -30,-10, 20, 30, 30, 20,-10,-30,
        -30,-30,  0,  0,  0,  0,-30,-30,
        -50,-30,-30,-30,-30,-30,-30,-50
    };

    private static final short[] QueenTableMiddlegame = new short[]
    {
        -20,-10,-10, -5, -5,-10,-10,-20,
        -10,  0,  0,  0,  0,  0,  0,-10,
        -10,  0,  5,  5,  5,  5,  0,-10,
         -5,  0,  5,  5,  5,  5,  0, -5,
          0,  0,  5,  5,  5,  5,  0, -5,
        -10,  5,  5,  5,  5,  5,  0,-10,
        -10,  0,  5,  0,  0,  0,  0,-10,
        -20,-10,-10, -5, -5,-10,-10,-20
    };

    private static final short[] QueenTableEndgame = new short[]
    {
        -20,-10,-10, -5, -5,-10,-10,-20,
        -10,  0,  5,  5,  5,  5,  0,-10,
        -10,  5,  5,  5,  5,  5,  5,-10,
         -5,  0,  5,  5,  5,  5,  0, -5,
         -5,  0,  5,  5,  5,  5,  0, -5,
        -10,  0,  5,  5,  5,  5,  0,-10,
        -10,  0,  0,  0,  0,  0,  0,-10,
        -20,-10,-10, -5, -5,-10,-10,-20
    };

    private static final short[] RookTableMiddlegame = new short[]
    {
          0,  0,  0,  0,  0,  0,  0,  0,
         50, 50, 50, 50, 50, 50, 50, 50,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
          0,  0,  0,  5,  5,  0,  0,  0
    };

    private static final short[] RookTableEndgame = new short[]
    {
          0,  0,  0,  0,  0,  0,  0,  0,
          5,  5,  5,  5,  5,  5,  5,  5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
          0,  0,  0,  0,  0,  0,  0,  0
    };

    private static final short[] PawnTableEndgame = new short[]
    {
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
     * Returns PST bonus for a piece based on type, side, position, and game phase.
     * Phase-aware: uses different tables for opening/middlegame vs endgame.
     *
     * @param type The piece type
     * @param side The piece's side (WHITE or BLACK)
     * @param newPos The square position
     * @param phase The current game phase
     * @return PST bonus value (centipawns)
     */
    public static short getBonus(PieceType type, Side side, Square newPos, GamePhase.Phase phase) {
        int index = side == Side.WHITE ? newPos.ordinal() : 63 - newPos.ordinal();

        return switch (type) {
            case PAWN   -> (phase == GamePhase.Phase.ENDGAME) ? PawnTableEndgame[index] : PawnTable[index];
            case KNIGHT -> KnightTable[index];
            case BISHOP -> BishopTable[index];
            case ROOK   -> (phase == GamePhase.Phase.ENDGAME) ? RookTableEndgame[index] : RookTableMiddlegame[index];
            case QUEEN  -> (phase == GamePhase.Phase.ENDGAME) ? QueenTableEndgame[index] : QueenTableMiddlegame[index];
            case KING   -> (phase == GamePhase.Phase.ENDGAME) ? KingTableEndGame[index] : KingTable[index];
            default -> 0;
        };
    }

    /**
     * Returns interpolated PST bonus based on a continuous game phase value.
     * Smoothly transitions from opening/middlegame tables to endgame tables.
     *
     * @param type The piece type
     * @param side The piece's side (WHITE or BLACK)
     * @param newPos The square position
     * @param phaseValue Continuous phase value (0.0 = opening, 1.0 = endgame)
     * @return Interpolated PST bonus value (centipawns)
     */
    public static int getBonusInterpolated(PieceType type, Side side, Square newPos, double phaseValue) {
        // Clamp phase value to [0.0, 1.0]
        phaseValue = Math.max(0.0, Math.min(1.0, phaseValue));

        int index = side == Side.WHITE ? newPos.ordinal() : 63 - newPos.ordinal();

        // Get opening/middlegame and endgame values
        int openingValue, endgameValue;

        switch (type) {
            case PAWN:
                openingValue = PawnTable[index];
                endgameValue = PawnTableEndgame[index];
                break;
            case KNIGHT:
                openingValue = KnightTable[index];
                endgameValue = KnightTable[index];  // Knight table doesn't change
                break;
            case BISHOP:
                openingValue = BishopTable[index];
                endgameValue = BishopTable[index];  // Bishop table doesn't change
                break;
            case ROOK:
                openingValue = RookTableMiddlegame[index];
                endgameValue = RookTableEndgame[index];
                break;
            case QUEEN:
                openingValue = QueenTableMiddlegame[index];
                endgameValue = QueenTableEndgame[index];
                break;
            case KING:
                openingValue = KingTable[index];
                endgameValue = KingTableEndGame[index];
                break;
            default:
                return 0;
        }

        // Linear interpolation: opening * (1 - phase) + endgame * phase
        return (int) Math.round(openingValue * (1.0 - phaseValue) + endgameValue * phaseValue);
    }

}
