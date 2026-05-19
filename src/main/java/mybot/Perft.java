package mybot;

/**
 * Perft test driver — validates move generation correctness against known node counts.
 * Not included in the production JAR (run directly via javac).
 */
public class Perft {

    public static long perft(Board board, int depth) {
        if (depth == 0) return 1L;
        int[] moves = board.generateLegalMoves();
        if (depth == 1) return moves.length;
        long nodes = 0;
        for (int move : moves) {
            board.makeMove(move);
            nodes += perft(board, depth - 1);
            board.unmakeMove(move);
        }
        return nodes;
    }

    public static void runSuite() {
        System.out.println("=== Perft Validation Suite ===\n");

        test("Startpos",
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            new long[]{20, 400, 8902, 197281, 4865609});

        test("Kiwipete",
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            new long[]{48, 2039, 97862, 4085603});

        test("Position 3",
            "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1",
            new long[]{14, 191, 2812, 43238, 674624});

        test("Position 4",
            "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1",
            new long[]{6, 264, 9467, 422333});

        test("Position 5",
            "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8",
            new long[]{44, 1486, 62379, 2103487});

        test("Position 6",
            "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10",
            new long[]{46, 2079, 89890, 3894594});
    }

    private static void test(String name, String fen, long[] expected) {
        Board board = new Board(fen);
        System.out.println("── " + name + " ──");
        System.out.println("FEN: " + fen);
        boolean allPass = true;
        for (int d = 1; d <= expected.length; d++) {
            long start = System.currentTimeMillis();
            long got = perft(board, d);
            long ms  = System.currentTimeMillis() - start;
            boolean pass = got == expected[d - 1];
            if (!pass) allPass = false;
            System.out.printf("  depth %d: %,d  expected %,d  %s  (%d ms)%n",
                d, got, expected[d - 1], pass ? "PASS" : "FAIL", ms);
        }
        System.out.println(allPass ? "  RESULT: ALL PASS\n" : "  RESULT: FAILURES DETECTED\n");
    }

    public static void main(String[] args) {
        runSuite();
    }
}
