# Chess Engine Testing & Debugging Scripts

## Quick Start

Run the test launcher:
```bash
./RUN_TESTS.sh
```

Or run individual tests directly:
```bash
./debug_test.sh         # Quick automated test
./debug_deep.sh         # Interactive detailed analysis
./debug_focused.sh      # Saves logs for later analysis
./test_ordering_fix.sh  # Verify move ordering vs quality separation
```

## Recent Fix: Move Ordering vs Move Quality

**CRITICAL FIX APPLIED:** The engine was conflating move ordering (search efficiency) with move quality (tactical evaluation).

**The Problem:**
- Moves were getting higher scores just because they were in the Transposition Table
- The bot thought "we've seen this move before" meant "this move is good"
- This caused poor move selection

**The Solution:**
- `scoreMove()` now ONLY handles search order (which move to search first)
- Actual move quality comes from `negamax()` evaluation (centipawn scores)
- TT moves get search priority but not evaluation bonuses

**How to Verify:**
```bash
./test_ordering_fix.sh
```

Look for output like:
```
Evaluating move: Nc6 -> eval: 120 cp (ordering: 10009000)
Evaluating move: d6 -> eval: -50 cp (ordering: 1000000000)
```

Here, `d6` has higher ordering (TT move, searched first) but lower eval (worse position).
The bot should choose `Nc6` because it has better eval.

## Test Positions

### Position 1: Problematic Position
```
position startpos moves e2e4 b7b5 d2d4 e7e5 d4e5 f8b4 b1c3
```
This is the position where the engine has been making poor moves. The test will show:
- Material tracking accuracy
- Move evaluation scores
- Whether captures are prioritized correctly
- Principal variation (best line found)

### Position 2: Queen Capture Test
```
position startpos moves e2e4 d7d5 e4d5 d8d5 g1f3
```
Tests whether the engine correctly handles exposed queen positions and prioritizes high-value captures.

## What The Tests Reveal

The debug scripts expose the following information:

### 1. Material Tracking
```
Raw material score: 0
```
- Shows if piece tracker is working correctly
- Should update after captures
- Should be ~0 for equal positions

### 2. Move Evaluation
```
Evaluating move: Nc6 -> score: 10000892
Evaluating move: d6 -> score: 45
```
- Shows how each move is scored
- Captures should be 10,000,000+
- Quiet moves should be < 100,000

### 3. Move Ordering
```
First move cutoffs: 523 (65.4%)
```
- Shows search efficiency
- Higher % = better move ordering
- Should be > 50% ideally

### 4. Search Analysis
```
========== PRINCIPAL VARIATION ==========
Best score: 120 (cp)
Best line: Nc6 Nxd5 Qxd5
```
- Shows the best line found
- Should make tactical sense
- Reveals if engine sees tactics

## Key Debugging Features

### Existing Debug Lines in ChessEngine.java

The engine already has extensive debugging built in:

1. **Line 191-202**: Debug for specific moves (d5e5)
2. **Line 478-483**: Ply 2 move logging
3. **Line 500-505**: f3e5 move details
4. **Line 513-540**: Material tracking after moves
5. **Line 555-558**: Best move updates

### What to Watch For

**Good signs:**
- ✓ Captures scored > 10,000,000
- ✓ Material balance accurate
- ✓ TT hit rate > 50%
- ✓ First move cutoffs > 50%
- ✓ PV makes tactical sense

**Bad signs:**
- ✗ Quiet moves scored higher than captures
- ✗ Material doesn't change after captures
- ✗ Best move gives away material
- ✗ Very low/high evaluation scores
- ✗ Nonsensical PV

## Using the Output Logs

When you run `debug_focused.sh`, logs are saved:

```bash
# View test 1 output
cat /tmp/test1_output.log

# View test 2 output
cat /tmp/test2_output.log

# Quick analysis commands
grep 'Evaluating move:' /tmp/test1_output.log | head -20
grep -i 'material' /tmp/test1_output.log
grep 'PRINCIPAL VARIATION' -A 10 /tmp/test1_output.log
```

## Comparison to run_bot.sh

| Feature | run_bot.sh | debug_*.sh |
|---------|-----------|------------|
| Purpose | Run bot for games | Test specific positions |
| Protocol | UCI | UCI |
| Output | Minimal | Extensive debugging |
| Use case | Production | Development/debugging |
| Positions | From game | Hardcoded test cases |

## Workflow for Debugging

1. **Identify problem** (e.g., bad move in game)
2. **Capture position** (get FEN or move sequence)
3. **Add to test script** (edit debug_test.sh)
4. **Run tests** (`./debug_focused.sh`)
5. **Analyze output** (check logs)
6. **Fix issue** (modify ChessEngine.java)
7. **Retest** (run scripts again)
8. **Verify** (use run_bot.sh in real game)

## Expected Test Runtime

- `debug_test.sh`: ~10-30 seconds
- `debug_deep.sh`: 1-5 minutes (interactive)
- `debug_focused.sh`: ~10-30 seconds

Times vary based on search depth and position complexity.

## Adding New Test Positions

To add a new test position, edit the script and add:

```bash
cat << 'EOF' | java -cp build/classes:libs/chesslib-1.2.0.jar mybot.MyBot
uci
isready
ucinewgame
position startpos moves e2e4 e7e5 g1f3  # Your moves here
go
quit
EOF
```

## Troubleshooting

**Problem: Scripts don't run**
```bash
chmod +x *.sh  # Make all scripts executable
```

**Problem: Compilation fails**
- Check that `libs/chesslib-1.2.0.jar` exists
- Verify Java is installed: `java -version`
- Ensure all .java files are present

**Problem: No output**
- Check that UCI commands are formatted correctly
- Ensure `quit` command is at the end
- Verify bot responds to `uci` command

**Problem: Can't find logs**
- Logs are in `/tmp/test1_output.log` and `/tmp/test2_output.log`
- Use `ls -lh /tmp/test*.log` to verify

## Integration with Version Control

Recommended `.gitignore` entries:
```
/tmp/test*.log
manifest.txt
build/
*.class
```

The test scripts themselves should be committed to track debugging capabilities over time.

## Further Reading

See [DEBUG_GUIDE.md](DEBUG_GUIDE.md) for comprehensive debugging information including:
- Detailed explanation of each script
- Common problems and solutions
- Analysis commands
- Expected vs problematic output examples
