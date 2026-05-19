package mybot;

/**
 * Legal move generation using bitboards.
 * All moves are returned as int-encoded Move values.
 */
public final class MoveGenerator {

    private static final int MAX_MOVES = 256;

    private MoveGenerator() {}

    // ── Public entry points ──

    public static int[] generateLegal(Board board) {
        int[] buf = new int[MAX_MOVES];
        int count = generate(board, buf, false);
        return trim(buf, count);
    }

    public static int[] generateCaptures(Board board) {
        int[] buf = new int[MAX_MOVES];
        int count = generate(board, buf, true);
        return trim(buf, count);
    }

    // ── Core generation ──

    private static int generate(Board board, int[] buf, boolean capturesOnly) {
        int us   = board.sideToMove();
        int them = us ^ 1;
        int kingSq = board.kingSquare(us);
        long occ = board.allPieces();

        long checkers = computeCheckers(board, kingSq, them, occ);
        boolean inCheck = checkers != 0;
        boolean doubleCheck = Long.bitCount(checkers) > 1;

        int count = 0;

        // In double check only king moves are legal
        if (doubleCheck) {
            return generateKingMoves(board, buf, count, us, them, occ, capturesOnly);
        }

        // Compute the set of squares a non-king piece can move to in order to resolve check
        // (block or capture the checker). When not in check, any square is valid.
        long checkMask = inCheck ? (checkers | AttackTables.BETWEEN[kingSq][Bitboard.lsb(checkers)]) : ~0L;

        // Pinned pieces: they can only move along the pin ray
        long pinned = computePinned(board, kingSq, us, them, occ);

        count = generatePawnMoves(board, buf, count, us, them, occ, checkMask, pinned, kingSq, inCheck, capturesOnly);
        count = generateKnightMoves(board, buf, count, us, them, occ, checkMask, pinned, capturesOnly);
        count = generateBishopMoves(board, buf, count, us, them, occ, checkMask, pinned, kingSq, capturesOnly);
        count = generateRookMoves(board, buf, count, us, them, occ, checkMask, pinned, kingSq, capturesOnly);
        count = generateQueenMoves(board, buf, count, us, them, occ, checkMask, pinned, kingSq, capturesOnly);
        count = generateKingMoves(board, buf, count, us, them, occ, capturesOnly);
        if (!capturesOnly && !inCheck) {
            count = generateCastles(board, buf, count, us, them, occ);
        }

        return count;
    }

    // ── Check and pin detection ──

    private static long computeCheckers(Board board, int kingSq, int them, long occ) {
        return (AttackTables.pawnAttacks(kingSq, them ^ 1) & board.pieceBB(them, Piece.PAWN))
             | (AttackTables.knightAttacks(kingSq)          & board.pieceBB(them, Piece.KNIGHT))
             | (AttackTables.bishopAttacks(kingSq, occ)     & (board.pieceBB(them, Piece.BISHOP) | board.pieceBB(them, Piece.QUEEN)))
             | (AttackTables.rookAttacks(kingSq, occ)       & (board.pieceBB(them, Piece.ROOK)   | board.pieceBB(them, Piece.QUEEN)))
             | (AttackTables.kingAttacks(kingSq)            & board.pieceBB(them, Piece.KING));
    }

    private static long computePinned(Board board, int kingSq, int us, int them, long occ) {
        long pinned = 0;
        long ourPieces = board.colorBB(us);
        // Enemy sliders that could pin
        long enemyRQ = board.pieceBB(them, Piece.ROOK)   | board.pieceBB(them, Piece.QUEEN);
        long enemyBQ = board.pieceBB(them, Piece.BISHOP) | board.pieceBB(them, Piece.QUEEN);

        // X-ray through our pieces to find pinners
        long rookXray = AttackTables.rookAttacks(kingSq, occ ^ (ourPieces & AttackTables.rookAttacks(kingSq, occ))) & enemyRQ;
        long bishXray = AttackTables.bishopAttacks(kingSq, occ ^ (ourPieces & AttackTables.bishopAttacks(kingSq, occ))) & enemyBQ;

        long pinners = rookXray | bishXray;
        while (pinners != 0) {
            int pinnerSq = Bitboard.lsb(pinners);
            pinners = Bitboard.popLsb(pinners);
            long between = AttackTables.BETWEEN[kingSq][pinnerSq] & occ;
            if (Long.bitCount(between) == 1 && (between & ourPieces) != 0) {
                pinned |= between;
            }
        }
        return pinned;
    }

