package mybot;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.Timer;

public class ChessGUI extends JFrame {
    private static final int BOARD_SIZE = 8;
    private static final int SQUARE_SIZE = 80;
    private static final Color LIGHT_SQUARE = new Color(240, 217, 181);
    private static final Color DARK_SQUARE = new Color(181, 136, 99);
    private static final Color HIGHLIGHT_COLOR = new Color(255, 255, 0, 128);
    private static final Color SELECTED_COLOR = new Color(130, 151, 105);

    private GameSettings settings;
    private ChessEngine engine;
    private JPanel boardPanel;
    private JButton[][] squares;
    private JLabel statusLabel;
    private JLabel whiteTimeLabel;
    private JLabel blackTimeLabel;

    private int selectedSquare = Sq.NONE;
    private int[] legalMovesFromSelected = null;

    // Time tracking
    private long whiteTimeRemainingMs;
    private long blackTimeRemainingMs;
    private long lastMoveTimeMs;
    private int clockSide = Piece.WHITE; // which side's clock is running; only written on EDT
    private Timer clockTimer;

    public ChessGUI(GameSettings settings) {
        this.settings = settings;
        this.engine = new ChessEngine();

        if (settings.getGameMode() == GameSettings.GameMode.TIMED) {
            whiteTimeRemainingMs = settings.getTimePerPlayerMs();
            blackTimeRemainingMs = settings.getTimePerPlayerMs();
            lastMoveTimeMs = System.currentTimeMillis();
        }

        initializeComponents();
        updateBoard();

        if (settings.getGameMode() == GameSettings.GameMode.TIMED) {
            startClock();
        }

        if (settings.getWhitePlayer() == GameSettings.PlayerType.COMPUTER) {
            SwingUtilities.invokeLater(this::makeComputerMove);
        }
    }

