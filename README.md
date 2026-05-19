# My Chess Bot

A UCI chess engine written in Java with iterative deepening, alpha-beta pruning, magic-bitboard move generation, phase-interpolated piece-square tables, and a Python/Pygame GUI.

## Quick Start

### Prerequisites

- **Java 17+** — [Download](https://adoptium.net)
- **Python 3.8+** — [Download](https://python.org) (GUI only)

Verify your versions:
```bash
java -version
python3 --version
```

### 1. Install Python dependencies

```bash
pip install -r requirements.txt
```

This installs `pygame` (the GUI) and `python-chess` (board logic and move validation).

### 2. Build the engine

```bash
./build.sh
```

Compiles the Java source and produces `build/libs/my_bot.jar`. Re-running only recompiles if source files have changed.

### 3. Play against the bot

```bash
python3 chess_gui.py
```

On launch you'll see a setup screen to choose:
- White / Black player type (Human or Computer)
- Time control (Untimed or Timed, with increment)

**Java Swing GUI** — simpler built-in interface:
```bash
./run_gui.sh
```

**Console mode** (no display required):
```bash
java -jar build/libs/my_bot.jar --console
```

## Features

| Feature | Details |
|---------|---------|
| Search | Iterative deepening negamax with alpha-beta pruning |
| Quiescence | Captures resolved at leaf nodes to avoid horizon effect |
| Transposition table | 2M-entry flat array (packed `long[]`); move ordering + re-search avoidance |
| Move ordering | TT move → promotions → captures (SEE) → killers → quiet |
| Null-move pruning | R=2+depth/6, skipped in check and pawn-only positions |
| Evaluation | Material + phase-interpolated piece-square tables |
| Time management | Increment-aware with emergency low-time handling |
| Move generation | Magic bitboards for sliders; full pseudo-legal + legality filter |
| GUI | Python/Pygame and Java Swing; Human vs Human / Computer |
| UCI | Full protocol: `uci`, `isready`, `position`, `go`, `ucinewgame`, `quit` |

## Run as UCI Engine

To connect to an external GUI (Arena, CuteChess, etc.) or Lichess Bot:

```bash
./run_bot.sh
```

Or point your GUI directly at the JAR:
```
java -jar /path/to/build/libs/my_bot.jar --uci
```

## Project Structure

```
chess-programming/
├── src/main/java/mybot/
│   ├── MyBot.java              Entry point; UCI / GUI / console dispatch
│   ├── ChessEngine.java        Search: negamax, alpha-beta, quiescence, TT
│   ├── Board.java              Bitboard board representation and move making
│   ├── MoveGenerator.java      Pseudo-legal move generation + legality filter
│   ├── AttackTables.java       Magic bitboard tables for sliders
│   ├── Bitboard.java           Bitboard utilities
│   ├── Move.java               Move encoding (from/to/flags in 16 bits)
│   ├── Piece.java              Piece type and color constants
│   ├── Sq.java                 Square constants and utilities
│   ├── Zobrist.java            Zobrist hashing for TT keys
│   ├── PieceSquareTables.java  Opening and endgame PSTs
│   ├── PieceTracker.java       Incremental material + PST score tracking
│   ├── TimeManager.java        Per-move time allocation from UCI go command
│   ├── GamePhase.java          Opening / middlegame / endgame detection
│   ├── ChessGUI.java           Java Swing game board
│   ├── GameSetupGUI.java       Java Swing setup screen
│   ├── GameSettings.java       Player type / time control config
│   ├── Profiler.java           Timing and memory profiler
│   ├── Perft.java              Move generation correctness testing
│   └── Debug.java              Magic bitboard diagnostics
├── chess_gui.py                Python/Pygame GUI (recommended)
├── pieces/                     Chess piece PNG images
├── build/                      Compiled output (gitignored)
├── build.sh                    Build the engine JAR only
├── run_bot.sh                  Build and run in UCI mode
├── run_gui.sh                  Build and launch Java Swing GUI
├── requirements.txt            Python dependencies (pygame, python-chess)
└── build.gradle                Gradle build config
```

## Architecture

### Move Generation

The engine uses **magic bitboards** for slider pieces (rooks, bishops, queens). Attack tables are precomputed at startup via `AttackTables.java` and indexed by a magic-multiplied occupancy hash, giving O(1) attack lookups. Pawns, knights, and kings use fixed precomputed tables.

### Search

The engine uses **negamax with alpha-beta pruning**. Iterative deepening restarts from depth 1 each turn, improving the best-move estimate as time allows. Quiescence search at leaf nodes extends through captures to avoid evaluating unstable positions.

**Pruning techniques:**
- Alpha-beta cutoffs
- Null-move pruning (R = 2 + depth/6)
- Transposition table cutoffs (exact, lower bound, upper bound)

### Move Ordering

`scoreMove()` assigns ordering priorities for search efficiency only — it does not affect which move is ultimately chosen. Priority order: TT move → queen promotions → winning captures (SEE ≥ 0) → killer moves → losing captures → quiet moves.

### Evaluation

Material balance with **incrementally updated piece-square tables** (PSTs). `PieceTracker` maintains the running score as pieces move, so evaluation is O(1) at leaf nodes. Separate PST tables for opening and endgame phases are blended continuously based on remaining material.

### Transposition Table

The TT uses two parallel `long[]` arrays (keys and packed data) for ~32 MB total, replacing what would be ~200+ MB with Java object overhead. Each entry packs score (16-bit), depth (8-bit), bound type (2-bit), and best move (16-bit) into a single `long`.

## Dependencies

- Java 17+
- Python 3.8+ with `pygame` and `python-chess` (GUI only)

## License

[Your license here]

## Credits

Developed with assistance from Claude (Anthropic).