    // ── Pawn moves ──

    private static int generatePawnMoves(Board board, int[] buf, int count,
            int us, int them, long occ, long checkMask, long pinned,
            int kingSq, boolean inCheck, boolean capturesOnly) {

        long pawns = board.pieceBB(us, Piece.PAWN);
        long theirPieces = board.colorBB(them);
        long empty = ~occ;

        // ── Single pushes ──
        long singlePush = (us == Piece.WHITE ? Bitboard.northOne(pawns) : Bitboard.southOne(pawns)) & empty;
        long promoPush  = singlePush & (us == Piece.WHITE ? Bitboard.RANK_8 : Bitboard.RANK_1);

        // Quiet promotions: always searched (huge material swing)
        long bb = promoPush & checkMask;
        while (bb != 0) {
            int to   = Bitboard.lsb(bb); bb = Bitboard.popLsb(bb);
            int from = us == Piece.WHITE ? to - 8 : to + 8;
            if ((Bitboard.bit(from) & pinned) != 0 && !onLine(kingSq, from, to)) continue;
            buf[count++] = Move.of(from, to, Move.PROMO_Q);
            buf[count++] = Move.of(from, to, Move.PROMO_R);
            buf[count++] = Move.of(from, to, Move.PROMO_B);
            buf[count++] = Move.of(from, to, Move.PROMO_N);
        }

        if (!capturesOnly) {
            long quietPush = singlePush & ~(us == Piece.WHITE ? Bitboard.RANK_8 : Bitboard.RANK_1);

            // Quiet single push
            bb = quietPush & checkMask;
            while (bb != 0) {
                int to   = Bitboard.lsb(bb); bb = Bitboard.popLsb(bb);
                int from = us == Piece.WHITE ? to - 8 : to + 8;
                if ((Bitboard.bit(from) & pinned) != 0 && !onLine(kingSq, from, to)) continue;
                buf[count++] = Move.of(from, to, Move.QUIET);
            }

            // Double push
            long doublePush = (us == Piece.WHITE
                ? Bitboard.northOne(singlePush & Bitboard.RANK_3)
                : Bitboard.southOne(singlePush & Bitboard.RANK_6)) & empty & checkMask;
            bb = doublePush;
            while (bb != 0) {
                int to   = Bitboard.lsb(bb); bb = Bitboard.popLsb(bb);
                int from = us == Piece.WHITE ? to - 16 : to + 16;
                if ((Bitboard.bit(from) & pinned) != 0 && !onLine(kingSq, from, to)) continue;
                buf[count++] = Move.of(from, to, Move.DOUBLE_PUSH);
            }
        }

        // ── Captures ──
        // East captures
        long eastCaps = (us == Piece.WHITE ? Bitboard.noEaOne(pawns) : Bitboard.soEaOne(pawns)) & theirPieces;
        long promoCaps = eastCaps & (us == Piece.WHITE ? Bitboard.RANK_8 : Bitboard.RANK_1);
        long normCaps  = eastCaps & ~(us == Piece.WHITE ? Bitboard.RANK_8 : Bitboard.RANK_1);

        bb = normCaps & checkMask;
        while (bb != 0) {
            int to   = Bitboard.lsb(bb); bb = Bitboard.popLsb(bb);
            int from = us == Piece.WHITE ? to - 9 : to + 7;
            if ((Bitboard.bit(from) & pinned) != 0 && !onLine(kingSq, from, to)) continue;
            buf[count++] = Move.of(from, to, Move.CAPTURE);
        }
        bb = promoCaps & checkMask;
        while (bb != 0) {
            int to   = Bitboard.lsb(bb); bb = Bitboard.popLsb(bb);
            int from = us == Piece.WHITE ? to - 9 : to + 7;
            if ((Bitboard.bit(from) & pinned) != 0 && !onLine(kingSq, from, to)) continue;
            buf[count++] = Move.of(from, to, Move.PROMO_CAP_Q);
            buf[count++] = Move.of(from, to, Move.PROMO_CAP_R);
            buf[count++] = Move.of(from, to, Move.PROMO_CAP_B);
            buf[count++] = Move.of(from, to, Move.PROMO_CAP_N);
        }

        // West captures
        long westCaps = (us == Piece.WHITE ? Bitboard.noWeOne(pawns) : Bitboard.soWeOne(pawns)) & theirPieces;
        promoCaps = westCaps & (us == Piece.WHITE ? Bitboard.RANK_8 : Bitboard.RANK_1);
        normCaps  = westCaps & ~(us == Piece.WHITE ? Bitboard.RANK_8 : Bitboard.RANK_1);

        bb = normCaps & checkMask;
        while (bb != 0) {
            int to   = Bitboard.lsb(bb); bb = Bitboard.popLsb(bb);
            int from = us == Piece.WHITE ? to - 7 : to + 9;
            if ((Bitboard.bit(from) & pinned) != 0 && !onLine(kingSq, from, to)) continue;
            buf[count++] = Move.of(from, to, Move.CAPTURE);
        }
        bb = promoCaps & checkMask;
        while (bb != 0) {
            int to   = Bitboard.lsb(bb); bb = Bitboard.popLsb(bb);
            int from = us == Piece.WHITE ? to - 7 : to + 9;
            if ((Bitboard.bit(from) & pinned) != 0 && !onLine(kingSq, from, to)) continue;
            buf[count++] = Move.of(from, to, Move.PROMO_CAP_Q);
            buf[count++] = Move.of(from, to, Move.PROMO_CAP_R);
            buf[count++] = Move.of(from, to, Move.PROMO_CAP_B);
            buf[count++] = Move.of(from, to, Move.PROMO_CAP_N);
        }

        // ── En passant ──
        int ep = board.epSquare();
        if (ep != Sq.NONE) {
            long epBit = Bitboard.bit(ep);
            long epPawns = AttackTables.pawnAttacks(ep, them) & pawns;
            while (epPawns != 0) {
                int from = Bitboard.lsb(epPawns); epPawns = Bitboard.popLsb(epPawns);
                int capSq = us == Piece.WHITE ? ep - 8 : ep + 8;
                // EP resolves check only if the checker is the captured pawn
                if (inCheck && (Bitboard.bit(capSq) & checkMask) == 0 && (epBit & checkMask) == 0) continue;
                // Pin check: remove both pawns, see if king is in check
                long occAfterEp = occ ^ Bitboard.bit(from) ^ Bitboard.bit(capSq) ^ epBit;
                if (epExposesKing(board, kingSq, us, them, occAfterEp)) continue;
                buf[count++] = Move.of(from, ep, Move.EN_PASSANT);
            }
        }

        return count;
    }

