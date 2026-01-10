# Python Chess GUI

A PyGame-based graphical interface for the MyBot chess engine with full game configuration support.

## Features

### Initial Setup Screen
Before each game, you'll see a configuration screen allowing you to set:
- **White Player**: Human or Computer
- **Black Player**: Human or Computer  
- **Time Control**: Choose between Untimed or Timed games
- **Time Settings** (if Timed is selected):
  - Minutes per player (1-60)
  - Increment per move in seconds (0-60)

### Game Board Interface
- Full graphical chess board with piece images
- Click-to-move interface with legal move highlighting
- **Time clocks** (for timed games) with real-time countdown
- Debug information panel showing engine output
- Control buttons: New Game, Flip Board, Undo Move

### Supported Game Modes
- **Human vs Human**: Both players make moves on the same computer
- **Human vs Computer**: Play against the AI engine
- **Computer vs Human**: AI plays as White, you play as Black
- **Computer vs Computer**: Watch two AI players compete

## Installation

### Requirements
- Python 3.7+
- pygame
- python-chess

### Install Dependencies
```bash
pip install pygame python-chess
```

## Running the GUI

```bash
cd /home/diamo/lichess-bot/lichess-bot/engines/my_bot
python3 chess_gui.py
```

## How to Play

### Setup Phase
1. Launch the GUI - you'll see the setup screen
2. Choose player types for White and Black
3. Select time control mode
4. If using time control, set minutes and increment
5. Click "Start Game" to begin

### Playing the Game

#### Human Players
1. Click on a piece to select it
2. Legal move destinations will be highlighted in yellow-green
3. Click a highlighted square to make your move
4. Click elsewhere to deselect

#### Time Controls (Timed Games)
- Active player's time is highlighted in yellow
- Time counts down in MM:SS.d format
- Increment is added after each move
- Game ends if a player runs out of time

### Control Buttons
- **New Game**: Reset the board and start fresh (keeps same settings)
- **Flip Board**: Rotate the view 180 degrees
- **Undo Move**: Take back the last move

## File Structure

- `chess_gui.py` - Main GUI application
- `build/libs/my_bot.jar` - Chess engine (must be built first)
- `pieces/` - Directory for piece images (optional)

## Building the Engine

The GUI requires the chess engine JAR file. Build it first:

```bash
# Using the existing build
cd /home/diamo/lichess-bot/lichess-bot/engines/my_bot
javac -cp "libs/chesslib-1.2.0.jar" -d build/classes/java/main src/main/java/mybot/*.java
cd build/classes/java/main
jar cvfm ../../../../build/libs/my_bot.jar /dev/stdin mybot/*.class << 'MANIFEST'
Manifest-Version: 1.0
Main-Class: mybot.MyBot
Class-Path: chesslib-1.2.0.jar
MANIFEST
```

## Piece Images

The GUI will look for piece images in a `pieces/` directory:
- `white-pawn.png`, `black-pawn.png`
- `white-knight.png`, `black-knight.png`
- `white-bishop.png`, `black-bishop.png`
- `white-rook.png`, `black-rook.png`
- `white-queen.png`, `black-queen.png`
- `white-king.png`, `black-king.png`

If images are not found, the pieces will appear as colored circles (fallback mode).

You can find free chess piece sets online (search for "chess piece PNG set").

## Troubleshooting

### Engine not found
```
Error: build/libs/my_bot.jar not found
```
**Solution**: Build the engine first (see "Building the Engine" above)

### Pygame not installed
```
ModuleNotFoundError: No module named 'pygame'
```
**Solution**: Install pygame with `pip install pygame`

### No display in WSL
If running in WSL without an X server:
- Install an X server on Windows (VcXsrv, Xming)
- Set `export DISPLAY=:0` in WSL
- Or use the Java Swing GUI instead

## Credits

- Uses the python-chess library for chess logic
- Pygame for graphics rendering
- MyBot engine for computer moves (UCI protocol)

Enjoy playing chess!
