package mybot;

public class GameSettings {
    public enum PlayerType {
        HUMAN,
        COMPUTER
    }

    public enum GameMode {
        TIMED,
        UNTIMED
    }

    private PlayerType whitePlayer;
    private PlayerType blackPlayer;
    private GameMode gameMode;

    // Time control settings (only used if gameMode == TIMED)
    private long timePerPlayerMs;  // Total time per player in milliseconds
    private long incrementMs;       // Increment per move in milliseconds

    public GameSettings() {
        // Default settings
        this.whitePlayer = PlayerType.HUMAN;
        this.blackPlayer = PlayerType.COMPUTER;
        this.gameMode = GameMode.UNTIMED;
        this.timePerPlayerMs = 300000;  // 5 minutes default
        this.incrementMs = 0;
    }

    public PlayerType getWhitePlayer() {
        return whitePlayer;
    }

    public void setWhitePlayer(PlayerType whitePlayer) {
        this.whitePlayer = whitePlayer;
    }

    public PlayerType getBlackPlayer() {
        return blackPlayer;
    }

    public void setBlackPlayer(PlayerType blackPlayer) {
        this.blackPlayer = blackPlayer;
    }

    public GameMode getGameMode() {
        return gameMode;
    }

    public void setGameMode(GameMode gameMode) {
        this.gameMode = gameMode;
    }

    public long getTimePerPlayerMs() {
        return timePerPlayerMs;
    }

    public void setTimePerPlayerMs(long timePerPlayerMs) {
        this.timePerPlayerMs = timePerPlayerMs;
    }

    public long getIncrementMs() {
        return incrementMs;
    }

    public void setIncrementMs(long incrementMs) {
        this.incrementMs = incrementMs;
    }

    @Override
    public String toString() {
        return String.format("GameSettings[White: %s, Black: %s, Mode: %s%s]",
            whitePlayer, blackPlayer, gameMode,
            gameMode == GameMode.TIMED ?
                String.format(", Time: %d min + %d sec increment",
                    timePerPlayerMs / 60000, incrementMs / 1000) : "");
    }
}
