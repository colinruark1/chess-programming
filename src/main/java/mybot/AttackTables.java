package mybot;

public final class AttackTables {

    // ── Leaper attack tables (precomputed, occupancy-independent) ──
    public static final long[] KNIGHT_ATTACKS = new long[64];
    public static final long[] KING_ATTACKS   = new long[64];
    public static final long[] PAWN_ATTACKS_W = new long[64];
    public static final long[] PAWN_ATTACKS_B = new long[64];

    // ── Slider occupancy masks (relevant blockers only, edges excluded) ──
    public static final long[] ROOK_MASKS   = new long[64];
    public static final long[] BISHOP_MASKS = new long[64];

    // ── Magic numbers (initial guesses; runtime-validated and replaced if they collide) ──
    static long[] ROOK_MAGICS = {
        0xa8002c000108020L,  0x6c00049b0002001L,  0x100200010090040L,  0x2480041000800801L,
        0x280028004000800L,  0x900410008040022L,  0x280020001001080L,  0x2880002041000080L,
        0xa000800080400034L, 0x4808020004000L,    0x2290802004801000L, 0x411000d00100020L,
        0x402800800040080L,  0xb000401004208L,    0x2409000100040200L, 0x1002100004082L,
        0x22878001e24000L,   0x1090810021004010L, 0x801030040200012L,  0x500808008001000L,
        0xa08018014000880L,  0x8000808004000200L, 0x201008080010200L,  0x801020000441091L,
        0x800080204005L,     0x1040200040100048L, 0x120200402082L,     0xd14880480100080L,
        0x12040280080080L,   0x100040080020080L,  0x9020010080800200L, 0x813241200148449L,
        0x491604001800080L,  0x100401000402001L,  0x4820010021001040L, 0x400402202000812L,
        0x209009005000802L,  0x810800601800400L,  0x4301083214000150L, 0x204026458e001401L,
        0x40204000808000L,   0x8001008040010020L, 0x8410820820420010L, 0x1003001000090020L,
        0x804040008008080L,  0x12000810020004L,   0x1000100200040208L, 0x430000a044020001L,
        0x280009023410300L,  0xe0100040002240L,   0x200100401700L,     0x2244100408008080L,
        0x8000400801980L,    0x2000810040200L,    0x8010100228810400L, 0x2000009044210200L,
        0x4080008040102101L, 0x40002080411d01L,   0x2005524060000901L, 0x502001008400422L,
        0x489a000810200402L, 0x1004400080a13L,    0x4000011008020084L, 0x26002114058042L
    };

    static long[] BISHOP_MAGICS = {
        0x89a1121896040240L, 0x2004844802002010L, 0x2068080051921000L, 0x62880a0220200808L,
        0x4042004000000L,    0x100822020200011L,  0xc00444222012000aL, 0x28808801216001L,
        0x400492088408100L,  0x201c401040c0084L,  0x840800910a0010L,   0x82080240060L,
        0x2000840504006000L, 0x30010c4108405004L, 0x1008005410080802L, 0x8144042209100900L,
        0x208081020014400L,  0x4800201208ca00L,   0xf18140408012008L,  0x1004002802102001L,
        0x841000820080811L,  0x40200200a42008L,   0x800054042000L,     0x88010400410c9000L,
        0x520040470104290L,  0x1004040051500081L, 0x2002081833080021L, 0x400c00c010142L,
        0x941408200c002000L, 0x658810000806011L,  0x188071040440a00L,  0x4800404002011c00L,
        0x104442040404200L,  0x511080202091021L,  0x4022401120400L,    0x80c0040400080120L,
        0x8040010040820802L, 0x480810700020090L,  0x102008e00040242L,  0x809005202050100L,
        0x8002024220104080L, 0x431008804142000L,  0x19001802081400L,   0x200014208040080L,
        0x3308082008200100L, 0x41010500040c020L,  0x4012020c04210308L, 0x208220a202004080L,
        0x111040120082000L,  0x6803040141280a00L, 0x2101004f08008010L, 0x8208839611822000L,
        0x802073040a48a800L, 0x1032006008c00b00L, 0x601090952022d000L, 0x40180410900098L,
        0x2000400082060000L, 0x8410411900448L,    0x308080208100400L,  0x20010040040040L,
        0x1002060082009010L, 0x100080c408020L,    0x2080090808840102L, 0x20004010100208L
    };

