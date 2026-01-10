# GamePhase Usage Guide

The `GamePhase` class has been enhanced to provide intelligent search depth calculation based on multiple factors beyond just material count.

## Overview

The new `GamePhase` class is a comprehensive decision-making system that determines optimal search depth by analyzing:

1. **Time Management**: Allocates thinking time based on remaining time and increment
2. **Game Phase**: Opening, Middlegame, or Endgame detection
3. **Piece Count**: Simplified positions allow deeper search
4. **Material Balance**: Complex tactical situations
5. **Position Complexity**: Branching factor and legal move count
6. **Critical Situations**: Check, forced sequences, near-mate positions

## Basic Usage

### Simple Depth Calculation (Backward Compatible)

```java
// Legacy method - uses only material
int absoluteMaterial = pieceTracker.getAbsoluteMaterial();
int depth = GamePhase.getSearchDepth(absoluteMaterial);
```

### Advanced Depth Calculation

```java
// Full calculation with all factors
int depth = GamePhase.calculateDepth(
    board,                    // Current board position
    absoluteMaterial,         // Total material on board
    pieceCount,              // Number of pieces (excluding kings)
    timeRemainingMs,         // Time remaining in milliseconds (0 = no time control)
    incrementMs,             // Increment per move in milliseconds
    movesPlayed              // Number of moves played so far
);
```

### Example Integration in ChessEngine

```java
public Move selectBestMove(long timeRemainingMs, long incrementMs, int movesPlayed) {
    int absoluteMaterial = pieceTracker.getAbsoluteMaterial();
    int pieceCount = countPieces();

    // Calculate optimal depth
    int depth = GamePhase.calculateDepth(
        board,
        absoluteMaterial,
        pieceCount,
        timeRemainingMs,
        incrementMs,
        movesPlayed
    );

    System.out.println("Searching at depth " + depth);

    // Run search...
    return negamaxRoot(depth);
}
```

## Configuration

### Custom Depth Configuration

You can customize the behavior by creating a custom `DepthConfig`:

```java
GamePhase.DepthConfig config = new GamePhase.DepthConfig();

// Adjust base depths by phase
config.openingDepth = 5;      // Increase opening depth
config.middlegameDepth = 7;   // Increase middlegame depth
config.endgameDepth = 9;      // Increase endgame depth

// Time management settings
config.useTimeManagement = true;
config.timeBufferPercentage = 0.15;  // Reserve 15% of time (default 10%)
config.criticalTimeThreshold = 15.0; // Enter time trouble at 15s (default 10s)

// Complexity adjustments
config.adjustForComplexity = true;
config.maxComplexityBonus = 3;      // Allow up to +3 depth for simple positions
config.maxComplexityReduction = 2;  // Allow up to -2 depth for complex positions

// Critical situations
config.adjustForCheck = true;
config.checkDepthBonus = 2;         // +2 depth when in check (default 1)

// Piece count adjustments
config.adjustForPieceCount = true;
config.fewPiecesThreshold = 10;     // Consider "simple" below 10 pieces
config.simplificationBonus = 2;     // +2 depth in simple positions

// Use custom configuration
int depth = GamePhase.calculateDepth(
    board, absoluteMaterial, pieceCount,
    timeRemainingMs, incrementMs, movesPlayed,
    config
);
```

### Playing Styles

**Aggressive (Search Deeper)**:
```java
config.openingDepth = 5;
config.middlegameDepth = 7;
config.endgameDepth = 10;
config.maxComplexityBonus = 3;
config.checkDepthBonus = 2;
```

**Conservative (Safer Time Management)**:
```java
config.openingDepth = 3;
config.middlegameDepth = 5;
config.endgameDepth = 7;
config.timeBufferPercentage = 0.2;  // Reserve 20% time
config.maxComplexityBonus = 1;
```

**Blitz Mode**:
```java
config.openingDepth = 3;
config.middlegameDepth = 4;
config.endgameDepth = 5;
config.criticalTimeThreshold = 5.0;
config.adjustForComplexity = false;  // Disable to save time
```

## Helper Methods

### Calculate Move Time

Determine how much time to spend on a move:

```java
long moveTimeMs = GamePhase.calculateMoveTime(
    timeRemainingMs,
    incrementMs,
    movesPlayed,
    isComplexPosition  // boolean - is position tactically complex?
);

// Use with UCI go command
engine.send("go movetime " + moveTimeMs);
```

### Position Complexity

Estimate how complex a position is (0-100 scale):

```java
int complexity = GamePhase.estimateComplexity(
    board,
    pieceCount,
    materialBalance
);

System.out.println("Position complexity: " + complexity + "/100");

if (complexity > 70) {
    // Very complex tactical position
} else if (complexity < 30) {
    // Simple positional game
}
```

### Critical Position Detection

Check if extra thinking time is warranted:

```java
boolean isCritical = GamePhase.isCriticalPosition(board, absoluteMaterial);

if (isCritical) {
    depth += 1;  // Add extra depth
    // Or allocate more time
}
```

### Phase Detection

Determine current game phase:

```java
// By material
GamePhase.Phase phase = GamePhase.detectPhase(absoluteMaterial);

// By piece count
GamePhase.Phase phase = GamePhase.detectPhaseByPieceCount(pieceCount);

switch (phase) {
    case OPENING -> System.out.println("Opening phase");
    case MIDDLEGAME -> System.out.println("Middlegame");
    case ENDGAME -> System.out.println("Endgame");
}
```

