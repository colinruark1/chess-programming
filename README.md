# My Chess Bot

A custom UCI chess engine built in Java with iterative deepening search and advanced evaluation.

## Features

- **Iterative Deepening Search**: Progressive depth search with time management
- **Phase-Aware Evaluation**: Smooth interpolation between opening/middlegame and endgame piece-square tables
- **Advanced Move Ordering**: Transposition table moves, killer moves, MVV-LVA
- **Time Management**: Adaptive time allocation based on game phase and time controls
- **UCI Protocol**: Full UCI compliance for integration with chess GUIs

## Building

```bash
./run_bot.sh
```

Or manually:
```bash
javac -cp libs/chesslib-1.2.0.jar -d build/classes src/main/java/mybot/*.java
jar cfm build/libs/my_bot.jar manifest.txt -C build/classes mybot
cp libs/chesslib-1.2.0.jar build/libs/
```

## Running

```bash
java -jar build/libs/my_bot.jar --uci
```

## Usage with Chess GUIs

This engine works with any UCI-compatible chess GUI:
- Arena Chess
- Cute Chess
- PyChess
- Lichess Bot integration

## Technical Details

### Search
- Iterative deepening from depth 1 to 64
- Minimum guaranteed depth of 4 in untimed games
- Alpha-beta pruning with negamax
- Quiescence search for tactical positions
- Transposition table (2M entries)

### Evaluation
- Material counting
- Piece-square tables with phase interpolation
- Continuous phase value (0.0 = opening, 1.0 = endgame)
- Mobility and positional factors

### Time Management
- Increment-aware allocation
- Emergency time handling
- Separate strategies for sudden death vs increment games

## Dependencies

- [chesslib](https://github.com/bhlangonijr/chesslib) 1.2.0 (included in `libs/`)
- Java 17 or higher

## License

[Your license here]

## Credits

Developed with assistance from Claude (Anthropic).
