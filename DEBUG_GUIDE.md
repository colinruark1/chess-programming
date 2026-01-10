# Chess Engine Debug Test Scripts

## Overview
Three debugging scripts have been created to help identify and diagnose issues with the chess engine. Each script serves a different purpose.

## Scripts

### 1. `debug_test.sh` - Basic Debug Mode
**Purpose:** Quick automated test of both problem positions
**Use when:** You want a fast check of both positions
**Run:** `./debug_test.sh`

**What it tests:**
- Position 1: `startpos moves e2e4 b7b5 d2d4 e7e5 d4e5 f8b4 b1c3`
- Position 2: `startpos moves e2e4 d7d5 e4d5 d8d5 g1f3`

**Output:** Console output with both tests run sequentially

---

### 2. `debug_deep.sh` - Deep Analysis Mode
**Purpose:** Interactive detailed testing with color-coded output
**Use when:** You want to examine each position carefully
**Run:** `./debug_deep.sh`

**Features:**
- Color-coded output for easier reading
- Pauses between tests (press Enter to continue)
- Tests 4 positions including baseline comparisons
- Provides analysis checklist after all tests

**Additional positions tested:**
- Simple opening (baseline)
- Position right before the problematic move

---

### 3. `debug_focused.sh` - Targeted Issue Hunter
**Purpose:** Attack specific known problems with detailed analysis
**Use when:** You need to diagnose exact failure points
**Run:** `./debug_focused.sh`

**Features:**
- Saves output to log files (`/tmp/test1_output.log`, `/tmp/test2_output.log`)
- Provides detailed explanation of what to watch for
- Includes grep commands for quick analysis
- Lists common problems and their indicators

**Log locations:**
- Test 1: `/tmp/test1_output.log`
- Test 2: `/tmp/test2_output.log`

---

## What to Look For in Output

### Material Scores
```
Raw material score: <number>
```
- Should be near 0 for balanced positions
- Should change by 900 when queen is captured
- Should change by 500 when rook is captured

### Move Evaluation
```
Evaluating move: <move> -> score: <number>
```
- Captures should score 10,000,000+
- Quiet moves should score < 100,000
- Best tactical moves should have highest scores

### Principal Variation (PV)
```
========== PRINCIPAL VARIATION ==========
Best score: <cp>
PV depth: <plies>
Best line: <move1> <move2> <move3> ...
```
- Should make tactical sense
- Should not give away material
- Should be 3+ moves deep typically

### Move Ordering Statistics
```
========== MOVE ORDERING STATISTICS ==========
First move cutoffs: <percent>
```
- First move cutoffs should be > 50%
- Indicates good move ordering
- Lower percentage = inefficient search

### Transposition Table
```
TT hits: <n>, misses: <m>, hit rate: <percent>
```
- Hit rate should be > 50% ideally
- Higher is better (more efficient search)

---

## Common Problems and Diagnosis

### Problem 1: Engine Makes Bad Moves
**Symptoms:**
- Best move gives away material
- Captures are not prioritized

**Check:**
1. Look for move scores in output
2. Verify captures score 10,000,000+
3. Check if evaluation reflects material

**Debug lines to examine:**
```bash
grep 'Evaluating move:' /tmp/test1_output.log | head -20
```

### Problem 2: Material Tracking Issues
**Symptoms:**
- Material score doesn't change after captures
- Evaluation doesn't reflect position

**Check:**
1. Watch "Raw material score" after position command
2. Look for material delta in debug output
3. Check piece tracker state

**Debug lines to examine:**
```bash
grep -i 'material' /tmp/test1_output.log
grep '\[DEBUG' /tmp/test1_output.log
```

### Problem 3: Move Ordering Broken
**Symptoms:**
- Low first move cutoff rate
- High branch factor
- Slow search

**Check:**
1. Move ordering statistics section
2. Verify TT move hits
3. Check killer move effectiveness

**Debug lines to examine:**
```bash
grep -A 20 'MOVE ORDERING STATISTICS' /tmp/test1_output.log
```

### Problem 4: Search Issues
**Symptoms:**
- PV makes no sense
- Very high/low evaluation scores
- Alpha-beta window errors

**Check:**
1. Principal variation output
2. Look for "Window error" messages
3. Check search depth

**Debug lines to examine:**
```bash
grep 'PRINCIPAL VARIATION' -A 10 /tmp/test1_output.log
grep -i 'window error' /tmp/test1_output.log
```

---

## Quick Analysis Commands

After running `debug_focused.sh`, use these commands for quick diagnosis:

### Show top evaluated moves
```bash
grep 'Evaluating move:' /tmp/test1_output.log | head -10
```

### Show material tracking
```bash
grep -i 'material' /tmp/test1_output.log
```

### Show best move and PV
```bash
grep -A 5 'PRINCIPAL VARIATION' /tmp/test1_output.log
```

### Show all debug messages
```bash
grep -E '\[DEBUG|ROOT|Ply\]' /tmp/test1_output.log
```

### Show move ordering effectiveness
```bash
grep -A 20 'MOVE ORDERING STATISTICS' /tmp/test1_output.log
```

### Compare move scores
```bash
grep 'Evaluating move:' /tmp/test1_output.log | sort -t: -k2 -n -r | head -20
```

---

## Understanding the Test Positions

### Position 1: `e2e4 b7b5 d2d4 e7e5 d4e5 f8b4 b1c3`
- After White plays Nc3
- Black has bishop on b4
- Material should be roughly equal
- Tests general move selection

### Position 2: `e2e4 d7d5 e4d5 d8d5 g1f3`
- Black queen is on d5 (exposed)
- Knight on f3 can develop
- Tests if engine recognizes tactical opportunities
- Queen capture should be high priority if available

---

## Debugging Workflow

1. **First run:** `./debug_focused.sh`
   - Saves detailed logs
   - Shows what to watch for

2. **Examine logs:**
   ```bash
   cat /tmp/test1_output.log
   ```

3. **Quick analysis:**
   ```bash
   grep 'Evaluating move:' /tmp/test1_output.log | head -20
   ```

4. **If issues found:**
   - Check material scores
   - Verify move ordering
   - Examine PV for tactical sense

5. **For detailed investigation:**
   - Run `./debug_deep.sh` for interactive analysis
   - Examine specific debug output for the failing position

---

## Expected Behavior

### Healthy Engine Output
```
There are 30 legal moves.
Searching at depth 5 (phase: OPENING, material: 0)
Evaluating move: Nc6 -> score: 892    # Example capture
Evaluating move: d6 -> score: 45      # Example quiet move
...
========== PRINCIPAL VARIATION ==========
Best score: 120 (cp)
PV depth: 5 plies
Best line: Nc6 Nxd5 Qxd5 ...
```

### Problematic Output
```
There are 30 legal moves.
Searching at depth 5 (phase: OPENING, material: 0)
Evaluating move: a4 -> score: -500    # Bad: quiet move scored high
Evaluating move: Nxd5 -> score: -800  # Bad: capture scored low
...
Best line: a4 a5 ...                  # Bad: nonsensical moves
```

---

## Integration with Main Bot

These scripts are **separate from** `run_bot.sh`. They are debugging tools.

- **run_bot.sh:** Used for actual games (UCI protocol)
- **debug_*.sh:** Used for testing specific positions

To debug during a real game, capture the position and test it with these scripts.
