package mybot;

public final class Board {

    // ── Piece bitboards: [color][pieceType] ──
    // color: Piece.WHITE=0, Piece.BLACK=1
    // type:  Piece.PAWN=1 .. Piece.KING=6  (index 0 unused)
    private final long[][] pieceBB  = new long[2][7];
    private final long[]   colorBB  = new long[2];
    private long           allPieces;

    // Mailbox: colored piece code at each square (Piece.EMPTY = 0)
    private final int[] mailbox = new int[64];

    // Position state
    private int  sideToMove;
    private int  castlingRights; // bits: 0=WK, 1=WQ, 2=BK, 3=BQ
    private int  epSquare;       // Sq.NONE when no ep target
    private int  halfMoveClock;
    private int  fullMoveNumber;
    private long zobristKey;

    // Castling right masks
    public static final int CR_WK = 1, CR_WQ = 2, CR_BK = 4, CR_BQ = 8;

    // ── Undo history ──
    private static final int MAX_PLY = 1024;
    private final long[] histZobrist      = new long[MAX_PLY];
    private final int[]  histCastling     = new int[MAX_PLY];
    private final int[]  histEpSquare     = new int[MAX_PLY];
    private final int[]  histHalfMove     = new int[MAX_PLY];
    private final int[]  histCapture      = new int[MAX_PLY]; // captured piece (Piece.EMPTY if none)
    private int histPly = 0;

    // Position keys for repetition detection
    private final long[] posHistory = new long[MAX_PLY];
    private int posHistorySize = 0;

    // ── Construction ──

    public Board() { loadStartpos(); }

    public Board(String fen) { loadFromFen(fen); }

    public static Board fromFen(String fen) { return new Board(fen); }

    // ── Public accessors ──

    public int   sideToMove()       { return sideToMove; }
    public long  zobristKey()       { return zobristKey; }
    public int   epSquare()         { return epSquare; }
    public int   castlingRights()   { return castlingRights; }
    public int   halfMoveClock()    { return halfMoveClock; }
    public int   fullMoveNumber()   { return fullMoveNumber; }
    public long  allPieces()        { return allPieces; }
    public long  colorBB(int color) { return colorBB[color]; }
    public long  pieceBB(int color, int type) { return pieceBB[color][type]; }

    public int pieceAt(int sq) { return mailbox[sq]; }

    public int kingSquare(int color) {
        return Bitboard.lsb(pieceBB[color][Piece.KING]);
    }

    // ── Check / mate / draw ──

    public boolean inCheck() {
        return isAttackedBy(kingSquare(sideToMove), sideToMove ^ 1);
    }

    public boolean isMated() {
        return inCheck() && MoveGenerator.generateLegal(this).length == 0;
    }

    public boolean isStalemate() {
        return !inCheck() && MoveGenerator.generateLegal(this).length == 0;
    }

    public boolean isDraw() {
        return isStalemate() || isFiftyMoveDraw() || isThreefoldRepetition() || isInsufficientMaterial();
    }

    private boolean isFiftyMoveDraw() { return halfMoveClock >= 100; }

    private boolean isThreefoldRepetition() {
        int count = 0;
        for (int i = posHistorySize - 1; i >= 0; i--) {
            if (posHistory[i] == zobristKey) {
                count++;
                if (count >= 2) return true;
            }
        }
        return false;
    }

