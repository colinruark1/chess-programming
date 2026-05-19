# Debugging Guide

## Debug Scripts

Four scripts are available for testing specific positions. They are separate from `run_bot.sh` (which is for production use).

| Script | Purpose | Output |
|--------|---------|--------|
| `debug_test.sh` | Quick automated test of both problem positions | Console |
| `debug_deep.sh` | Interactive, color-coded, pauses between positions | Console (interactive) |
| `debug_focused.sh` | Saves detailed logs for later analysis | `/tmp/test*.log` |
| `test_ordering_fix.sh` | Verifies move ordering vs. evaluation separation | Console |

Run all via the launcher:
```bash
./RUN_TESTS.sh
```

Or individually:
```bash
./debug_test.sh
./debug_deep.sh
./debug_focused.sh
./test_ordering_fix.sh
```

### Test Positions

**Position 1** — general move selection test:
```
position startpos moves e2e4 b7b5 d2d4 e7e5 d4e5 f8b4 b1c3
```

**Position 2** — exposed queen / tactical priority:
```
position startpos moves e2e4 d7d5 e4d5 d8d5 g1f3
```

## Reading Debug Output

### Material Score
```
Raw material score: 0
```
- Should be ~0 for balanced positions
- Should change by ±900 on queen capture, ±500 on rook capture

### Move Evaluation (after ordering fix)
```
Evaluating move: Nc6 -> eval: 892 cp (ordering: 10009000)
Evaluating move: Qb3 -> eval: -50 cp (ordering: 1000000000)
```
- `eval` (centipawns) determines which move the engine plays
- `ordering` determines which move gets searched first — not the same as quality

### Principal Variation
```
========== PRINCIPAL VARIATION ==========
Best score: 120 (cp)
PV depth: 5 plies
Best line: Nc6 Nxd5 Qxd5 ...
```
Should make tactical sense and not give away material.

### Move Ordering Statistics
```
========== MOVE ORDERING STATISTICS ==========
First move cutoffs: 65.4%
```
Should be > 50%. Low percentage indicates inefficient move ordering.

### Transposition Table
```
TT hits: 12345, misses: 5678, hit rate: 68.5%
```
Should be > 50% ideally.

## Common Problems

### Engine Makes Bad Moves

**Check:**
```bash
grep 'Evaluating move:' /tmp/test1_output.log | head -20
```
Captures should have eval reflecting the material gain. Best move should match the highest `eval`, not the highest `ordering`.

### Material Doesn't Track Correctly

**Check:**
```bash
grep -i 'material' /tmp/test1_output.log
grep '\[DEBUG' /tmp/test1_output.log
```
Material balance should update after every capture.

### Slow Search / Low Cutoff Rate

**Check:**
```bash
grep -A 20 'MOVE ORDERING STATISTICS' /tmp/test1_output.log
```
TT hit rate and first-move cutoff rate both indicate ordering quality.

### Nonsensical Principal Variation

**Check:**
```bash
grep 'PRINCIPAL VARIATION' -A 10 /tmp/test1_output.log
grep -i 'window error' /tmp/test1_output.log
```

## Quick Analysis Commands

After running `debug_focused.sh`:

```bash
# Top evaluated moves
grep 'Evaluating move:' /tmp/test1_output.log | head -10

# Material tracking
grep -i 'material' /tmp/test1_output.log

# Best move and PV
grep -A 5 'PRINCIPAL VARIATION' /tmp/test1_output.log

# All debug messages
grep -E '\[DEBUG|ROOT|Ply\]' /tmp/test1_output.log

# Move ordering effectiveness
grep -A 20 'MOVE ORDERING STATISTICS' /tmp/test1_output.log

# Sort moves by eval score
grep 'Evaluating move:' /tmp/test1_output.log | sort -t: -k2 -n -r | head -20
```

## Healthy vs. Problematic Output

**Healthy:**
```
There are 30 legal moves.
Searching at depth 5 (phase: OPENING, material: 0)
Evaluating move: Nc6 -> eval: 892 cp (ordering: 10009000)
Evaluating move: d6  -> eval: 45 cp  (ordering: 1000000000)
...
Best score: 120 (cp)
PV depth: 5 plies
Best line: Nc6 Nxd5 Qxd5 ...
```

**Problematic:**
```
Evaluating move: a4    -> eval: -500 cp  ← quiet move scored too high
Evaluating move: Nxd5  -> eval: -800 cp  ← capture scored low
Best line: a4 a5 ...                      ← nonsensical PV
```

## Debug Entry Points in ChessEngine.java

| Lines | What Is Logged |
|-------|---------------|
| 191–202 | Debug output for specific moves (e.g., d5e5) |
| 478–483 | Ply 2 move logging |
| 500–505 | f3e5 move details |
| 513–540 | Material tracking after each move |
| 555–558 | Best move updates |

## Adding a New Test Position

```bash
cat << 'EOF' | java -cp build/classes:libs/chesslib-1.2.0.jar mybot.MyBot
uci
isready
ucinewgame
position startpos moves e2e4 e7e5 g1f3
go
quit
EOF
```

## Debugging Workflow

1. Identify the bad move (from game or manual play)
2. Capture the position as a move sequence or FEN
3. Add it to a debug script
4. Run `./debug_focused.sh` to save logs
5. Analyze with `grep` commands above
6. Fix in `ChessEngine.java`
7. Retest with debug scripts
8. Verify in a real game with `run_bot.sh`

## Script vs. Production Mode

| | `run_bot.sh` | `debug_*.sh` |
|-|-------------|-------------|
| Purpose | Production games | Position testing |
| Output | Minimal UCI | Extensive debug logs |
| Positions | From game in progress | Hardcoded test cases |
| Expected runtime | Indefinite | 10–30s (debug_test/focused), 1–5min (debug_deep) |

## Testing Move Ordering Fix

Run the dedicated verification script:
```bash
./test_ordering_fix.sh
```

Look for output where the TT move has the highest ordering score but the engine still chooses the move with the best eval:
```
Evaluating move: Qb3 -> eval: -50 cp  (ordering: 1000000000)  ← searched first
Evaluating move: Nxc6 -> eval: 892 cp (ordering: 10009000)    ← chosen as best
bestmove Nxc6  ✓
```