    // Shift values: 64 - popcount(mask)
    static final int[] ROOK_SHIFTS   = new int[64];
    static final int[] BISHOP_SHIFTS = new int[64];

    // Attack tables: [sq][magic_index] → attack bitboard
    public static final long[][] ROOK_ATTACKS   = new long[64][];
    public static final long[][] BISHOP_ATTACKS = new long[64][];

    // Between-mask table for pin/check detection: squares strictly between a and b on a ray
    public static final long[][] BETWEEN = new long[64][64];

    // Line-mask table: all squares on the same rank/file/diagonal as both a and b
    public static final long[][] LINE = new long[64][64];

    static {
        initLeapers();
        initBetween();
        initMagics();
    }

    private AttackTables() {}

    // ── Leaper initialization ──

    private static void initLeapers() {
        for (int sq = 0; sq < 64; sq++) {
            long bb = Bitboard.bit(sq);

            // Knight
            KNIGHT_ATTACKS[sq] =
                ((bb << 17) & Bitboard.NOT_FILE_A)  |
                ((bb << 15) & Bitboard.NOT_FILE_H)  |
                ((bb << 10) & Bitboard.NOT_FILE_AB) |
                ((bb << 6)  & Bitboard.NOT_FILE_GH) |
                ((bb >>> 6)  & Bitboard.NOT_FILE_AB) |
                ((bb >>> 10) & Bitboard.NOT_FILE_GH) |
                ((bb >>> 15) & Bitboard.NOT_FILE_A)  |
                ((bb >>> 17) & Bitboard.NOT_FILE_H);

            // King
            long k = bb | Bitboard.eastOne(bb) | Bitboard.westOne(bb);
            KING_ATTACKS[sq] = (k | Bitboard.northOne(k) | Bitboard.southOne(k)) & ~bb;

            // Pawn attacks
            PAWN_ATTACKS_W[sq] = Bitboard.noEaOne(bb) | Bitboard.noWeOne(bb);
            PAWN_ATTACKS_B[sq] = Bitboard.soEaOne(bb) | Bitboard.soWeOne(bb);
        }
    }

    // ── Between / Line masks ──

    private static void initBetween() {
        for (int a = 0; a < 64; a++) {
            for (int b = 0; b < 64; b++) {
                if (a == b) continue;
                int df = Sq.file(b) - Sq.file(a);
                int dr = Sq.rank(b) - Sq.rank(a);
                // Only squares sharing a rank, file, or diagonal
                if (df != 0 && dr != 0 && Math.abs(df) != Math.abs(dr)) continue;
                int sf = Integer.signum(df), sr = Integer.signum(dr);
                // BETWEEN: squares strictly between a and b along the ray
                long between = 0;
                for (int f = Sq.file(a)+sf, r = Sq.rank(a)+sr;
                     f != Sq.file(b) || r != Sq.rank(b); f += sf, r += sr) {
                    between |= Bitboard.bit(Sq.of(f, r));
                }
                BETWEEN[a][b] = between;
                // LINE: full ray through a and b in both directions
                long line = Bitboard.bit(a) | Bitboard.bit(b);
                for (int f = Sq.file(a)+sf, r = Sq.rank(a)+sr;
                     f >= 0 && f < 8 && r >= 0 && r < 8; f += sf, r += sr)
                    line |= Bitboard.bit(Sq.of(f, r));
                for (int f = Sq.file(a)-sf, r = Sq.rank(a)-sr;
                     f >= 0 && f < 8 && r >= 0 && r < 8; f -= sf, r -= sr)
                    line |= Bitboard.bit(Sq.of(f, r));
                LINE[a][b] = line;
            }
        }
    }

    // ── Magic bitboard initialization ──