    private boolean isInsufficientMaterial() {
        if ((pieceBB[0][Piece.PAWN] | pieceBB[1][Piece.PAWN] |
             pieceBB[0][Piece.ROOK] | pieceBB[1][Piece.ROOK] |
             pieceBB[0][Piece.QUEEN]| pieceBB[1][Piece.QUEEN]) != 0) return false;
        int wMinor = Long.bitCount(pieceBB[0][Piece.KNIGHT] | pieceBB[0][Piece.BISHOP]);
        int bMinor = Long.bitCount(pieceBB[1][Piece.KNIGHT] | pieceBB[1][Piece.BISHOP]);
        if (wMinor <= 1 && bMinor == 0) return true;
        if (bMinor <= 1 && wMinor == 0) return true;
        // KBvKB same color bishops
        if (wMinor == 1 && bMinor == 1 &&
            pieceBB[0][Piece.KNIGHT] == 0 && pieceBB[1][Piece.KNIGHT] == 0) {
            long wb = pieceBB[0][Piece.BISHOP], bb2 = pieceBB[1][Piece.BISHOP];
            if ((wb & Bitboard.LIGHT_SQUARES) != 0 && (bb2 & Bitboard.LIGHT_SQUARES) != 0) return true;
            if ((wb & Bitboard.DARK_SQUARES)  != 0 && (bb2 & Bitboard.DARK_SQUARES)  != 0) return true;
        }
        return false;
    }

    // ── isAttackedBy: is square sq attacked by a piece of attackerColor? ──

    public boolean isAttackedBy(int sq, int attackerColor) {
        long occ = allPieces;
        if ((AttackTables.pawnAttacks(sq, attackerColor ^ 1) & pieceBB[attackerColor][Piece.PAWN])   != 0) return true;
        if ((AttackTables.knightAttacks(sq)                  & pieceBB[attackerColor][Piece.KNIGHT]) != 0) return true;
        if ((AttackTables.kingAttacks(sq)                    & pieceBB[attackerColor][Piece.KING])   != 0) return true;
        if ((AttackTables.bishopAttacks(sq, occ) & (pieceBB[attackerColor][Piece.BISHOP] | pieceBB[attackerColor][Piece.QUEEN])) != 0) return true;
        if ((AttackTables.rookAttacks(sq, occ)   & (pieceBB[attackerColor][Piece.ROOK]   | pieceBB[attackerColor][Piece.QUEEN])) != 0) return true;
        return false;
    }

    /** All pieces of attackerColor that attack square sq, given an occupancy override. */
    public long allAttackersTo(int sq, long occ) {
        return (AttackTables.pawnAttacks(sq, Piece.WHITE) & pieceBB[Piece.BLACK][Piece.PAWN])
             | (AttackTables.pawnAttacks(sq, Piece.BLACK) & pieceBB[Piece.WHITE][Piece.PAWN])
             | (AttackTables.knightAttacks(sq) & (pieceBB[0][Piece.KNIGHT] | pieceBB[1][Piece.KNIGHT]))
             | (AttackTables.bishopAttacks(sq, occ) & (pieceBB[0][Piece.BISHOP] | pieceBB[1][Piece.BISHOP]
                    | pieceBB[0][Piece.QUEEN] | pieceBB[1][Piece.QUEEN]))
             | (AttackTables.rookAttacks(sq, occ) & (pieceBB[0][Piece.ROOK] | pieceBB[1][Piece.ROOK]
                    | pieceBB[0][Piece.QUEEN] | pieceBB[1][Piece.QUEEN]))
             | (AttackTables.kingAttacks(sq) & (pieceBB[0][Piece.KING] | pieceBB[1][Piece.KING]));
    }

    // ── Make / Unmake ──