    private static boolean epExposesKing(Board board, int kingSq, int us, int them, long occAfterEp) {
        long rqSliders = board.pieceBB(them, Piece.ROOK) | board.pieceBB(them, Piece.QUEEN);
        long bqSliders = board.pieceBB(them, Piece.BISHOP) | board.pieceBB(them, Piece.QUEEN);
        return (AttackTables.rookAttacks(kingSq, occAfterEp) & rqSliders) != 0
            || (AttackTables.bishopAttacks(kingSq, occAfterEp) & bqSliders) != 0;
    }

    // ── Knight moves ──

    private static int generateKnightMoves(Board board, int[] buf, int count,
            int us, int them, long occ, long checkMask, long pinned, boolean capturesOnly) {
        long knights = board.pieceBB(us, Piece.KNIGHT) & ~pinned; // pinned knights can never move
        long ourPieces = board.colorBB(us);
        long theirPieces = board.colorBB(them);

        while (knights != 0) {
            int from = Bitboard.lsb(knights); knights = Bitboard.popLsb(knights);
            long attacks = AttackTables.knightAttacks(from) & ~ourPieces & checkMask;
            if (capturesOnly) attacks &= theirPieces;
            while (attacks != 0) {
                int to = Bitboard.lsb(attacks); attacks = Bitboard.popLsb(attacks);
                int flags = (board.pieceAt(to) != Piece.EMPTY) ? Move.CAPTURE : Move.QUIET;
                buf[count++] = Move.of(from, to, flags);
            }
        }
        return count;
    }

    // ── Bishop moves ──