    private static void initMagics() {
        // LFSR seed — deterministic but avoids 0-patterns that make magic search slow
        long seed = 0xDEADBEEFCAFEBABEL;
        for (int sq = 0; sq < 64; sq++) {
            ROOK_MASKS[sq]   = computeRookMask(sq);
            BISHOP_MASKS[sq] = computeBishopMask(sq);

            int rBits = Long.bitCount(ROOK_MASKS[sq]);
            int bBits = Long.bitCount(BISHOP_MASKS[sq]);
            ROOK_SHIFTS[sq]   = 64 - rBits;
            BISHOP_SHIFTS[sq] = 64 - bBits;

            ROOK_ATTACKS[sq]   = new long[1 << rBits];
            BISHOP_ATTACKS[sq] = new long[1 << bBits];

            // Validate hardcoded rook magic; find a replacement if it collides destructively
            if (!fillTable(sq, ROOK_MASKS[sq], ROOK_SHIFTS[sq], ROOK_MAGICS[sq], ROOK_ATTACKS[sq], false)) {
                seed = findMagic(sq, ROOK_MASKS[sq], ROOK_SHIFTS[sq], ROOK_ATTACKS[sq], false, seed);
            }

            // Validate hardcoded bishop magic; find a replacement if it collides destructively
            if (!fillTable(sq, BISHOP_MASKS[sq], BISHOP_SHIFTS[sq], BISHOP_MAGICS[sq], BISHOP_ATTACKS[sq], true)) {
                seed = findMagic(sq, BISHOP_MASKS[sq], BISHOP_SHIFTS[sq], BISHOP_ATTACKS[sq], true, seed);
            }
        }
    }

    /**
     * Attempt to fill the attacks table using the given magic.
     * Returns true if successful (no destructive collisions).
     * On failure, the table may be partially filled; caller should retry with a new magic.
     */
    private static boolean fillTable(int sq, long mask, int shift, long magic, long[] table, boolean bishop) {
        java.util.Arrays.fill(table, 0L);
        long occ = 0;
        do {
            int idx = (int)((occ * magic) >>> shift);
            long atk = bishop ? computeBishopAttacksClassical(sq, occ)
                               : computeRookAttacksClassical(sq, occ);
            if (table[idx] != 0L && table[idx] != atk) return false; // destructive collision
            table[idx] = atk;
            occ = (occ - mask) & mask;
        } while (occ != 0);
        return true;
    }

    /**
     * Find a valid magic for this square by random search (LFSR-based, deterministic).
     * Fills the table with the found magic and returns the updated seed.
     */
    private static long findMagic(int sq, long mask, int shift, long[] table, boolean bishop, long seed) {
        while (true) {
            // Xorshift64 LFSR — few-bit candidates
            seed ^= seed << 13; seed ^= seed >>> 7; seed ^= seed << 17;
            long s2 = seed; s2 ^= s2 << 13; s2 ^= s2 >>> 7; s2 ^= s2 << 17;
            long s3 = s2;   s3 ^= s3 << 13; s3 ^= s3 >>> 7; s3 ^= s3 << 17;
            long candidate = seed & s2 & s3; // AND of three → sparse bit pattern
            if (Long.bitCount(candidate) < 6) continue; // too few bits, skip
            if (fillTable(sq, mask, shift, candidate, table, bishop)) {
                if (bishop) BISHOP_MAGICS[sq] = candidate;
                else        ROOK_MAGICS[sq]   = candidate;
                return seed;
            }
        }
    }

    // ── Public attack API ──

    public static long rookAttacks(int sq, long occupancy) {
        long occ = occupancy & ROOK_MASKS[sq];
        return ROOK_ATTACKS[sq][rookIndex(sq, occ)];
    }

    public static long bishopAttacks(int sq, long occupancy) {
        long occ = occupancy & BISHOP_MASKS[sq];
        return BISHOP_ATTACKS[sq][bishopIndex(sq, occ)];
    }

    public static long queenAttacks(int sq, long occupancy) {
        return rookAttacks(sq, occupancy) | bishopAttacks(sq, occupancy);
    }

    public static long knightAttacks(int sq) { return KNIGHT_ATTACKS[sq]; }
    public static long kingAttacks(int sq)   { return KING_ATTACKS[sq]; }

