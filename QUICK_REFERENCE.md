# Quick Reference Card

## GamePhase - Smart Depth Calculation

### Simple Usage (Backward Compatible)
```java
int depth = GamePhase.getSearchDepth(absoluteMaterial);
```

### Full Featured Usage
```java
int depth = GamePhase.calculateDepth(
    board,                // Board position
    absoluteMaterial,     // Total material
    pieceCount,          // Number of pieces
    timeRemainingMs,     // Time left (0 = no limit)
    incrementMs,         // Time increment
    movesPlayed          // Moves played so far
);
```

### Custom Configuration
```java
GamePhase.DepthConfig config = new GamePhase.DepthConfig();
config.openingDepth = 5;
config.middlegameDepth = 7;
config.endgameDepth = 9;
config.timeBufferPercentage = 0.15;

int depth = GamePhase.calculateDepth(
    board, material, pieces, time, inc, moves, config
);
```

### Helper Methods
```java
// Calculate move time
long timeMs = GamePhase.calculateMoveTime(
    timeRemainingMs, incrementMs, movesPlayed, isComplex
);

// Estimate complexity (0-100)
int complexity = GamePhase.estimateComplexity(
    board, pieceCount, materialBalance
);

// Check if critical
boolean critical = GamePhase.isCriticalPosition(board, material);

// Detect phase
GamePhase.Phase phase = GamePhase.detectPhase(material);
```

## Chess GUI

### Run the GUI
```bash
pip install -r requirements.txt
python3 chess_gui.py
```

### Change Game Mode
In `chess_gui.py`:
```python
# In __init__ method
self.white_player = PlayerType.HUMAN      # or .COMPUTER
self.black_player = PlayerType.COMPUTER   # or .HUMAN
```

### Customize Colors
```python
COLOR_LIGHT_SQUARE = (240, 217, 181)
COLOR_DARK_SQUARE = (181, 136, 99)
COLOR_HIGHLIGHT = (186, 202, 68, 128)
```

### Customize Board Size
```python
SQUARE_SIZE = 80  # Pixels per square
INFO_PANEL_WIDTH = 300  # Right panel width
```

## File Locations

### Java Source
- `src/main/java/mybot/GamePhase.java` - Depth calculator
- `src/main/java/mybot/ChessEngine.java` - Main engine
- `src/main/java/mybot/PieceTracker.java` - Piece tracking

### Python GUI
- `chess_gui.py` - Main GUI application
- `requirements.txt` - Python dependencies

### Documentation
- `GAMEPHASE_USAGE.md` - Detailed GamePhase guide
- `GUI_README.md` - GUI customization guide
- `UPDATES_SUMMARY.md` - Complete changes overview
- `QUICK_REFERENCE.md` - This file

## Common Tasks

### Build the Engine
```bash
javac -cp libs/chesslib-1.2.0.jar -d build/classes src/main/java/mybot/*.java
jar cfm build/libs/my_bot.jar manifest.txt -C build/classes .
```

### Test UCI Communication
```bash
java -jar build/libs/my_bot.jar
# Then type:
uci
isready
position startpos
go movetime 2000
quit
```

### Count Pieces (for GamePhase)
```java
int pieceCount = 0;
for (Square sq : Square.values()) {
    Piece p = board.getPiece(sq);
    if (p != Piece.NONE && p.getPieceType() != PieceType.KING) {
        pieceCount++;
    }
}
```

### Parse UCI Time Control
```java
// go wtime 60000 btime 58000 winc 1000 binc 1000
long wtime = 0, btime = 0, winc = 0, binc = 0;
String[] tokens = line.split(" ");
for (int i = 0; i < tokens.length; i++) {
    switch (tokens[i]) {
        case "wtime" -> wtime = Long.parseLong(tokens[++i]);
        case "btime" -> btime = Long.parseLong(tokens[++i]);
        case "winc" -> winc = Long.parseLong(tokens[++i]);
        case "binc" -> binc = Long.parseLong(tokens[++i]);
    }
}
long timeMs = (board.getSideToMove() == Side.WHITE) ? wtime : btime;
long incMs = (board.getSideToMove() == Side.WHITE) ? winc : binc;
```

## DepthConfig Presets

### Aggressive
```java
config.openingDepth = 5;
config.middlegameDepth = 7;
config.endgameDepth = 10;
config.checkDepthBonus = 2;
```

### Balanced (Default)
```java
config.openingDepth = 4;
config.middlegameDepth = 6;
config.endgameDepth = 8;
config.checkDepthBonus = 1;
```

### Conservative
```java
config.openingDepth = 3;
config.middlegameDepth = 5;
config.endgameDepth = 7;
config.timeBufferPercentage = 0.2;
```

### Blitz
```java
config.openingDepth = 3;
config.middlegameDepth = 4;
config.endgameDepth = 5;
config.criticalTimeThreshold = 5.0;
config.adjustForComplexity = false;
```

## Troubleshooting

### GUI won't start
```bash
pip install --upgrade pygame python-chess
```

### Compilation errors
```bash
# Clean build
rm -rf build/classes/*
javac -cp libs/chesslib-1.2.0.jar -d build/classes src/main/java/mybot/*.java
```

### Engine not responding in GUI
- Check `build/libs/my_bot.jar` exists
- Run `./run_bot.sh` to rebuild
- Verify Java installed: `java -version`

## Phase Thresholds

| Phase | Material Range | Typical Pieces |
|-------|---------------|----------------|
| OPENING | ≥ 6400 | 28+ pieces |
| MIDDLEGAME | 3000-6399 | 12-27 pieces |
| ENDGAME | < 3000 | < 12 pieces |

## Time Allocation

| Time Remaining | Depth Adjustment |
|----------------|------------------|
| > 30s per move | +2 depth |
| > 10s per move | +1 depth |
| > 3s per move | base depth |
| > 1s per move | -1 depth |
| < 1s per move | -2 depth |
| < 10s total | -2 depth (critical) |

## Complexity Adjustments

| Legal Moves | Adjustment |
|-------------|------------|
| > 40 | -1 depth (too complex) |
| 20-40 | no change |
| 10-20 | +1 depth |
| < 10 | +2 depth (simple) |

## Additional Bonuses

| Condition | Bonus |
|-----------|-------|
| In check | +1 depth |
| Few pieces (< 8) | +1 depth |
| Critical position | (handled in time allocation) |

## Checkmove Function

Added to MyBot.java:
```
checkmove e2e4
# Returns: true or false
```

Usage from GUI or testing:
```bash
echo -e "position startpos\ncheckmove e2e4\nquit" | java -jar build/libs/my_bot.jar
```
