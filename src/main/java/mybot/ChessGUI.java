package mybot;

import com.github.bhlangonijr.chesslib.*;
import com.github.bhlangonijr.chesslib.move.Move;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
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

    private Square selectedSquare = null;
    private List<Move> legalMovesFromSelected = null;

    // Time tracking
    private long whiteTimeRemainingMs;
    private long blackTimeRemainingMs;
    private long lastMoveTimeMs;
    private Timer clockTimer;

    public ChessGUI(GameSettings settings) {
        this.settings = settings;
        this.engine = new ChessEngine();

        // Initialize time controls
        if (settings.getGameMode() == GameSettings.GameMode.TIMED) {
            whiteTimeRemainingMs = settings.getTimePerPlayerMs();
            blackTimeRemainingMs = settings.getTimePerPlayerMs();
            lastMoveTimeMs = System.currentTimeMillis();
        }

        initializeComponents();
        updateBoard();

        // Start clock if timed game
        if (settings.getGameMode() == GameSettings.GameMode.TIMED) {
            startClock();
        }

        // If computer plays white, make first move
        if (settings.getWhitePlayer() == GameSettings.PlayerType.COMPUTER) {
            SwingUtilities.invokeLater(this::makeComputerMove);
        }
    }

    private void initializeComponents() {
        setTitle("Chess Game - " + settings.toString());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        setResizable(false);

        // Top panel with time displays (if timed)
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

        // Board panel
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

                // Color the squares
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

        // Status panel
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
        Board board = engine.board;

        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                // Convert to chess coordinates (rank 0 = row 7, file 0 = col 0)
                int rank = 7 - row;
                int file = col;
                Square sq = Square.valueOf("ABCDEFGH".charAt(file) + String.valueOf(rank + 1));

                JButton button = squares[row][col];
                Piece piece = board.getPiece(sq);

                // Set piece text
                button.setText(getPieceSymbol(piece));

                // Reset background color
                boolean isLight = (row + col) % 2 == 0;
                button.setBackground(isLight ? LIGHT_SQUARE : DARK_SQUARE);

                // Highlight selected square
                if (sq.equals(selectedSquare)) {
                    button.setBackground(SELECTED_COLOR);
                }

                // Highlight legal move destinations
                if (legalMovesFromSelected != null) {
                    for (Move move : legalMovesFromSelected) {
                        if (move.getTo().equals(sq)) {
                            button.setBackground(HIGHLIGHT_COLOR);
                        }
                    }
                }
            }
        }

        // Update status
        updateStatus();
    }

    private String getPieceSymbol(Piece piece) {
        if (piece == null || piece == Piece.NONE) {
            return "";
        }

        String symbol = switch (piece.getPieceType()) {
            case PAWN -> "♟";
            case KNIGHT -> "♞";
            case BISHOP -> "♝";
            case ROOK -> "♜";
            case QUEEN -> "♛";
            case KING -> "♚";
            default -> "";
        };

        // Use white pieces for white, filled (black) pieces for black
        if (piece.getPieceSide() == Side.WHITE) {
            symbol = switch (piece.getPieceType()) {
                case PAWN -> "♙";
                case KNIGHT -> "♘";
                case BISHOP -> "♗";
                case ROOK -> "♖";
                case QUEEN -> "♕";
                case KING -> "♔";
                default -> "";
            };
        }

        return symbol;
    }

    private void handleSquareClick(int row, int col) {
        // Convert to chess square
        int rank = 7 - row;
        int file = col;
        Square clickedSquare = Square.valueOf("ABCDEFGH".charAt(file) + String.valueOf(rank + 1));

        Side currentSide = engine.board.getSideToMove();
        boolean isHumanTurn = (currentSide == Side.WHITE && settings.getWhitePlayer() == GameSettings.PlayerType.HUMAN) ||
                             (currentSide == Side.BLACK && settings.getBlackPlayer() == GameSettings.PlayerType.HUMAN);

        if (!isHumanTurn) {
            return; // Computer's turn, ignore clicks
        }

        if (engine.board.isMated() || engine.board.isDraw()) {
            return; // Game over
        }

        // If no square selected, select this square if it has a piece of current side
        if (selectedSquare == null) {
            Piece piece = engine.board.getPiece(clickedSquare);
            if (piece != null && piece != Piece.NONE && piece.getPieceSide() == currentSide) {
                selectedSquare = clickedSquare;
                legalMovesFromSelected = engine.board.legalMoves().stream()
                    .filter(m -> m.getFrom().equals(clickedSquare))
                    .toList();
                updateBoard();
            }
        } else {
            // Check if clicked square is a legal move destination
            Move moveToMake = null;
            for (Move move : legalMovesFromSelected) {
                if (move.getTo().equals(clickedSquare)) {
                    // Check for pawn promotion
                    if (isPawnPromotion(move)) {
                        Piece promotion = promptForPromotion(currentSide);
                        moveToMake = new Move(move.getFrom(), move.getTo(), promotion);
                    } else {
                        moveToMake = move;
                    }
                    break;
                }
            }

            if (moveToMake != null) {
                // Make the move
                makeMove(moveToMake);
                selectedSquare = null;
                legalMovesFromSelected = null;
                updateBoard();

                // If next player is computer, make their move
                Side nextSide = engine.board.getSideToMove();
                boolean isNextComputerTurn = (nextSide == Side.WHITE && settings.getWhitePlayer() == GameSettings.PlayerType.COMPUTER) ||
                                            (nextSide == Side.BLACK && settings.getBlackPlayer() == GameSettings.PlayerType.COMPUTER);
                if (isNextComputerTurn && !engine.board.isMated() && !engine.board.isDraw()) {
                    SwingUtilities.invokeLater(this::makeComputerMove);
                }
            } else {
                // Deselect or select new piece
                Piece piece = engine.board.getPiece(clickedSquare);
                if (piece != null && piece != Piece.NONE && piece.getPieceSide() == currentSide) {
                    selectedSquare = clickedSquare;
                    legalMovesFromSelected = engine.board.legalMoves().stream()
                        .filter(m -> m.getFrom().equals(clickedSquare))
                        .toList();
                } else {
                    selectedSquare = null;
                    legalMovesFromSelected = null;
                }
                updateBoard();
            }
        }
    }

    private boolean isPawnPromotion(Move move) {
        Piece piece = engine.board.getPiece(move.getFrom());
        if (piece == null || piece.getPieceType() != PieceType.PAWN) {
            return false;
        }

        int toRank = move.getTo().getRank().ordinal();
        return toRank == 7 || toRank == 0;
    }

    private Piece promptForPromotion(Side side) {
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

        PieceType type = switch (choice) {
            case 1 -> PieceType.ROOK;
            case 2 -> PieceType.BISHOP;
            case 3 -> PieceType.KNIGHT;
            default -> PieceType.QUEEN;
        };

        return Piece.make(side, type);
    }

    private void makeMove(Move move) {
        Piece movingPiece = engine.board.getPiece(move.getFrom());
        Piece capturedPiece = engine.board.getPiece(move.getTo());

        engine.board.doMove(move);
        engine.pieceTracker.applyMove(move, movingPiece, capturedPiece, engine.board);

        // Update time control
        if (settings.getGameMode() == GameSettings.GameMode.TIMED) {
            updateTimeAfterMove();
        }
    }

    private void makeComputerMove() {
        // Disable board during computation
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        SwingWorker<Move, Void> worker = new SwingWorker<>() {
            @Override
            protected Move doInBackground() {
                return engine.selectBestMove();
            }

            @Override
            protected void done() {
                try {
                    Move bestMove = get();
                    if (bestMove != null) {
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
        if (engine.board.isMated()) {
            Side loser = engine.board.getSideToMove();
            statusLabel.setText("Checkmate! " + (loser == Side.WHITE ? "Black" : "White") + " wins!");
            stopClock();
        } else if (engine.board.isDraw()) {
            statusLabel.setText("Game drawn!");
            stopClock();
        } else if (engine.board.isKingAttacked()) {
            statusLabel.setText(engine.board.getSideToMove() + " is in check!");
        } else {
            statusLabel.setText(engine.board.getSideToMove() + " to move");
        }
    }

    private void startClock() {
        clockTimer = new Timer(100, e -> updateClock());
        clockTimer.start();
    }

    private void stopClock() {
        if (clockTimer != null) {
            clockTimer.stop();
        }
    }

    private void updateClock() {
        if (engine.board.isMated() || engine.board.isDraw()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - lastMoveTimeMs;

        if (engine.board.getSideToMove() == Side.WHITE) {
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
        // Add increment to the side that just moved
        Side justMoved = engine.board.getSideToMove() == Side.WHITE ? Side.BLACK : Side.WHITE;
        if (justMoved == Side.WHITE) {
            whiteTimeRemainingMs += settings.getIncrementMs();
        } else {
            blackTimeRemainingMs += settings.getIncrementMs();
        }

        lastMoveTimeMs = System.currentTimeMillis();
        updateTimeLabels();
    }

    private void updateTimeLabels() {
        if (whiteTimeLabel != null) {
            whiteTimeLabel.setText("White: " + formatTime(whiteTimeRemainingMs));
            whiteTimeLabel.setBackground(engine.board.getSideToMove() == Side.WHITE ? Color.YELLOW : Color.WHITE);
        }
        if (blackTimeLabel != null) {
            blackTimeLabel.setText("Black: " + formatTime(blackTimeRemainingMs));
            blackTimeLabel.setBackground(engine.board.getSideToMove() == Side.BLACK ? Color.YELLOW : Color.LIGHT_GRAY);
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