    public void makeMove(int move) {
        int h = histPly++;
        histZobrist[h]  = zobristKey;
        histCastling[h] = castlingRights;
        histEpSquare[h] = epSquare;
        histHalfMove[h] = halfMoveClock;
        histCapture[h]  = Piece.EMPTY;

        int from  = Move.from(move);
        int to    = Move.to(move);
        int flags = Move.flags(move);
        int us    = sideToMove;
        int them  = us ^ 1;

        // Remove old ep from hash
        if (epSquare != Sq.NONE) {
            zobristKey ^= Zobrist.EP_FILE[Sq.file(epSquare)];
            epSquare = Sq.NONE;
        }

        halfMoveClock++;

        int movingPiece = mailbox[from];
        int movingType  = Piece.type(movingPiece);

        if (movingType == Piece.PAWN) halfMoveClock = 0;

        switch (flags) {
            case Move.QUIET -> movePiece(from, to);
            case Move.DOUBLE_PUSH -> {
                movePiece(from, to);
                epSquare = us == Piece.WHITE ? to - 8 : to + 8;
                zobristKey ^= Zobrist.EP_FILE[Sq.file(epSquare)];
            }
            case Move.CASTLE_K -> {
                if (us == Piece.WHITE) { movePiece(Sq.E1, Sq.G1); movePiece(Sq.H1, Sq.F1); }
                else                   { movePiece(Sq.E8, Sq.G8); movePiece(Sq.H8, Sq.F8); }
            }
            case Move.CASTLE_Q -> {
                if (us == Piece.WHITE) { movePiece(Sq.E1, Sq.C1); movePiece(Sq.A1, Sq.D1); }
                else                   { movePiece(Sq.E8, Sq.C8); movePiece(Sq.A8, Sq.D8); }
            }
            case Move.CAPTURE -> {
                int cap = mailbox[to];
                histCapture[h] = cap;
                removePiece(to);
                movePiece(from, to);
                halfMoveClock = 0;
            }
            case Move.EN_PASSANT -> {
                int capSq = us == Piece.WHITE ? to - 8 : to + 8;
                histCapture[h] = mailbox[capSq];
                removePiece(capSq);
                movePiece(from, to);
                halfMoveClock = 0;
            }
            default -> {
                // Promotions
                if (Move.isCapture(move)) {
                    int cap = mailbox[to];
                    histCapture[h] = cap;
                    removePiece(to);
                    halfMoveClock = 0;
                }
                removePiece(from);
                placePiece(to, Piece.make(us, Move.promoType(move)));
                halfMoveClock = 0;
            }
        }

        // Update castling rights
        zobristKey ^= Zobrist.CASTLING[castlingRights];
        castlingRights &= CASTLING_SPOILERS[from] & CASTLING_SPOILERS[to];
        zobristKey ^= Zobrist.CASTLING[castlingRights];

        // Flip side
        sideToMove = them;
        zobristKey ^= Zobrist.BLACK_MOVE;

        if (us == Piece.BLACK) fullMoveNumber++;

        posHistory[posHistorySize++] = zobristKey;
    }

    public void unmakeMove(int move) {
        posHistorySize--;
        int h = --histPly;

        int from  = Move.from(move);
        int to    = Move.to(move);
        int flags = Move.flags(move);
        int them  = sideToMove; // before flip — they moved
        int us    = them ^ 1;

        // Restore state
        sideToMove     = us;
        castlingRights = histCastling[h];
        epSquare       = histEpSquare[h];
        halfMoveClock  = histHalfMove[h];
        zobristKey     = histZobrist[h];
        if (us == Piece.BLACK) fullMoveNumber--;

        switch (flags) {
            case Move.QUIET, Move.DOUBLE_PUSH -> movePieceBack(to, from);
            case Move.CASTLE_K -> {
                if (us == Piece.WHITE) { movePieceBack(Sq.G1, Sq.E1); movePieceBack(Sq.F1, Sq.H1); }
                else                   { movePieceBack(Sq.G8, Sq.E8); movePieceBack(Sq.F8, Sq.H8); }
            }
            case Move.CASTLE_Q -> {
                if (us == Piece.WHITE) { movePieceBack(Sq.C1, Sq.E1); movePieceBack(Sq.D1, Sq.A1); }
                else                   { movePieceBack(Sq.C8, Sq.E8); movePieceBack(Sq.D8, Sq.A8); }
            }
            case Move.CAPTURE -> {
                movePieceBack(to, from);
                placePieceRaw(to, histCapture[h]);
            }
            case Move.EN_PASSANT -> {
                movePieceBack(to, from);
                int capSq = us == Piece.WHITE ? to - 8 : to + 8;
                placePieceRaw(capSq, histCapture[h]);
            }
            default -> {
                // Promotion: restore pawn on from, remove promoted piece
                removePieceRaw(to);
                placePieceRaw(from, Piece.make(us, Piece.PAWN));
                if (Move.isCapture(move)) placePieceRaw(to, histCapture[h]);
            }
        }
    }

