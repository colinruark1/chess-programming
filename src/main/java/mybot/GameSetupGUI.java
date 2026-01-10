package mybot;

import javax.swing.*;
import java.awt.*;

public class GameSetupGUI extends JFrame {
    private GameSettings settings;
    private JComboBox<String> whitePlayerCombo;
    private JComboBox<String> blackPlayerCombo;
    private JComboBox<String> gameModeCombo;
    private JSpinner timeMinutesSpinner;
    private JSpinner incrementSecondsSpinner;
    private JPanel timeControlPanel;
    private boolean gameStarted = false;

    public GameSetupGUI() {
        settings = new GameSettings();
        initializeComponents();
    }

    private void initializeComponents() {
        setTitle("Chess Game Setup");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));
        setResizable(false);

        // Main panel with padding
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Title
        JLabel titleLabel = new JLabel("Chess Game Configuration");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(titleLabel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));

        // White Player Selection
        mainPanel.add(createPlayerPanel("White Player:", whitePlayerCombo = createPlayerComboBox()));
        mainPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        // Black Player Selection
        mainPanel.add(createPlayerPanel("Black Player:", blackPlayerCombo = createPlayerComboBox()));
        mainPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        // Game Mode Selection
        JPanel gameModePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel gameModeLabel = new JLabel("Game Mode:");
        gameModeLabel.setFont(new Font("Arial", Font.PLAIN, 16));
        gameModeCombo = new JComboBox<>(new String[]{"Untimed", "Timed"});
        gameModeCombo.setFont(new Font("Arial", Font.PLAIN, 14));
        gameModeCombo.addActionListener(e -> updateTimeControlVisibility());
        gameModePanel.add(gameModeLabel);
        gameModePanel.add(gameModeCombo);
        mainPanel.add(gameModePanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Time Control Panel (initially hidden)
        timeControlPanel = createTimeControlPanel();
        timeControlPanel.setVisible(false);
        mainPanel.add(timeControlPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));

        // Start Button
        JButton startButton = new JButton("Start Game");
        startButton.setFont(new Font("Arial", Font.BOLD, 16));
        startButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        startButton.setPreferredSize(new Dimension(150, 40));
        startButton.addActionListener(e -> startGame());
        mainPanel.add(startButton);

        add(mainPanel, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(null); // Center on screen
    }

    private JComboBox<String> createPlayerComboBox() {
        JComboBox<String> combo = new JComboBox<>(new String[]{"Human", "Computer"});
        combo.setFont(new Font("Arial", Font.PLAIN, 14));
        return combo;
    }

    private JPanel createPlayerPanel(String labelText, JComboBox<String> comboBox) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel label = new JLabel(labelText);
        label.setFont(new Font("Arial", Font.PLAIN, 16));
        label.setPreferredSize(new Dimension(120, 25));
        panel.add(label);
        panel.add(comboBox);
        return panel;
    }

    private JPanel createTimeControlPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            "Time Control",
            0,
            0,
            new Font("Arial", Font.BOLD, 14)
        ));

        // Time per player
        JPanel timePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel timeLabel = new JLabel("Time per player (minutes):");
        timeLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        timeMinutesSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 60, 1));
        timeMinutesSpinner.setFont(new Font("Arial", Font.PLAIN, 14));
        ((JSpinner.DefaultEditor) timeMinutesSpinner.getEditor()).getTextField().setColumns(3);
        timePanel.add(timeLabel);
        timePanel.add(timeMinutesSpinner);
        panel.add(timePanel);

        // Increment
        JPanel incrementPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel incrementLabel = new JLabel("Increment per move (seconds):");
        incrementLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        incrementSecondsSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 60, 1));
        incrementSecondsSpinner.setFont(new Font("Arial", Font.PLAIN, 14));
        ((JSpinner.DefaultEditor) incrementSecondsSpinner.getEditor()).getTextField().setColumns(3);
        incrementPanel.add(incrementLabel);
        incrementPanel.add(incrementSecondsSpinner);
        panel.add(incrementPanel);

        return panel;
    }

    private void updateTimeControlVisibility() {
        boolean isTimed = gameModeCombo.getSelectedItem().equals("Timed");
        timeControlPanel.setVisible(isTimed);
        pack(); // Resize window to fit content
    }

    private void startGame() {
        // Parse settings from UI
        settings.setWhitePlayer(
            whitePlayerCombo.getSelectedItem().equals("Human") ?
                GameSettings.PlayerType.HUMAN : GameSettings.PlayerType.COMPUTER
        );
        settings.setBlackPlayer(
            blackPlayerCombo.getSelectedItem().equals("Human") ?
                GameSettings.PlayerType.HUMAN : GameSettings.PlayerType.COMPUTER
        );
        settings.setGameMode(
            gameModeCombo.getSelectedItem().equals("Timed") ?
                GameSettings.GameMode.TIMED : GameSettings.GameMode.UNTIMED
        );

        if (settings.getGameMode() == GameSettings.GameMode.TIMED) {
            int minutes = (Integer) timeMinutesSpinner.getValue();
            int incrementSeconds = (Integer) incrementSecondsSpinner.getValue();
            settings.setTimePerPlayerMs(minutes * 60L * 1000L);
            settings.setIncrementMs(incrementSeconds * 1000L);
        }

        gameStarted = true;

        // Launch the chess GUI with these settings
        SwingUtilities.invokeLater(() -> {
            ChessGUI chessGUI = new ChessGUI(settings);
            chessGUI.setVisible(true);
        });

        // Close this setup window
        dispose();
    }

    public GameSettings getSettings() {
        return settings;
    }

    public boolean isGameStarted() {
        return gameStarted;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GameSetupGUI setupGUI = new GameSetupGUI();
            setupGUI.setVisible(true);
        });
    }
}
