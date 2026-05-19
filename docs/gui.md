# Chess GUI

The project includes two graphical interfaces: a **Python/PyGame GUI** (`chess_gui.py`) and a **Java Swing GUI** (built into the JAR). Both communicate with the engine via the UCI protocol.

## Python GUI (Recommended)

`chess_gui.py` — a PyGame-based interface with full game configuration, piece images, and a debug panel.

### Requirements

- Python 3.7+
- `pygame`
- `python-chess`

```bash
pip install pygame python-chess
# or
pip install -r requirements.txt
```

### Running

```bash
python3 chess_gui.py
```

The engine JAR must be built first (see [README.md](../README.md#building)).

### Features

- **Setup screen**: configure player types (Human/Computer) and time controls before each game
- **Time clocks**: real-time countdown in MM:SS.d format; increment applied after each move
- **Click-to-move**: click a piece to see highlighted legal moves, click a destination to move
- **Board flip**: rotate view 180°
- **Undo move**: take back the last move
- **Debug panel**: shows engine UCI output in real time
- **Piece images**: loaded from `pieces/` directory; falls back to colored circles if not found

### Game Modes

| Mode | Description |
|------|-------------|
| Human vs Human | Both players on same computer |
| Human vs Computer | You play, engine responds |
| Computer vs Human | Engine plays White, you play Black |
| Computer vs Computer | Watch engine play itself |

### Piece Images

Place PNG files in `pieces/` named:
```
white-pawn.png    black-pawn.png
white-knight.png  black-knight.png
white-bishop.png  black-bishop.png
white-rook.png    black-rook.png
white-queen.png   black-queen.png
white-king.png    black-king.png
```

Free piece sets are widely available online.

### Customization

```python
# Colors
COLOR_LIGHT_SQUARE = (240, 217, 181)
COLOR_DARK_SQUARE  = (181, 136, 99)
COLOR_HIGHLIGHT    = (186, 202, 68, 128)

# Board size
SQUARE_SIZE       = 80   # pixels per square
INFO_PANEL_WIDTH  = 300  # right panel width
```

### Troubleshooting

| Error | Fix |
|-------|-----|
| `build/libs/my_bot.jar not found` | Build the engine: `./run_bot.sh` |
| `No module named 'pygame'` | `pip install --upgrade pygame python-chess` |
| No display in WSL | Install VcXsrv/Xming, then `export DISPLAY=:0` |

---

## Java Swing GUI

Built into the main JAR. Launched by default when no arguments are passed.

### Running

```bash
./run_gui.sh
# or
java -jar build/libs/my_bot.jar
```

### Command-Line Flags

| Flag | Behavior |
|------|----------|
| *(none)* | Launch GUI (falls back to console if no display) |
| `--gui` | Force GUI mode (fails if no display) |
| `--console` | Force console mode |
| `--uci` | UCI engine mode (for chess GUIs like Arena or CuteChess) |

### Console / Headless Mode

In WSL or other headless environments, the app detects the missing display and falls back to an interactive console menu automatically.

```bash
# Force console mode explicitly
java -cp "build/libs/my_bot.jar:libs/chesslib-1.2.0.jar" mybot.MyBot --console
```

### Features

- **Setup screen**: choose player types and time controls
- **Time clocks**: per-player countdown; current player highlighted in yellow
- **Legal move highlighting**: click a piece to see valid squares (yellow semi-transparent)
- **Pawn promotion dialog**: choose Queen, Rook, Bishop, or Knight
- **Unicode piece symbols** on a beige/brown board

### Color Scheme

| Element | Color |
|---------|-------|
| Light squares | Beige `#F0D9B5` |
| Dark squares | Brown `#B58863` |
| Selected piece | Olive green |
| Legal move highlights | Semi-transparent yellow |

### System Requirements

- Java 17+
- Display (X11, Wayland, or native windowing)
- `chesslib-1.2.0.jar` in the same directory as the JAR

### Key Java Classes

| Class | Role |
|-------|------|
| [GameSetupGUI.java](../src/main/java/mybot/GameSetupGUI.java) | Initial configuration screen |
| [ChessGUI.java](../src/main/java/mybot/ChessGUI.java) | Main board and gameplay |
| [GameSettings.java](../src/main/java/mybot/GameSettings.java) | Stores player types and time control config |
| [MyBot.java](../src/main/java/mybot/MyBot.java) | Entry point; dispatches GUI/console/UCI mode |
