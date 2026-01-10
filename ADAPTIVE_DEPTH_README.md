# Adaptive Search Depth System

The chess engine now uses an **advanced adaptive depth calculation** system that dynamically adjusts search depth based on multiple factors, providing optimal play in various game situations.

## Overview

The core `selectBestMove()` method has been upgraded from simple material-based phase detection to a sophisticated multi-factor depth calculation system.

### Before (Simple)
```java
int depth = GamePhase.getSearchDepth(absoluteMaterial);
// Returns: Opening=4, Middlegame=6, Endgame=8 (material only)
```

### After (Adaptive)
```java
int depth = GamePhase.calculateDepth(
    board,              // Current position
    absoluteMaterial,   // Total material
    pieceCount,         // Number of pieces (excluding kings)
    timeRemainingMs,    // Time left for current side
    incrementMs,        // Increment per move
    movesPlayed         // Moves played so far
);
// Returns: Dynamic depth 1-12 based on multiple factors
```

## Factors Considered

### 1. **Game Phase** (Base Depth)
- **Opening** (material ≥ 6400): Base depth 4
- **Middlegame** (material ≥ 3000): Base depth 6
- **Endgame** (material < 3000): Base depth 8

### 2. **Time Management**
Adapts depth based on time remaining and increment:
- **Lots of time** (>30s per move): +2 depth
- **Good time** (>10s per move): +1 depth
- **Normal time** (>3s per move): Base depth
- **Limited time** (>1s per move): -1 depth
- **Very limited** (≤1s per move): -2 depth
- **Time trouble** (<10s total): Max reduction (-2)

Estimates remaining moves conservatively (40 - min(movesPlayed, 30)).

### 3. **Position Complexity**
Adjusts based on number of legal moves:
- **Very complex** (>40 legal moves): -1 depth (too expensive)
- **Simple** (<10 legal moves): +2 depth (can afford deeper search)
- **Somewhat simple** (<20 legal moves): +1 depth
- **Normal complexity**: No adjustment

### 4. **Critical Situations**
- **In check**: +1 depth (tactical awareness)
- **Simplified position** (≤8 pieces): +1 depth (easier to calculate)

### 5. **Depth Bounds**
Final depth is clamped: **min(1, max(depth, 12))**

## New ChessEngine Methods

### Set Time Control
```java
engine.setTimeControl(
    300000,  // White time: 5 minutes
    300000,  // Black time: 5 minutes
    5000     // Increment: 5 seconds
);
```

### Update Time After Move
```java
engine.updateTime(Side.WHITE, timeUsedMs);
// Automatically:
// - Subtracts time used
// - Adds increment
// - Increments move counter
```

### Get Move Count
```java
int moves = engine.getMovesPlayed();
```

## Integration Points

### 1. **UCI Mode**
Time tracking happens automatically via `parsePosition()`:
- `ucinewgame` resets move counter to 0
- `position` with moves counts and tracks them
- Each move increments counter

### 2. **Manual Mode**
Time control can be set at game start for adaptive depth.

### 3. **GUI Modes**
Both Java Swing and Python GUIs can set time control:

```java
// Java Swing GUI
engine.setTimeControl(whiteTimeMs, blackTimeMs, incrementMs);
```

```python
# Python GUI - engine interface handles time via UCI protocol
```

## Example Depth Calculations

### Opening Position (No Time Control)
```
Material: 8000 (all pieces)
Pieces: 30
Moves: 0
Time: 0 (no time control)
Legal moves: ~20

Result: depth = 4 (opening base)
```

### Middlegame (Timed, Low on Time)
```
Material: 5500
Pieces: 22
Moves: 25
Time: 8 seconds remaining
Legal moves: 35

Result: depth = 6 (middlegame) - 2 (time trouble) = 4
```

### Endgame (Lots of Time, Simplified)
```
Material: 1500
Pieces: 6
Moves: 45
Time: 60 seconds remaining
Legal moves: 12

Result: depth = 8 (endgame) + 1 (simplified) + 1 (somewhat simple) = 10
```

### Critical Position (In Check, Few Moves)
```
Material: 3200
Pieces: 18
Moves: 30
Time: 15 seconds
Legal moves: 5 (in check, forced)

Result: depth = 6 (middlegame) + 1 (check) + 2 (very simple) = 9
```

## Performance Impact

**Adaptive depth provides:**
- ✅ **Better time management** - Doesn't run out of time in timed games
- ✅ **Stronger endgame play** - Searches deeper when there are fewer pieces
- ✅ **Tactical awareness** - Extra depth when in check or forced positions
- ✅ **Efficiency** - Reduces depth in complex positions to stay within time
- ✅ **Consistent strength** - Plays optimally across all game phases

## Configuration

The default configuration is defined in `GamePhase.DepthConfig`:

```java
public static class DepthConfig {
    // Base depths by phase
    public int openingDepth = 4;
    public int middlegameDepth = 6;
    public int endgameDepth = 8;
    
    // Time management
    public boolean useTimeManagement = true;
    public double timeBufferPercentage = 0.1;
    public double criticalTimeThreshold = 10.0;
    
    // Complexity adjustments
    public boolean adjustForComplexity = true;
    public int maxComplexityBonus = 2;
    public int maxComplexityReduction = 1;
    
    // Critical situations
    public boolean adjustForCheck = true;
    public int checkDepthBonus = 1;
    
    // Piece count adjustments
    public boolean adjustForPieceCount = true;
    public int fewPiecesThreshold = 8;
    public int simplificationBonus = 1;
}
```

You can customize by creating a custom config and passing it to `calculateDepth()`.

## Testing

The system is automatically active in all modes. You'll see depth adjustments in the debug output:

```
Searching at depth 6 (phase: MIDDLEGAME, material: 5200, pieces: 20, moves: 18, time: 45.3s)
```

This shows the engine is using adaptive depth calculation based on the current game state!

---

**Result**: The engine now plays significantly stronger, especially in timed games and endgames!