    public boolean makeNullMove() {
        if (inCheck()) return false;
        int h = histPly++;
        histZobrist[h]  = zobristKey;
        histCastling[h] = castlingRights;
        histEpSquare[h] = epSquare;
        histHalfMove[h] = halfMoveClock;
        histCapture[h]  = Piece.EMPTY;

        if (epSquare != Sq.NONE) {
            zobristKey ^= Zobrist.EP_FILE[Sq.file(epSquare)];
            epSquare = Sq.NONE;
        }
        sideToMove ^= 1;
        zobristKey ^= Zobrist.BLACK_MOVE;
        halfMoveClock++;
        posHistory[posHistorySize++] = zobristKey;
        return true;
    }

    public void unmakeNullMove() {
        posHistorySize--;
        int h = --histPly;
        sideToMove     = sideToMove ^ 1;
        zobristKey     = histZobrist[h];
        castlingRights = histCastling[h];
        epSquare       = histEpSquare[h];
        halfMoveClock  = histHalfMove[h];
    }

    // ── Legal move generation (delegates to MoveGenerator) ──

    public int[] generateLegalMoves() {
        return MoveGenerator.generateLegal(this);
    }

    public int[] generateCaptures() {
        return MoveGenerator.generateCaptures(this);
    }

    // ── FEN ──

    public void loadStartpos() {
        loadFromFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
    }

    public void loadFromFen(String fen) {
        // Clear state
        for (int c = 0; c < 2; c++) {
            colorBB[c] = 0;
            for (int t = 0; t < 7; t++) pieceBB[c][t] = 0;
        }
        allPieces = 0;
        for (int i = 0; i < 64; i++) mailbox[i] = Piece.EMPTY;
        histPly = 0;
        posHistorySize = 0;
        zobristKey = 0;

        String[] parts = fen.trim().split("\\s+");
        // 1. Piece placement
        String[] ranks = parts[0].split("/");
        for (int rank = 7; rank >= 0; rank--) {
            String row = ranks[7 - rank];
            int file = 0;
            for (char c : row.toCharArray()) {
                if (Character.isDigit(c)) {
                    file += c - '0';
                } else {
                    int piece = Piece.fromChar(c);
                    placePieceRaw(Sq.of(file, rank), piece);
                    zobristKey ^= Zobrist.PIECE[piece][Sq.of(file, rank)];
                    file++;
                }
            }
        }
        // 2. Side to move
        sideToMove = (parts.length > 1 && parts[1].equals("b")) ? Piece.BLACK : Piece.WHITE;
        if (sideToMove == Piece.BLACK) zobristKey ^= Zobrist.BLACK_MOVE;
        // 3. Castling rights
        castlingRights = 0;
        if (parts.length > 2) {
            String cr = parts[2];
            if (cr.contains("K")) castlingRights |= CR_WK;
            if (cr.contains("Q")) castlingRights |= CR_WQ;
            if (cr.contains("k")) castlingRights |= CR_BK;
            if (cr.contains("q")) castlingRights |= CR_BQ;
        }
        zobristKey ^= Zobrist.CASTLING[castlingRights];
        // 4. En passant
        epSquare = Sq.NONE;
        if (parts.length > 3 && !parts[3].equals("-")) {
            epSquare = Sq.parse(parts[3]);
            if (epSquare != Sq.NONE) zobristKey ^= Zobrist.EP_FILE[Sq.file(epSquare)];
        }
        // 5. Clocks
        halfMoveClock  = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;
        fullMoveNumber = parts.length > 5 ? Integer.parseInt(parts[5]) : 1;
    }