## Time Management Details

### How Time is Allocated

The `calculateDepth` method uses this logic:

1. **Emergency Time** (< 5 seconds): Return depth - 2
2. **Critical Time** (< threshold): Return depth - 2
3. **Estimate remaining moves**: `40 - min(movesPlayed, 30)`, minimum 10
4. **Reserve buffer**: Default 10% of remaining time
5. **Calculate time per move**: `(available time / moves remaining) + increment`
6. **Adjust depth**:
   - `> 30s per move`: depth + 2
   - `> 10s per move`: depth + 1
   - `> 3s per move`: base depth
   - `> 1s per move`: depth - 1
   - `< 1s per move`: depth - 2

### Move Time Calculation

The `calculateMoveTime` method:

1. **Emergency** (< 5s): 500ms
2. **Critical** (< 15s): 2000ms
3. **Normal**: Base time per move × complexity multiplier
4. **Cap**: Never more than 20% of remaining time

## Integration with UCI

### Receiving Time Information

From UCI `go` command:

```java
// Example UCI command:
// go wtime 60000 btime 58000 winc 1000 binc 1000

if (line.startsWith("go")) {
    long wtime = 0, btime = 0, winc = 0, binc = 0;
    String[] parts = line.split(" ");

    for (int i = 1; i < parts.length; i++) {
        switch (parts[i]) {
            case "wtime" -> wtime = Long.parseLong(parts[++i]);
            case "btime" -> btime = Long.parseLong(parts[++i]);
            case "winc" -> winc = Long.parseLong(parts[++i]);
            case "binc" -> binc = Long.parseLong(parts[++i]);
        }
    }

    long timeRemaining = (board.getSideToMove() == Side.WHITE) ? wtime : btime;
    long increment = (board.getSideToMove() == Side.WHITE) ? winc : binc;

    Move best = engine.selectBestMove(timeRemaining, increment, movesPlayed);
}
```

## Examples

### Example 1: Opening Position

```java
// Position: Starting position (all pieces on board)
// Time: 5 minutes, 3 second increment
// Moves: 5 played

int depth = GamePhase.calculateDepth(
    board,
    7800,          // High material
    30,            // All pieces
    300000,        // 5 minutes
    3000,          // 3 second increment
    5              // Early in game
);
// Result: depth = 4 (opening base) + 0 (plenty of time) = 4
```

### Example 2: Complex Middlegame

```java
// Position: Complex tactical middlegame, 45 legal moves
// Time: 2 minutes remaining
// Moves: 25 played

int depth = GamePhase.calculateDepth(
    board,
    4500,          // Middlegame material
    20,            // Moderate pieces
    120000,        // 2 minutes
    1000,          // 1 second increment
    25             // Halfway through
);
// Result: depth = 6 (middlegame) - 1 (complex position) = 5
```

### Example 3: Simple Endgame

```java
// Position: K+R vs K endgame, 8 legal moves
// Time: 1 minute remaining
// Moves: 50 played

int depth = GamePhase.calculateDepth(
    board,
    1000,          // Low material (endgame)
    3,             // Very few pieces
    60000,         // 1 minute
    500,           // 0.5 second increment
    50             // Late game
);
// Result: depth = 8 (endgame) + 2 (simple) + 1 (few pieces) = 11
```

### Example 4: Time Trouble

```java
// Position: Any position
// Time: 8 seconds remaining
// Moves: 40 played

int depth = GamePhase.calculateDepth(
    board,
    3500,          // Middlegame
    18,
    8000,          // Critical time!
    0,
    40
);
// Result: depth = 6 - 2 (critical time) = 4 (reduced for speed)
```

## Migration from Old Code

### Old Code

```java
int absoluteMaterial = pieceTracker.getAbsoluteMaterial();
GamePhase phase = GamePhase.fromMaterial(absoluteMaterial);
int depth = phase.getSearchDepth();
```

### New Code (Simple Migration)

```java
int absoluteMaterial = pieceTracker.getAbsoluteMaterial();
int depth = GamePhase.getSearchDepth(absoluteMaterial);  // Backward compatible
```

### New Code (Full Features)

```java
int absoluteMaterial = pieceTracker.getAbsoluteMaterial();
int pieceCount = countPiecesOnBoard();
int depth = GamePhase.calculateDepth(
    board,
    absoluteMaterial,
    pieceCount,
    timeRemainingMs,
    incrementMs,
    movesPlayed
);
```

## Best Practices

1. **Always pass time information when available**: Helps prevent time trouble
2. **Track moves played**: Important for endgame time allocation
3. **Use custom config for different time controls**: Blitz vs rapid vs classical
4. **Monitor depth in logs**: Helps tune configuration
5. **Test time management in practice games**: Ensure no timeouts

## Future Extensions

The `GamePhase` class is designed to be extensible. Future factors could include:

- **Opening book integration**: Reduce depth in known book lines
- **Tablebase proximity**: Increase depth near tablebase positions
- **Opponent strength**: Adjust based on rating difference
- **Game importance**: Tournament vs casual
- **Hardware detection**: Auto-tune for CPU speed
- **Search statistics**: Adjust based on NPS (nodes per second)
- **Position evaluation**: Deeper search in unclear positions
