package mybot;

/**
 * Targeted diagnostics for magic bitboard validation.
 * Run via: gradle run -PmainClass=mybot.Debug
 */
public class Debug {

    public static void main(String[] args) {
        System.out.println("=== Bishop Magic Diagnostic ===\n");

        // Verify bishop mask for e7 (sq 52, file=4, rank=6)
        int sq = Sq.E7; // should be 52
        System.out.println("e7 = sq " + sq + " (expected 52)");

        long mask = AttackTables.BISHOP_MASKS[sq];
        int bits = Long.bitCount(mask);
        System.out.println("Bishop mask for e7 (" + bits + " bits):");
        System.out.print(Bitboard.pretty(mask));

        // Verify the classical attack with b4 + f6 occupied
        int b4 = Sq.B4; // file=1, rank=3 => 25
        int f6 = Sq.F6; // file=5, rank=5 => 45
        int d6 = Sq.D6; // file=3, rank=5 => 43
        int c5 = Sq.C5; // file=2, rank=4 => 34
        System.out.println("\nb4=" + b4 + " f6=" + f6 + " d6=" + d6 + " c5=" + c5);

        long occ = Bitboard.bit(b4) | Bitboard.bit(f6);
        System.out.println("\nOccupancy (b4+f6):");
        System.out.print(Bitboard.pretty(occ));

        long classical = AttackTables.computeBishopAttacksClassical(sq, occ);
        System.out.println("Classical attacks from e7 with {b4,f6} occupied:");
        System.out.print(Bitboard.pretty(classical));
        System.out.println("Includes c5: " + ((classical & Bitboard.bit(c5)) != 0));

        long maskedOcc = occ & mask;
        System.out.println("\nMasked occupancy (occ & mask):");
        System.out.print(Bitboard.pretty(maskedOcc));

        long magic = AttackTables.BISHOP_MAGICS[sq];
        int  shift = AttackTables.BISHOP_SHIFTS[sq];
        int  idx   = (int)((maskedOcc * magic) >>> shift);
        System.out.println("Magic index: " + idx + " (shift=" + shift + ")");

        long magicAttacks = AttackTables.BISHOP_ATTACKS[sq][idx];
        System.out.println("Magic-table attacks:");
        System.out.print(Bitboard.pretty(magicAttacks));
        System.out.println("Includes c5: " + ((magicAttacks & Bitboard.bit(c5)) != 0));

        // ── Collision check: verify every subset stored correctly ──
        System.out.println("\n=== Collision / correctness check for sq " + sq + " ===");
        int tableSize = 1 << bits;
        long[] stored = AttackTables.BISHOP_ATTACKS[sq];
        boolean[] filled = new boolean[tableSize];
        long[] expected = new long[tableSize];

        int errors = 0;
        long sub = 0;
        do {
            int i = (int)((sub * magic) >>> shift);
            long correct = AttackTables.computeBishopAttacksClassical(sq, sub);
            if (filled[i] && stored[i] != correct) {
                System.out.println("COLLISION ERROR at idx=" + i);
                System.out.println("  sub1=" + Long.toHexString(sub) + " -> attacks=" + Long.toHexString(correct));
                System.out.println("  but stored=" + Long.toHexString(stored[i]));
                errors++;
                if (errors > 5) break;
            }
            filled[i] = true;
            expected[i] = correct;
            sub = (sub - mask) & mask;
        } while (sub != 0);

        System.out.println(errors == 0 ? "No collisions found." : errors + " collisions found!");

        // Verify all filled indices
        int mismatches = 0;
        for (int i = 0; i < tableSize; i++) {
            if (filled[i] && stored[i] != expected[i]) {
                System.out.println("MISMATCH at idx=" + i
                    + ": stored=" + Long.toHexString(stored[i])
                    + " expected=" + Long.toHexString(expected[i]));
                mismatches++;
                if (mismatches > 5) break;
            }
        }
        System.out.println(mismatches == 0 ? "All stored values correct." : mismatches + " mismatches!");

        // ── Full suite: check all 64 squares for bishop collisions ──
        System.out.println("\n=== Full bishop collision check (all 64 squares) ===");
        int totalErrors = 0;
        for (int s = 0; s < 64; s++) {
            totalErrors += checkBishopSquare(s);
        }
        System.out.println(totalErrors == 0 ? "All bishop squares OK!" : totalErrors + " total errors.");

        System.out.println("\n=== Full rook collision check (all 64 squares) ===");
        totalErrors = 0;
        for (int s = 0; s < 64; s++) {
            totalErrors += checkRookSquare(s);
        }
        System.out.println(totalErrors == 0 ? "All rook squares OK!" : totalErrors + " total errors.");
    }

    private static int checkBishopSquare(int sq) {
        long mask  = AttackTables.BISHOP_MASKS[sq];
        long magic = AttackTables.BISHOP_MAGICS[sq];
        int  shift = AttackTables.BISHOP_SHIFTS[sq];
        long[] stored = AttackTables.BISHOP_ATTACKS[sq];
        int bits = Long.bitCount(mask);
        boolean[] seen = new boolean[1 << bits];
        int errors = 0;
        long sub = 0;
        do {
            int idx = (int)((sub * magic) >>> shift);
            long correct = AttackTables.computeBishopAttacksClassical(sq, sub);
            if (seen[idx]) {
                if (stored[idx] != correct) {
                    System.out.println("Bishop sq=" + sq + " COLLISION idx=" + idx);
                    errors++;
                }
            } else {
                seen[idx] = true;
                if (stored[idx] != correct) {
                    System.out.println("Bishop sq=" + sq + " WRONG VALUE idx=" + idx);
                    errors++;
                }
            }
            sub = (sub - mask) & mask;
        } while (sub != 0);
        return errors;
    }

    private static int checkRookSquare(int sq) {
        long mask  = AttackTables.ROOK_MASKS[sq];
        long magic = AttackTables.ROOK_MAGICS[sq];
        int  shift = AttackTables.ROOK_SHIFTS[sq];
        long[] stored = AttackTables.ROOK_ATTACKS[sq];
        int bits = Long.bitCount(mask);
        boolean[] seen = new boolean[1 << bits];
        int errors = 0;
        long sub = 0;
        do {
            int idx = (int)((sub * magic) >>> shift);
            long correct = AttackTables.computeRookAttacksClassical(sq, sub);
            if (seen[idx]) {
                if (stored[idx] != correct) {
                    System.out.println("Rook sq=" + sq + " COLLISION idx=" + idx);
                    errors++;
                }
            } else {
                seen[idx] = true;
                if (stored[idx] != correct) {
                    System.out.println("Rook sq=" + sq + " WRONG VALUE idx=" + idx);
                    errors++;
                }
            }
            sub = (sub - mask) & mask;
        } while (sub != 0);
        return errors;
    }
}