    private void initializeComponents() {
        setTitle("Chess Game - " + settings.toString());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        setResizable(false);

        if (settings.getGameMode() == GameSettings.GameMode.TIMED) {
            JPanel topPanel = new JPanel(new GridLayout(1, 2, 10, 0));
            topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            whiteTimeLabel = new JLabel("White: " + formatTime(whiteTimeRemainingMs), SwingConstants.CENTER);
            whiteTimeLabel.setFont(new Font("Monospaced", Font.BOLD, 18));
            whiteTimeLabel.setOpaque(true);
            whiteTimeLabel.setBackground(Color.WHITE);

            blackTimeLabel = new JLabel("Black: " + formatTime(blackTimeRemainingMs), SwingConstants.CENTER);
            blackTimeLabel.setFont(new Font("Monospaced", Font.BOLD, 18));
            blackTimeLabel.setOpaque(true);
            blackTimeLabel.setBackground(Color.LIGHT_GRAY);

            topPanel.add(whiteTimeLabel);
            topPanel.add(blackTimeLabel);
            add(topPanel, BorderLayout.NORTH);
        }

        boardPanel = new JPanel(new GridLayout(BOARD_SIZE, BOARD_SIZE));
        boardPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        squares = new JButton[BOARD_SIZE][BOARD_SIZE];

        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                JButton square = new JButton();
                square.setPreferredSize(new Dimension(SQUARE_SIZE, SQUARE_SIZE));
                square.setFont(new Font("Serif", Font.PLAIN, 48));
                square.setFocusPainted(false);
                square.setBorderPainted(true);

                boolean isLight = (row + col) % 2 == 0;
                square.setBackground(isLight ? LIGHT_SQUARE : DARK_SQUARE);

                final int r = row;
                final int c = col;
                square.addActionListener(e -> handleSquareClick(r, c));

                squares[row][col] = square;
                boardPanel.add(square);
            }
        }

        add(boardPanel, BorderLayout.CENTER);

        JPanel statusPanel = new JPanel();
        statusPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        statusLabel = new JLabel("White to move");
        statusLabel.setFont(new Font("Arial", Font.BOLD, 16));
        statusPanel.add(statusLabel);
        add(statusPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
    }

    private void updateBoard() {
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                int rank = 7 - row;
                int file = col;
                int sq = rank * 8 + file;

                JButton button = squares[row][col];
                int piece = engine.board.pieceAt(sq);

                button.setText(getPieceSymbol(piece));

                boolean isLight = (row + col) % 2 == 0;
                button.setBackground(isLight ? LIGHT_SQUARE : DARK_SQUARE);

                if (sq == selectedSquare) {
                    button.setBackground(SELECTED_COLOR);
                }

                if (legalMovesFromSelected != null) {
                    for (int m : legalMovesFromSelected) {
                        if (Move.to(m) == sq) {
                            button.setBackground(HIGHLIGHT_COLOR);
                        }
                    }
                }
            }
        }

        updateStatus();
    }

    private String getPieceSymbol(int piece) {
        if (piece == Piece.EMPTY) return "";
        int color = Piece.color(piece);
        return switch (Piece.type(piece)) {
            case Piece.PAWN   -> color == Piece.WHITE ? "♙" : "♟";
            case Piece.KNIGHT -> color == Piece.WHITE ? "♘" : "♞";
            case Piece.BISHOP -> color == Piece.WHITE ? "♗" : "♝";
            case Piece.ROOK   -> color == Piece.WHITE ? "♖" : "♜";
            case Piece.QUEEN  -> color == Piece.WHITE ? "♕" : "♛";
            case Piece.KING   -> color == Piece.WHITE ? "♔" : "♚";
            default -> "";
        };
    }

    private void handleSquareClick(int row, int col) {
        int rank = 7 - row;
        int file = col;
        int clickedSq = rank * 8 + file;

        int currentSide = engine.board.sideToMove();
        boolean isHumanTurn =
            (currentSide == Piece.WHITE && settings.getWhitePlayer() == GameSettings.PlayerType.HUMAN) ||
            (currentSide == Piece.BLACK && settings.getBlackPlayer() == GameSettings.PlayerType.HUMAN);

        if (!isHumanTurn) return;
        if (engine.board.isMated() || engine.board.isDraw()) return;

        if (selectedSquare == Sq.NONE) {
            int piece = engine.board.pieceAt(clickedSq);
            if (piece != Piece.EMPTY && Piece.color(piece) == currentSide) {
                selectedSquare = clickedSq;
                legalMovesFromSelected = movesFromSquare(engine.board.generateLegalMoves(), clickedSq);
                updateBoard();
            }
        } else {
            // Find matching moves to the clicked square
            int[] candidates = movesToSquare(legalMovesFromSelected, clickedSq);

            if (candidates.length > 0) {
                int moveToMake;
                if (Move.isPromotion(candidates[0])) {
                    int promoType = promptForPromotionType();
                    moveToMake = findPromoMove(candidates, promoType);
                } else {
                    moveToMake = candidates[0];
                }

                if (moveToMake != Move.NONE) {
                    makeMove(moveToMake);
                    selectedSquare = Sq.NONE;
                    legalMovesFromSelected = null;
                    updateBoard();

                    int nextSide = engine.board.sideToMove();
                    boolean isNextComputerTurn =
                        (nextSide == Piece.WHITE && settings.getWhitePlayer() == GameSettings.PlayerType.COMPUTER) ||
                        (nextSide == Piece.BLACK && settings.getBlackPlayer() == GameSettings.PlayerType.COMPUTER);
                    if (isNextComputerTurn && !engine.board.isMated() && !engine.board.isDraw()) {
                        SwingUtilities.invokeLater(this::makeComputerMove);
                    }
                }
            } else {
                // Deselect or select a different piece
                int piece = engine.board.pieceAt(clickedSq);
                if (piece != Piece.EMPTY && Piece.color(piece) == currentSide) {
                    selectedSquare = clickedSq;
                    legalMovesFromSelected = movesFromSquare(engine.board.generateLegalMoves(), clickedSq);
                } else {
                    selectedSquare = Sq.NONE;
                    legalMovesFromSelected = null;
                }
                updateBoard();
            }
        }
    }

    private int[] movesFromSquare(int[] allMoves, int from) {
        int count = 0;
        for (int m : allMoves) if (Move.from(m) == from) count++;
        int[] result = new int[count];
        int i = 0;
        for (int m : allMoves) if (Move.from(m) == from) result[i++] = m;
        return result;
    }

    private int[] movesToSquare(int[] moves, int to) {
        int count = 0;
        for (int m : moves) if (Move.to(m) == to) count++;
        int[] result = new int[count];
        int i = 0;
        for (int m : moves) if (Move.to(m) == to) result[i++] = m;
        return result;
    }

    private int promptForPromotionType() {
        String[] options = {"Queen", "Rook", "Bishop", "Knight"};
        int choice = JOptionPane.showOptionDialog(
            this,
            "Choose promotion piece:",
            "Pawn Promotion",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]
        );
        return switch (choice) {
            case 1 -> Piece.ROOK;
            case 2 -> Piece.BISHOP;
            case 3 -> Piece.KNIGHT;
            default -> Piece.QUEEN;
        };
    }

    private int findPromoMove(int[] candidates, int promoType) {
        for (int m : candidates) {
            if (Move.promoType(m) == promoType) return m;
        }
        return candidates.length > 0 ? candidates[0] : Move.NONE;
    }

    private void makeMove(int move) {
        int movingPiece = engine.board.pieceAt(Move.from(move));
        int capturedPiece = engine.board.pieceAt(Move.to(move));
        int epBefore = engine.board.epSquare();

        engine.board.makeMove(move);
        engine.pieceTracker.applyMove(move, movingPiece, capturedPiece, epBefore, engine.board);

        if (settings.getGameMode() == GameSettings.GameMode.TIMED) {
            clockSide = engine.board.sideToMove(); // safe: makeMove always called on EDT
            updateTimeAfterMove();
        }
    }

    private void makeComputerMove() {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        SwingWorker<Integer, Void> worker = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() {
                if (settings.getGameMode() == GameSettings.GameMode.TIMED) {
                    return engine.selectBestMove();
                }
                GoCommand cmd = new GoCommand();
                cmd.moveTimeMs = 10000;
                return engine.selectBestMove(cmd);
            }

            @Override
            protected void done() {
                try {
                    int bestMove = get();
                    if (bestMove != Move.NONE) {
                        makeMove(bestMove);
                        updateBoard();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(ChessGUI.this,
                        "Error computing move: " + e.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                }
                setCursor(Cursor.getDefaultCursor());
            }
        };

        worker.execute();
    }

    private void updateStatus() {
        String side = engine.board.sideToMove() == Piece.WHITE ? "White" : "Black";
        String opponent = engine.board.sideToMove() == Piece.WHITE ? "Black" : "White";
        if (engine.board.isMated()) {
            statusLabel.setText("Checkmate! " + opponent + " wins!");
            stopClock();
        } else if (engine.board.isDraw()) {
            statusLabel.setText("Game drawn!");
            stopClock();
        } else if (engine.board.inCheck()) {
            statusLabel.setText(side + " is in check!");
        } else {
            statusLabel.setText(side + " to move");
        }
    }

    private void startClock() {
        clockTimer = new Timer(100, e -> updateClock());
        clockTimer.start();
    }

    private void stopClock() {
        if (clockTimer != null) clockTimer.stop();
    }

    private void updateClock() {
        if (engine.board.isMated() || engine.board.isDraw()) return;

        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - lastMoveTimeMs;

        if (clockSide == Piece.WHITE) {
            whiteTimeRemainingMs -= elapsed;
            if (whiteTimeRemainingMs <= 0) {
                whiteTimeRemainingMs = 0;
                stopClock();
                statusLabel.setText("White ran out of time! Black wins!");
                JOptionPane.showMessageDialog(this, "White ran out of time! Black wins!");
            }
        } else {
            blackTimeRemainingMs -= elapsed;
            if (blackTimeRemainingMs <= 0) {
                blackTimeRemainingMs = 0;
                stopClock();
                statusLabel.setText("Black ran out of time! White wins!");
                JOptionPane.showMessageDialog(this, "Black ran out of time! White wins!");
            }
        }

        lastMoveTimeMs = currentTime;
        updateTimeLabels();
    }

    private void updateTimeAfterMove() {
        // Add increment to the side that just moved (opposite of current side to move)
        if (engine.board.sideToMove() == Piece.WHITE) {
            blackTimeRemainingMs += settings.getIncrementMs();
        } else {
            whiteTimeRemainingMs += settings.getIncrementMs();
        }
        lastMoveTimeMs = System.currentTimeMillis();
        updateTimeLabels();
    }

    private void updateTimeLabels() {
        if (whiteTimeLabel != null) {
            whiteTimeLabel.setText("White: " + formatTime(whiteTimeRemainingMs));
            whiteTimeLabel.setBackground(engine.board.sideToMove() == Piece.WHITE ? Color.YELLOW : Color.WHITE);
        }
        if (blackTimeLabel != null) {
            blackTimeLabel.setText("Black: " + formatTime(blackTimeRemainingMs));
            blackTimeLabel.setBackground(engine.board.sideToMove() == Piece.BLACK ? Color.YELLOW : Color.LIGHT_GRAY);
        }
    }

    private String formatTime(long timeMs) {
        long totalSeconds = timeMs / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        long tenths = (timeMs % 1000) / 100;
        return String.format("%02d:%02d.%d", minutes, seconds, tenths);
    }
}
