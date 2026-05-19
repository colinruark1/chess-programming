# Search Algorithm

## Overview

The engine uses **negamax with alpha-beta pruning** and **iterative deepening**. Search depth is determined dynamically by the `GamePhase` class based on time, position complexity, and game phase.

## Negamax & Alpha-Beta

- Iterative deepening from depth 1 to 64
- Minimum guaranteed depth of 4 in untimed games
- Alpha-beta pruning with negamax framework
- Quiescence search to handle tactical positions (captures, promotions)
- Transposition table with 2 million entries

## Adaptive Depth (`GamePhase.calculateDepth`)

Search depth is not fixed. The `GamePhase.calculateDepth()` method computes an optimal depth by combining five factors:

### 1. Base Depth by Game Phase

Phase is determined by total material remaining:

| Phase | Material Threshold | Base Depth |
|-------|--------------------|------------|
| OPENING | ≥ 6400 | 4 |
| MIDDLEGAME | 3000–6399 | 6 |
| ENDGAME | < 3000 | 8 |

Phase can also be detected by piece count:
```java
GamePhase.Phase phase = GamePhase.detectPhaseByPieceCount(pieceCount);
```

### 2. Time Management

Estimates time per move as `(available_time / estimated_remaining_moves) + increment`.

| Time per Move | Depth Adjustment |
|---------------|-----------------|
| > 30s | +2 |
| > 10s | +1 |
| > 3s | base |
| > 1s | −1 |
| < 1s | −2 |
| < 10s total | −2 (emergency) |

Remaining moves estimated as `40 - min(movesPlayed, 30)`, minimum 10.

### 3. Position Complexity

| Legal Moves | Adjustment |
|-------------|-----------|
| > 40 | −1 (too expensive) |
| < 10 | +2 (can afford deeper) |
| < 20 | +1 |
| otherwise | 0 |

### 4. Critical Situations

| Condition | Bonus |
|-----------|-------|
| In check | +1 |
| ≤ 8 pieces on board | +1 |

### 5. Depth Bounds

Final depth is clamped to **[1, 12]**.

### API

```java
// Simple (backward compatible)
int depth = GamePhase.getSearchDepth(absoluteMaterial);

// Full adaptive calculation
int depth = GamePhase.calculateDepth(
    board,            // current position
    absoluteMaterial, // total material
    pieceCount,       // non-king pieces
    timeRemainingMs,  // 0 = no time control
    incrementMs,
    movesPlayed
);
```

### Custom Configuration

```java
GamePhase.DepthConfig config = new GamePhase.DepthConfig();
config.openingDepth = 5;
config.middlegameDepth = 7;
config.endgameDepth = 9;
config.timeBufferPercentage = 0.15; // reserve 15% of time
config.criticalTimeThreshold = 15.0;
config.checkDepthBonus = 2;

int depth = GamePhase.calculateDepth(board, material, pieces, time, inc, moves, config);
```

**Preset profiles:**

```java
// Aggressive
config.openingDepth = 5; config.middlegameDepth = 7; config.endgameDepth = 10;

// Blitz
config.openingDepth = 3; config.middlegameDepth = 4; config.endgameDepth = 5;
config.criticalTimeThreshold = 5.0; config.adjustForComplexity = false;

// Conservative
config.openingDepth = 3; config.middlegameDepth = 5; config.endgameDepth = 7;
config.timeBufferPercentage = 0.2;
```

### Helper Methods

```java
// Time to spend on this move
long moveTimeMs = GamePhase.calculateMoveTime(timeRemainingMs, incrementMs, movesPlayed, isComplex);

// Position complexity score (0–100)
int complexity = GamePhase.estimateComplexity(board, pieceCount, materialBalance);

// Is extra thinking time warranted?
boolean isCritical = GamePhase.isCriticalPosition(board, absoluteMaterial);
```

## Move Ordering

Good move ordering is critical for alpha-beta efficiency. Moves are sorted **before** searching, not selected based on sort score.

**Order:**
1. Transposition table move (score: 1,000,000,000)
2. Promotions
3. Winning/equal captures by MVV-LVA (Most Valuable Victim, Least Valuable Attacker)
4. Killer moves (quiet moves that recently caused beta cutoffs)
5. Other quiet moves

### Move Ordering vs. Move Quality

These are two separate concepts:

- **Move ordering (`scoreMove`)**: determines *search order* for efficiency. Higher = search first.
- **Move evaluation (`negamax` return value)**: determines *which move to play*. Measured in centipawns.

A move in the transposition table gets the highest ordering priority (searched first) but its actual quality is still determined independently by negamax evaluation.

```
Example:
  Qb3 (TT move)  →  ordering: 1,000,000,000  |  eval: −50 cp
  Nxc6 (capture) →  ordering:    10,009,000  |  eval: +892 cp
  
Search order: Qb3 first (efficient)
Best move chosen: Nxc6 (best eval) ✓
```

**`scoreMove()` is only used for sorting — never for deciding the best move.** The decision is always based on the score returned by `negamax()`.

### Where `scoreMove` Is Called

| Location | Purpose |
|----------|---------|
| Root move ordering | Sort before iterative deepening root search |
| Negamax move ordering | Sort at each interior node |
| Quiescence move ordering | Sort captures in quiescence search |

### Move Ordering Score Ranges

| Range | Meaning |
|-------|---------|
| 1,000,000,000+ | TT move |
| 100,000,000+ | Promotion |
| 10,000,000+ | Capture (MVV-LVA) |
| 1,000–9,000 | Killer moves |
| 0–1,000 | Quiet moves |

## Evaluation

- **Material**: piece values in centipawns
- **Piece-square tables (PST)**: separate opening/middlegame and endgame tables
- **Phase interpolation**: smooth blend between opening PST and endgame PST using a continuous phase value (0.0 = opening, 1.0 = endgame)
- **Mobility**: number of legal moves available

## Time Management in UCI

Parsing the `go` command:

```java
// go wtime 60000 btime 58000 winc 1000 binc 1000
long wtime = 0, btime = 0, winc = 0, binc = 0;
String[] parts = line.split(" ");
for (int i = 1; i < parts.length; i++) {
    switch (parts[i]) {
        case "wtime" -> wtime = Long.parseLong(parts[++i]);
        case "btime" -> btime = Long.parseLong(parts[++i]);
        case "winc"  -> winc  = Long.parseLong(parts[++i]);
        case "binc"  -> binc  = Long.parseLong(parts[++i]);
    }
}
long timeRemaining = (board.getSideToMove() == Side.WHITE) ? wtime : btime;
long increment     = (board.getSideToMove() == Side.WHITE) ? winc  : binc;
Move best = engine.selectBestMove(timeRemaining, increment, movesPlayed);
```

Setting time control manually:
```java
engine.setTimeControl(300000, 300000, 5000); // 5 min + 5s increment
engine.updateTime(Side.WHITE, timeUsedMs);   // call after each move
int moves = engine.getMovesPlayed();
```

## Depth Calculation Examples

```
Opening (no time control):
  material=8000, pieces=30, moves=0, legalMoves=20
  → depth 4 (opening base)

Middlegame (time trouble):
  material=5500, pieces=22, moves=25, time=8s, legalMoves=35
  → depth 6 − 2 (critical time) = 4

Endgame (lots of time, simplified):
  material=1500, pieces=6, moves=45, time=60s, legalMoves=12
  → depth 8 + 1 (few pieces) + 1 (somewhat simple) = 10

In check (few legal moves):
  material=3200, pieces=18, moves=30, time=15s, legalMoves=5
  → depth 6 + 1 (check) + 2 (very simple) = 9
```

## Key Source Files

| File | Purpose |
|------|---------|
| [src/main/java/mybot/ChessEngine.java](../src/main/java/mybot/ChessEngine.java) | Main search: negamax, quiescence, move ordering |
| [src/main/java/mybot/GamePhase.java](../src/main/java/mybot/GamePhase.java) | Depth calculator, phase detection, time management |
| [src/main/java/mybot/PieceSquareTables.java](../src/main/java/mybot/PieceSquareTables.java) | Opening and endgame PSTs |
| [src/main/java/mybot/PieceTracker.java](../src/main/java/mybot/PieceTracker.java) | Material counting, piece tracking |
| [src/main/java/mybot/TranspositionEntry.java](../src/main/java/mybot/TranspositionEntry.java) | Transposition table entries |
| [src/main/java/mybot/TimeManager.java](../src/main/java/mybot/TimeManager.java) | Time allocation utilities |