    public static long pawnAttacks(int sq, int color) {
        return color == Piece.WHITE ? PAWN_ATTACKS_W[sq] : PAWN_ATTACKS_B[sq];
    }

    // ── Magic index computation ──

    private static int rookIndex(int sq, long occ) {
        return (int)((occ * ROOK_MAGICS[sq]) >>> ROOK_SHIFTS[sq]);
    }

    private static int bishopIndex(int sq, long occ) {
        return (int)((occ * BISHOP_MAGICS[sq]) >>> BISHOP_SHIFTS[sq]);
    }

    // ── Mask computation ──

    private static long computeRookMask(int sq) {
        int file = Sq.file(sq), rank = Sq.rank(sq);
        long mask = 0;
        // North ray (exclude rank 8)
        for (int r = rank + 1; r <= 6; r++) mask |= Bitboard.bit(Sq.of(file, r));
        // South ray (exclude rank 1)
        for (int r = rank - 1; r >= 1; r--) mask |= Bitboard.bit(Sq.of(file, r));
        // East ray (exclude file H)
        for (int f = file + 1; f <= 6; f++) mask |= Bitboard.bit(Sq.of(f, rank));
        // West ray (exclude file A)
        for (int f = file - 1; f >= 1; f--) mask |= Bitboard.bit(Sq.of(f, rank));
        return mask;
    }

    private static long computeBishopMask(int sq) {
        int file = Sq.file(sq), rank = Sq.rank(sq);
        long mask = 0;
        // NE diagonal (exclude edges)
        for (int f = file + 1, r = rank + 1; f <= 6 && r <= 6; f++, r++)
            mask |= Bitboard.bit(Sq.of(f, r));
        // NW diagonal
        for (int f = file - 1, r = rank + 1; f >= 1 && r <= 6; f--, r++)
            mask |= Bitboard.bit(Sq.of(f, r));
        // SE diagonal
        for (int f = file + 1, r = rank - 1; f <= 6 && r >= 1; f++, r--)
            mask |= Bitboard.bit(Sq.of(f, r));
        // SW diagonal
        for (int f = file - 1, r = rank - 1; f >= 1 && r >= 1; f--, r--)
            mask |= Bitboard.bit(Sq.of(f, r));
        return mask;
    }

    // ── Classical (slow) attack generation — used only at init time ──

    static long computeRookAttacksClassical(int sq, long occ) {
        int file = Sq.file(sq), rank = Sq.rank(sq);
        long attacks = 0;
        for (int r = rank + 1; r <= 7; r++) { long b = Bitboard.bit(Sq.of(file, r)); attacks |= b; if ((occ & b) != 0) break; }
        for (int r = rank - 1; r >= 0; r--) { long b = Bitboard.bit(Sq.of(file, r)); attacks |= b; if ((occ & b) != 0) break; }
        for (int f = file + 1; f <= 7; f++) { long b = Bitboard.bit(Sq.of(f, rank)); attacks |= b; if ((occ & b) != 0) break; }
        for (int f = file - 1; f >= 0; f--) { long b = Bitboard.bit(Sq.of(f, rank)); attacks |= b; if ((occ & b) != 0) break; }
        return attacks;
    }

    static long computeBishopAttacksClassical(int sq, long occ) {
        int file = Sq.file(sq), rank = Sq.rank(sq);
        long attacks = 0;
        for (int f=file+1,r=rank+1; f<=7&&r<=7; f++,r++) { long b=Bitboard.bit(Sq.of(f,r)); attacks|=b; if((occ&b)!=0) break; }
        for (int f=file-1,r=rank+1; f>=0&&r<=7; f--,r++) { long b=Bitboard.bit(Sq.of(f,r)); attacks|=b; if((occ&b)!=0) break; }
        for (int f=file+1,r=rank-1; f<=7&&r>=0; f++,r--) { long b=Bitboard.bit(Sq.of(f,r)); attacks|=b; if((occ&b)!=0) break; }
        for (int f=file-1,r=rank-1; f>=0&&r>=0; f--,r--) { long b=Bitboard.bit(Sq.of(f,r)); attacks|=b; if((occ&b)!=0) break; }
        return attacks;
    }
}
