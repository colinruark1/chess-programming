# My Chess Bot

A UCI chess engine written in Java with iterative deepening, alpha-beta pruning, adaptive search depth, phase-interpolated piece-square tables, and a Python/PyGame GUI.

## Quick Start

### Build

```bash
./run_bot.sh
```

Or manually:
```bash
javac -cp libs/chesslib-1.2.0.jar -d build/classes src/main/java/mybot/*.java
jar cfm build/libs/my_bot.jar manifest.txt -C build/classes mybot
cp libs/chesslib-1.2.0.jar build/libs/
```

### Play via GUI

```bash
# Python GUI (recommended)
pip install -r requirements.txt
python3 chess_gui.py

# Java Swing GUI
./run_gui.sh
```

### Run as UCI Engine

```bash
java -jar build/libs/my_bot.jar --uci
```

Compatible with Arena Chess, CuteChess, PyChess, and Lichess Bot integration.

### Test a Position Manually

```bash
java -jar build/libs/my_bot.jar
# type UCI commands:
uci
isready
position startpos
go movetime 2000
quit
```

## Features

| Feature | Details |
|---------|---------|
| Search | Iterative deepening negamax with alpha-beta pruning |
| Quiescence | Captures and promotions resolved before evaluating |
| Transposition table | 2M entries; used for move ordering and avoiding re-search |
| Move ordering | TT move → promotions → captures (MVV-LVA) → killers → quiet |
| Adaptive depth | Adjusts based on time, phase, position complexity, and check |
| Evaluation | Material + phase-interpolated piece-square tables + mobility |
| Time management | Increment-aware; emergency handling; per-phase allocation |
| GUI | Python/PyGame and Java Swing; Human vs Human/Computer/Computer |
| UCI | Full protocol compliance |

## Project Structure

```
chess-programming/
├── src/main/java/mybot/
│   ├── MyBot.java              Entry point; UCI / GUI / console dispatch
│   ├── ChessEngine.java        Search: negamax, alpha-beta, quiescence
│   ├── GamePhase.java          Adaptive depth + time management
│   ├── PieceSquareTables.java  Opening and endgame PSTs
│   ├── PieceTracker.java       Material counting and piece state
│   ├── TranspositionEntry.java Transposition table entries
│   ├── TimeManager.java        Time allocation utilities
│   ├── ChessGUI.java           Java Swing game board
│   ├── GameSetupGUI.java       Java Swing setup screen
│   ├── GameSettings.java       Player type / time control config
│   └── ...
├── chess_gui.py                Python/PyGame GUI
├── pieces/                     Chess piece images
├── libs/chesslib-1.2.0.jar     Chess move generation library
├── build/                      Compiled output (gitignored)
├── run_bot.sh                  Build and run in UCI mode
├── run_gui.sh                  Launch Java Swing GUI
├── RUN_TESTS.sh                Run all debug/test scripts
└── requirements.txt            Python dependencies
```

## Architecture

### Search

The engine uses **negamax with alpha-beta pruning**. Iterative deepening restarts from depth 1 up to 64 each turn, improving the move estimate as time allows. A quiescence search at leaf nodes extends the search through captures and promotions to avoid the horizon effect.

Search depth is not fixed — the `GamePhase.calculateDepth()` method computes it from five factors: game phase (opening/middlegame/endgame), remaining time, position complexity (branching factor), whether the side to move is in check, and piece count. See [docs/search.md](docs/search.md) for full details.

### Move Ordering

Move ordering is separate from move evaluation. `scoreMove()` assigns ordering priorities purely for search efficiency (which move to search first). The best move is always chosen by the `negamax()` centipawn score, not by ordering priority. This distinction is critical — a transposition table move gets the highest search priority but that does not mean it is the best move. See [docs/search.md#move-ordering](docs/search.md#move-ordering) for details.

### Evaluation

Evaluation combines material balance with piece-square tables (PSTs). Separate PST tables exist for opening/middlegame and endgame positions. A continuous phase value (0.0 = opening, 1.0 = endgame) is used to interpolate smoothly between them as pieces leave the board.

### UCI Protocol

`MyBot.java` reads UCI commands from stdin and writes responses to stdout. `ucinewgame` resets state; `position` builds the board; `go` triggers a search and emits `bestmove`. Time parameters (`wtime`, `btime`, `winc`, `binc`) are passed to the adaptive depth calculator.

## Dependencies

- [chesslib](https://github.com/bhlangonijr/chesslib) 1.2.0 — included in `libs/`
- Java 17+
- Python 3.7+ with `pygame` and `python-chess` (GUI only)

## Documentation

| Doc | Contents |
|-----|---------|
| [docs/search.md](docs/search.md) | Adaptive depth, move ordering, evaluation, time management |
| [docs/gui.md](docs/gui.md) | Python GUI and Java Swing GUI usage and customization |
| [docs/debugging.md](docs/debugging.md) | Debug scripts, log analysis, common problems |
| [docs/github-setup.md](docs/github-setup.md) | Git workflow, authentication, branching |

## License

[Your license here]

## Credits

Developed with assistance from Claude (Anthropic).
