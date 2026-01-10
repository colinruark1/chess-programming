/* MyBot.java */
package mybot;

import com.github.bhlangonijr.chesslib.move.Move;
import java.util.Scanner;

public class MyBot {
    public static void main(String[] args) {
        // Check if running in UCI mode (command-line argument --uci)
        boolean uciMode = false;
        boolean consoleMode = false;
        boolean guiMode = false;

        for (String arg : args) {
            if (arg.equals("--uci")) {
                uciMode = true;
                break;
            } else if (arg.equals("--console")) {
                consoleMode = true;
                break;
            } else if (arg.equals("--gui")) {
                guiMode = true;
                break;
            }
        }

        // Check if we're in a headless environment (before loading any AWT classes)
        String display = System.getenv("DISPLAY");
        String headlessProp = System.getProperty("java.awt.headless");
        boolean isHeadless = display == null || display.isEmpty() || "true".equals(headlessProp);

        if (uciMode) {
            // Run in UCI mode for chess engine protocol
            runUciMode();
        } else if (consoleMode || (isHeadless && !guiMode)) {
            // Run in console mode if explicitly requested or if no GUI available
            if (isHeadless && !consoleMode) {
                System.out.println("No GUI available (headless environment).");
                System.out.println("Running in console mode.");
                System.out.println("Use --gui flag to attempt GUI mode anyway.");
                System.out.println();
            }
            runConsoleMode();
        } else {
            // Launch GUI
            launchGUI();
        }
    }

    private static void launchGUI() {
        // Import Swing classes only when needed
        javax.swing.SwingUtilities.invokeLater(() -> {
            GameSetupGUI setupGUI = new GameSetupGUI();
            setupGUI.setVisible(true);
        });
    }

    /**
     * Parse UCI "go" command parameters.
     * Supports: wtime, btime, winc, binc, movetime, depth, infinite
     *
     * @param line the UCI go command line
     * @return GoCommand object with parsed parameters
     */
    private static GoCommand parseGoCommand(String line) {
        GoCommand cmd = new GoCommand();
        String[] tokens = line.split("\\s+");

        for (int i = 1; i < tokens.length; i++) {
            switch (tokens[i]) {
                case "wtime":
                    if (i + 1 < tokens.length) {
                        cmd.whiteTimeMs = Long.parseLong(tokens[++i]);
                    }
                    break;
                case "btime":
                    if (i + 1 < tokens.length) {
                        cmd.blackTimeMs = Long.parseLong(tokens[++i]);
                    }
                    break;
                case "winc":
                    if (i + 1 < tokens.length) {
                        cmd.whiteIncrementMs = Long.parseLong(tokens[++i]);
                    }
                    break;
                case "binc":
                    if (i + 1 < tokens.length) {
                        cmd.blackIncrementMs = Long.parseLong(tokens[++i]);
                    }
                    break;
                case "movetime":
                    if (i + 1 < tokens.length) {
                        cmd.moveTimeMs = Long.parseLong(tokens[++i]);
                    }
                    break;
                case "depth":
                    if (i + 1 < tokens.length) {
                        cmd.maxDepth = Integer.parseInt(tokens[++i]);
                    }
                    break;
                case "infinite":
                    cmd.infinite = true;
                    break;
            }
        }

        return cmd;
    }

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
                Move best = engine.selectBestMove(goCmd);
                System.out.println("bestmove " + best);
                //engine.printSearchInfo();
            } else if (line.startsWith("checkmove")) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 2) {
                    String moveStr = parts[1];
                    boolean isLegal = engine.isLegalMove(moveStr);
                    System.out.println(isLegal);
                } else {
                    System.out.println("false");
                }
            } else if (line.equals("manual")) {
                engine.runManualMode(input);
            } else if (line.equals("quit")) {
                break;
            }
        }
        input.close();
    }

    private static void runConsoleMode() {
        Scanner input = new Scanner(System.in);
        ChessEngine engine = new ChessEngine();

        System.out.println("===================================");
        System.out.println("   CHESS GAME - CONSOLE MODE");
        System.out.println("===================================");
        System.out.println();
        System.out.println("Options:");
        System.out.println("1. Play against computer");
        System.out.println("2. Computer vs Computer");
        System.out.println("3. Exit");
        System.out.println();
        System.out.print("Choose an option (1-3): ");

        String choice = input.nextLine().trim();

        if (choice.equals("3")) {
            System.out.println("Goodbye!");
            input.close();
            return;
        }

        if (choice.equals("1")) {
            // Human vs Computer
            engine.runManualMode(input);
        } else if (choice.equals("2")) {
            // Computer vs Computer
            runComputerVsComputer(engine);
        } else {
            System.out.println("Invalid choice. Exiting.");
        }

        input.close();
    }

    private static void runComputerVsComputer(ChessEngine engine) {
        System.out.println("\n=== Computer vs Computer ===\n");

        int moveCount = 0;
        while (!engine.board.isMated() && !engine.board.isDraw()) {
            moveCount++;
            System.out.println("Move " + moveCount + " (" + engine.board.getSideToMove() + "):");

            Move bestMove = engine.selectBestMove();
            if (bestMove == null) {
                System.out.println("No legal moves!");
                break;
            }

            System.out.println("Computer plays: " + bestMove);

            // Apply the move
            com.github.bhlangonijr.chesslib.Piece movingPiece = engine.board.getPiece(bestMove.getFrom());
            com.github.bhlangonijr.chesslib.Piece capturedPiece = engine.board.getPiece(bestMove.getTo());
            engine.board.doMove(bestMove);
            engine.pieceTracker.applyMove(bestMove, movingPiece, capturedPiece, engine.board);

            System.out.println("FEN: " + engine.board.getFen());
            System.out.println();

            // Clear TT to avoid stale positions
            // (commented out to keep search data between moves for better play)
            // engine.transpositionTable.clear();
        }

        if (engine.board.isMated()) {
            com.github.bhlangonijr.chesslib.Side loser = engine.board.getSideToMove();
            System.out.println("Checkmate! " + (loser == com.github.bhlangonijr.chesslib.Side.WHITE ? "Black" : "White") + " wins!");
        } else {
            System.out.println("Game drawn.");
        }
    }
}