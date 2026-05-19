package mybot;

import java.util.Scanner;

public class MyBot {
    public static void main(String[] args) {
        boolean uciMode = false, consoleMode = false, guiMode = false;
        for (String arg : args) {
            if (arg.equals("--uci"))     { uciMode = true; break; }
            if (arg.equals("--console")) { consoleMode = true; break; }
            if (arg.equals("--gui"))     { guiMode = true; break; }
        }

        String display = System.getenv("DISPLAY");
        boolean isHeadless = display == null || display.isEmpty()
                || "true".equals(System.getProperty("java.awt.headless"));

        if (uciMode) {
            runUciMode();
        } else if (consoleMode || (isHeadless && !guiMode)) {
            if (isHeadless && !consoleMode) System.out.println("Headless — running in console mode.");
            runConsoleMode();
        } else {
            javax.swing.SwingUtilities.invokeLater(() -> new GameSetupGUI().setVisible(true));
        }
    }

    // ─── UCI mode ─────────────────────────────────────────────────────────────

    private static void runUciMode() {
        Scanner input = new Scanner(System.in);
        ChessEngine engine = new ChessEngine();

        while (input.hasNextLine()) {
            String line = input.nextLine().trim();
            if (line.equals("uci")) {
                engine.printUciId();
            } else if (line.equals("isready")) {
                engine.printReadyOk();
            } else if (line.equals("eval")) {
                engine.printEval();
            } else if (line.equals("ucinewgame")) {
                engine.newGame();
            } else if (line.startsWith("position")) {
                engine.parsePosition(line);
            } else if (line.startsWith("go")) {
                GoCommand goCmd = parseGoCommand(line);
                int best = engine.selectBestMove(goCmd);
                System.out.println("bestmove " + Move.toUci(best));
            } else if (line.startsWith("checkmove")) {
                String[] parts = line.split("\\s+");
                System.out.println(parts.length >= 2 && engine.isLegalMove(parts[1]));
            } else if (line.equals("manual")) {
                engine.runManualMode(input);
            } else if (line.equals("quit")) {
                break;
            }
        }
        input.close();
    }

    private static GoCommand parseGoCommand(String line) {
        GoCommand cmd = new GoCommand();
        String[] tokens = line.split("\\s+");
        for (int i = 1; i < tokens.length; i++) {
            switch (tokens[i]) {
                case "wtime"    -> { if (i + 1 < tokens.length) cmd.whiteTimeMs     = Long.parseLong(tokens[++i]); }
                case "btime"    -> { if (i + 1 < tokens.length) cmd.blackTimeMs     = Long.parseLong(tokens[++i]); }
                case "winc"     -> { if (i + 1 < tokens.length) cmd.whiteIncrementMs= Long.parseLong(tokens[++i]); }
                case "binc"     -> { if (i + 1 < tokens.length) cmd.blackIncrementMs= Long.parseLong(tokens[++i]); }
                case "movetime" -> { if (i + 1 < tokens.length) cmd.moveTimeMs      = Long.parseLong(tokens[++i]); }
                case "depth"    -> { if (i + 1 < tokens.length) cmd.maxDepth        = Integer.parseInt(tokens[++i]); }
                case "infinite" -> cmd.infinite = true;
            }
        }
        return cmd;
    }

    // ─── Console mode ─────────────────────────────────────────────────────────

    private static void runConsoleMode() {
        Scanner input = new Scanner(System.in);
        ChessEngine engine = new ChessEngine();

        System.out.println("=== CHESS - CONSOLE MODE ===");
        System.out.println("1. Play against computer");
        System.out.println("2. Computer vs Computer");
        System.out.println("3. Exit");
        System.out.print("Choose (1-3): ");

        String choice = input.nextLine().trim();
        if (choice.equals("1")) {
            engine.runManualMode(input);
        } else if (choice.equals("2")) {
            runComputerVsComputer(engine);
        }
        input.close();
    }

    private static void runComputerVsComputer(ChessEngine engine) {
        System.out.println("\n=== Computer vs Computer ===\n");
        int moveCount = 0;
        while (!engine.board.isMated() && !engine.board.isDraw()) {
            moveCount++;
            String side = engine.board.sideToMove() == Piece.WHITE ? "White" : "Black";
            System.out.println("Move " + moveCount + " (" + side + "):");

            int best = engine.selectBestMove();
            if (best == Move.NONE) { System.out.println("No legal moves!"); break; }

            System.out.println("Computer plays: " + Move.toUci(best));
            int movingPiece   = engine.board.pieceAt(Move.from(best));
            int capturedPiece = engine.board.pieceAt(Move.to(best));
            int epBefore      = engine.board.epSquare();
            engine.board.makeMove(best);
            engine.pieceTracker.applyMove(best, movingPiece, capturedPiece, epBefore, engine.board);
            System.out.println("FEN: " + engine.board.toFen() + "\n");
        }
        System.out.println(engine.board.isMated() ? "Checkmate!" : "Draw.");
    }
}
