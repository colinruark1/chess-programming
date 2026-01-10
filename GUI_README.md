# Chess Game GUI

This chess engine now includes a graphical user interface for playing chess!

## Features

### Game Setup Screen
The initial setup screen allows you to configure:
- **White Player**: Choose between Human or Computer
- **Black Player**: Choose between Human or Computer
- **Game Mode**: Select Timed or Untimed games
- **Time Control** (if Timed is selected):
  - Time per player (in minutes)
  - Increment per move (in seconds)

### Chess Board Interface
Once you start the game, you'll see:
- A graphical chess board with Unicode piece symbols
- **Time clocks** (if playing a timed game) showing remaining time for each player
- **Status bar** showing whose turn it is and game state (check, checkmate, etc.)
- **Interactive gameplay**:
  - Click a piece to select it (highlighted squares show legal moves)
  - Click a destination square to move the piece
  - Pawn promotion dialog appears when promoting

### Game Modes

#### Human vs Human
Both players take turns making moves on the same computer.

#### Human vs Computer
You play against the chess engine. The computer will think and make its move automatically.

#### Computer vs Computer
Watch two AI players compete! The computer will play both sides.

## Running the GUI

### Option 1: Using the launch script (Linux/Mac)
```bash
./run_gui.sh
```

### Option 2: Direct Java command
```bash
java -jar my_bot_gui.jar
```

### Option 3: Running in UCI mode (for chess engines)
```bash
java -jar my_bot_gui.jar --uci
```

## System Requirements
- Java 17 or later
- Display with GUI support (X11, Wayland, or native windowing)
- The `chesslib-1.2.0.jar` library must be in the same directory as `my_bot_gui.jar`

## Game Controls

### Making Moves (Human Players)
1. Click on a piece of your color to select it
2. Valid destination squares will be highlighted
3. Click a highlighted square to move there
4. Click another piece of your color to select a different piece
5. Click the selected piece again or an invalid square to deselect

### Time Controls (Timed Games)
- Time counts down automatically when it's your turn
- Increment is added after each move
- If time runs out, you lose the game
- Current player's time is highlighted in yellow

### Pawn Promotion
When a pawn reaches the opposite end of the board, a dialog will appear asking you to choose a promotion piece (Queen, Rook, Bishop, or Knight).

## Technical Details

### New Classes
- **GameSettings**: Stores game configuration (player types, time controls)
- **GameSetupGUI**: Initial configuration screen
- **ChessGUI**: Main game board and gameplay interface

### Modified Classes
- **MyBot**: Now launches GUI by default, use `--uci` flag for UCI mode
- **ChessEngine**: Made `pieceTracker` public for GUI access

### Color Scheme
- Light squares: Beige (#F0D9B5)
- Dark squares: Brown (#B58863)
- Selected piece: Olive green
- Legal moves: Yellow highlight (semi-transparent)

Enjoy playing chess!

## WSL and Headless Environments

If you're running in WSL (Windows Subsystem for Linux) or any headless environment without a display, the application will automatically detect this and run in **console mode** instead.

### Console Mode Features
- Interactive menu to choose game type
- Play against computer (same as the `manual` UCI command)
- Watch computer vs computer games
- Displays moves and board state in FEN notation

### Running in WSL
```bash
# Automatically detects headless environment and uses console mode
./run_gui.sh

# Or explicitly request console mode
java -cp "my_bot_gui.jar:libs/chesslib-1.2.0.jar" mybot.MyBot --console
```

### Setting up GUI in WSL (Optional)
If you want to use the GUI in WSL, you can set up an X server:
1. Install an X server on Windows (e.g., VcXsrv, Xming)
2. Set the DISPLAY environment variable in WSL:
   ```bash
   export DISPLAY=:0
   ```
3. Run the application with `--gui` flag to force GUI mode

## Command-Line Options

- **No arguments**: Launches GUI (or console mode if headless)
- `--gui`: Force GUI mode (will fail if no display available)
- `--console`: Force console mode
- `--uci`: Run in UCI mode for chess engine protocol