    public String toFen() {
        StringBuilder sb = new StringBuilder();
        // Piece placement
        for (int rank = 7; rank >= 0; rank--) {
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                int piece = mailbox[Sq.of(file, rank)];
                if (piece == Piece.EMPTY) {
                    empty++;
                } else {
                    if (empty > 0) { sb.append(empty); empty = 0; }
                    sb.append(Piece.toChar(piece));
                }
            }
            if (empty > 0) sb.append(empty);
            if (rank > 0) sb.append('/');
        }
        sb.append(' ');
        sb.append(sideToMove == Piece.WHITE ? 'w' : 'b');
        sb.append(' ');
        if (castlingRights == 0) {
            sb.append('-');
        } else {
            if ((castlingRights & CR_WK) != 0) sb.append('K');
            if ((castlingRights & CR_WQ) != 0) sb.append('Q');
            if ((castlingRights & CR_BK) != 0) sb.append('k');
            if ((castlingRights & CR_BQ) != 0) sb.append('q');
        }
        sb.append(' ');
        sb.append(epSquare == Sq.NONE ? "-" : Sq.name(epSquare));
        sb.append(' ').append(halfMoveClock);
        sb.append(' ').append(fullMoveNumber);
        return sb.toString();
    }

    // ── Piece manipulation (with Zobrist updates) ──

    private void placePiece(int sq, int piece) {
        placePieceRaw(sq, piece);
        zobristKey ^= Zobrist.PIECE[piece][sq];
    }

    private void removePiece(int sq) {
        zobristKey ^= Zobrist.PIECE[mailbox[sq]][sq];
        removePieceRaw(sq);
    }

    private void movePiece(int from, int to) {
        int piece = mailbox[from];
        zobristKey ^= Zobrist.PIECE[piece][from] ^ Zobrist.PIECE[piece][to];
        removePieceRaw(from);
        placePieceRaw(to, piece);
    }

    // ── Raw piece manipulation (no Zobrist — used by undo and FEN load) ──

    private void placePieceRaw(int sq, int piece) {
        mailbox[sq] = piece;
        long bit = Bitboard.bit(sq);
        pieceBB[Piece.color(piece)][Piece.type(piece)] |= bit;
        colorBB[Piece.color(piece)] |= bit;
        allPieces |= bit;
    }

    private void removePieceRaw(int sq) {
        int piece = mailbox[sq];
        mailbox[sq] = Piece.EMPTY;
        long bit = Bitboard.bit(sq);
        pieceBB[Piece.color(piece)][Piece.type(piece)] &= ~bit;
        colorBB[Piece.color(piece)] &= ~bit;
        allPieces &= ~bit;
    }

    // movePieceBack is purely structural (unmake): no Zobrist, just mailbox/bitboard bookkeeping
    private void movePieceBack(int from, int to) {
        // Move the piece at 'from' back to 'to' (from=current position, to=original position)
        int piece = mailbox[from];
        removePieceRaw(from);
        placePieceRaw(to, piece);
    }

    // ── Castling spoiler table ──
    // Per-square mask: which castling rights survive if a piece moves from/to that square

    private static final int[] CASTLING_SPOILERS = new int[64];
    static {
        for (int i = 0; i < 64; i++) CASTLING_SPOILERS[i] = CR_WK | CR_WQ | CR_BK | CR_BQ; // all rights by default
        CASTLING_SPOILERS[Sq.E1] &= ~(CR_WK | CR_WQ);
        CASTLING_SPOILERS[Sq.H1] &= ~CR_WK;
        CASTLING_SPOILERS[Sq.A1] &= ~CR_WQ;
        CASTLING_SPOILERS[Sq.E8] &= ~(CR_BK | CR_BQ);
        CASTLING_SPOILERS[Sq.H8] &= ~CR_BK;
        CASTLING_SPOILERS[Sq.A8] &= ~CR_BQ;
    }

    // ── Debug ──

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            sb.append(rank + 1).append("  ");
            for (int file = 0; file < 8; file++) {
                int p = mailbox[Sq.of(file, rank)];
                sb.append(p == Piece.EMPTY ? '.' : Piece.toChar(p)).append(' ');
            }
            sb.append('\n');
        }
        sb.append("   a b c d e f g h\n");
        sb.append("FEN: ").append(toFen()).append('\n');
        return sb.toString();
    }
}