    private static int generateBishopMoves(Board board, int[] buf, int count,
            int us, int them, long occ, long checkMask, long pinned, int kingSq, boolean capturesOnly) {
        long bishops = board.pieceBB(us, Piece.BISHOP);
        long ourPieces = board.colorBB(us);
        long theirPieces = board.colorBB(them);

        while (bishops != 0) {
            int from = Bitboard.lsb(bishops); bishops = Bitboard.popLsb(bishops);
            long attacks = AttackTables.bishopAttacks(from, occ) & ~ourPieces & checkMask;
            if ((Bitboard.bit(from) & pinned) != 0) attacks &= AttackTables.LINE[kingSq][from];
            if (capturesOnly) attacks &= theirPieces;
            while (attacks != 0) {
                int to = Bitboard.lsb(attacks); attacks = Bitboard.popLsb(attacks);
                int flags = (board.pieceAt(to) != Piece.EMPTY) ? Move.CAPTURE : Move.QUIET;
                buf[count++] = Move.of(from, to, flags);
            }
        }
        return count;
    }

    // ── Rook moves ──

    private static int generateRookMoves(Board board, int[] buf, int count,
            int us, int them, long occ, long checkMask, long pinned, int kingSq, boolean capturesOnly) {
        long rooks = board.pieceBB(us, Piece.ROOK);
        long ourPieces = board.colorBB(us);
        long theirPieces = board.colorBB(them);

        while (rooks != 0) {
            int from = Bitboard.lsb(rooks); rooks = Bitboard.popLsb(rooks);
            long attacks = AttackTables.rookAttacks(from, occ) & ~ourPieces & checkMask;
            if ((Bitboard.bit(from) & pinned) != 0) attacks &= AttackTables.LINE[kingSq][from];
            if (capturesOnly) attacks &= theirPieces;
            while (attacks != 0) {
                int to = Bitboard.lsb(attacks); attacks = Bitboard.popLsb(attacks);
                int flags = (board.pieceAt(to) != Piece.EMPTY) ? Move.CAPTURE : Move.QUIET;
                buf[count++] = Move.of(from, to, flags);
            }
        }
        return count;
    }

    // ── Queen moves ──

    private static int generateQueenMoves(Board board, int[] buf, int count,
            int us, int them, long occ, long checkMask, long pinned, int kingSq, boolean capturesOnly) {
        long queens = board.pieceBB(us, Piece.QUEEN);
        long ourPieces = board.colorBB(us);
        long theirPieces = board.colorBB(them);

        while (queens != 0) {
            int from = Bitboard.lsb(queens); queens = Bitboard.popLsb(queens);
            long attacks = AttackTables.queenAttacks(from, occ) & ~ourPieces & checkMask;
            if ((Bitboard.bit(from) & pinned) != 0) attacks &= AttackTables.LINE[kingSq][from];
            if (capturesOnly) attacks &= theirPieces;
            while (attacks != 0) {
                int to = Bitboard.lsb(attacks); attacks = Bitboard.popLsb(attacks);
                int flags = (board.pieceAt(to) != Piece.EMPTY) ? Move.CAPTURE : Move.QUIET;
                buf[count++] = Move.of(from, to, flags);
            }
        }
        return count;
    }

    // ── King moves ──

    private static int generateKingMoves(Board board, int[] buf, int count,
            int us, int them, long occ, boolean capturesOnly) {
        int from = board.kingSquare(us);
        long ourPieces = board.colorBB(us);
        long theirPieces = board.colorBB(them);
        long attacks = AttackTables.kingAttacks(from) & ~ourPieces;
        if (capturesOnly) attacks &= theirPieces;

        while (attacks != 0) {
            int to = Bitboard.lsb(attacks); attacks = Bitboard.popLsb(attacks);
            // King can't move to attacked square
            long occWithoutKing = occ ^ Bitboard.bit(from);
            if (isAttackedByColor(board, to, them, occWithoutKing)) continue;
            int flags = (board.pieceAt(to) != Piece.EMPTY) ? Move.CAPTURE : Move.QUIET;
            buf[count++] = Move.of(from, to, flags);
        }
        return count;
    }

    // ── Castling ──

    private static int generateCastles(Board board, int[] buf, int count,
            int us, int them, long occ) {
        int cr = board.castlingRights();

        if (us == Piece.WHITE) {
            // King-side: e1-g1, rook h1-f1; f1,g1 must be empty; e1,f1,g1 not attacked
            if ((cr & Board.CR_WK) != 0
                    && (occ & 0x60L) == 0
                    && !board.isAttackedBy(Sq.E1, them)
                    && !board.isAttackedBy(Sq.F1, them)
                    && !board.isAttackedBy(Sq.G1, them)) {
                buf[count++] = Move.of(Sq.E1, Sq.G1, Move.CASTLE_K);
            }
            // Queen-side: e1-c1, rook a1-d1; b1,c1,d1 must be empty; e1,d1,c1 not attacked
            if ((cr & Board.CR_WQ) != 0
                    && (occ & 0xEL) == 0
                    && !board.isAttackedBy(Sq.E1, them)
                    && !board.isAttackedBy(Sq.D1, them)
                    && !board.isAttackedBy(Sq.C1, them)) {
                buf[count++] = Move.of(Sq.E1, Sq.C1, Move.CASTLE_Q);
            }
        } else {
            // King-side: e8-g8
            if ((cr & Board.CR_BK) != 0
                    && (occ & 0x6000000000000000L) == 0
                    && !board.isAttackedBy(Sq.E8, them)
                    && !board.isAttackedBy(Sq.F8, them)
                    && !board.isAttackedBy(Sq.G8, them)) {
                buf[count++] = Move.of(Sq.E8, Sq.G8, Move.CASTLE_K);
            }
            // Queen-side: e8-c8
            if ((cr & Board.CR_BQ) != 0
                    && (occ & 0x0E00000000000000L) == 0
                    && !board.isAttackedBy(Sq.E8, them)
                    && !board.isAttackedBy(Sq.D8, them)
                    && !board.isAttackedBy(Sq.C8, them)) {
                buf[count++] = Move.of(Sq.E8, Sq.C8, Move.CASTLE_Q);
            }
        }
        return count;
    }

    // ── Helpers ──

    /** True if sq is attacked by attackerColor, using the given occupancy (for king-move legality). */
    private static boolean isAttackedByColor(Board board, int sq, int attackerColor, long occ) {
        if ((AttackTables.pawnAttacks(sq, attackerColor ^ 1) & board.pieceBB(attackerColor, Piece.PAWN)) != 0) return true;
        if ((AttackTables.knightAttacks(sq) & board.pieceBB(attackerColor, Piece.KNIGHT)) != 0) return true;
        if ((AttackTables.kingAttacks(sq)   & board.pieceBB(attackerColor, Piece.KING))   != 0) return true;
        long rq = board.pieceBB(attackerColor, Piece.ROOK)   | board.pieceBB(attackerColor, Piece.QUEEN);
        long bq = board.pieceBB(attackerColor, Piece.BISHOP) | board.pieceBB(attackerColor, Piece.QUEEN);
        if ((AttackTables.rookAttacks(sq,   occ) & rq) != 0) return true;
        if ((AttackTables.bishopAttacks(sq, occ) & bq) != 0) return true;
        return false;
    }

    /** True if kingSq and sq are on the same ray through lineRef (used for pin checks). */
    private static boolean onLine(int kingSq, int from, int to) {
        return (AttackTables.LINE[kingSq][from] & Bitboard.bit(to)) != 0;
    }

    private static int[] trim(int[] buf, int count) {
        int[] result = new int[count];
        System.arraycopy(buf, 0, result, 0, count);
        return result;
    }

    /**
     * Attempt to match a UCI move string against generated legal moves.
     * Returns Move.NONE if not found.
     */
    public static int matchUci(Board board, String uci) {
        if (uci == null || uci.length() < 4) return Move.NONE;
        int from = Sq.parse(uci.substring(0, 2));
        int to   = Sq.parse(uci.substring(2, 4));
        if (from == Sq.NONE || to == Sq.NONE) return Move.NONE;
        char promoChar = uci.length() >= 5 ? uci.charAt(4) : 0;

        for (int move : board.generateLegalMoves()) {
            if (Move.from(move) != from || Move.to(move) != to) continue;
            if (promoChar != 0) {
                if (!Move.isPromotion(move)) continue;
                int pt = Move.promoType(move);
                char expected = switch (pt) {
                    case Piece.KNIGHT -> 'n';
                    case Piece.BISHOP -> 'b';
                    case Piece.ROOK   -> 'r';
                    default           -> 'q';
                };
                if (expected != promoChar) continue;
            }
            return move;
        }
        return Move.NONE;
    }
}
