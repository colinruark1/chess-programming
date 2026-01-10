# Move Ordering vs Move Quality - Critical Fix

## The Problem

The chess engine was confusing two fundamentally different concepts:

1. **Move Ordering** (search efficiency) - "Which move should we search first?"
2. **Move Quality** (tactical evaluation) - "How good is this move?"

### What Was Happening

The `scoreMove()` function was giving bonuses to moves based on:
- Being in the Transposition Table (TT)
- Being a killer move
- Tactical properties (captures, promotions)

The problem: **TT bonuses were being treated as if the move was better**, when really it just meant "we should search this move first for efficiency."

### The Bug in Action

**Before the fix:**
```java
if (isTTMove) {
    score = 100_000;  // Quiet TT move
} else if (move.equals(killerMoves[ply][0])) {
    score = 9_000;    // Killer move
}
```

This made the bot think:
- "We've seen this position before and tried move X"
- "Therefore move X must be good!"
- ❌ **This is wrong!**

### Why This Was Bad

The TT stores the best move from a **previous search** at that position. But:
- Previous search might have been at different depth
- Position might be approached from different move order
- The "best" move then might not be best in current context
- **Most importantly:** Being in the TT doesn't mean the move is good, it means we should check it first!

## The Solution

### Clear Separation of Concerns

**Move Ordering (scoreMove):**
- Purpose: Search efficiency only
- Used for: Sorting moves before search
- Goal: Find beta cutoffs faster
- **Does NOT indicate move quality**

**Move Evaluation (negamax return value):**
- Purpose: Actual position quality
- Measured in: Centipawns (cp)
- Based on: Material, mobility, tactics
- **This is what determines the best move**

### The Fix

```java
/**
 * Scores moves for ORDERING ONLY (search efficiency, not move quality).
 * Higher scores = search first (more likely to cause beta cutoff).
 * This does NOT indicate the move is tactically better!
 */
private int scoreMove(Move move, int ply) {
    // ...
    if (isTTMove) {
        // TT move gets highest priority for ORDERING
        // This doesn't mean it's good, just that we should search it first
        score = 1_000_000_000;
        // Add tactical bonuses on top for tie-breaking
        if (isPromotion) score += getPieceValue(move.getPromotion());
        else if (isCapture) {
            int mvv = getPieceValue(victim);
            int lva = getPieceValue(attacker);
            score += (mvv * 10 - lva);
        }
    }
    // ... rest of ordering logic
}
```

### Key Changes

1. **TT moves get top ordering priority** (1 billion) for search efficiency
2. **But evaluation is completely separate** - done by negamax()
3. **Debug output now shows both:**
   ```
   Evaluating move: Nc6 -> eval: 120 cp (ordering: 10009000)
   ```

## Understanding the Concepts

### Move Ordering (Search Efficiency)

Think of move ordering like organizing your search:
- "Let me check the most promising moves first"
- "If I find a good move early, I can skip checking bad moves"
- "This saves time but doesn't change which move is best"

**Good move ordering:**
1. TT move (we found it before, probably good)
2. Winning captures (take queen with pawn)
3. Equal captures (take knight with knight)
4. Killer moves (quiet moves that caused cutoffs)
5. Other quiet moves

### Move Evaluation (Actual Quality)

Think of evaluation like judging position quality:
- "After this move, am I up material?"
- "Do I have more mobility?"
- "Is my king safer?"
- "This determines which move I actually play"

**Evaluation factors:**
- Material balance (biggest factor)
- Piece mobility
- King safety
- Positional factors

## Why Move Ordering Matters

Even though ordering doesn't affect which move is best, it affects:

1. **Search speed** - Better ordering = fewer nodes searched
2. **Search depth** - Faster search = can search deeper
3. **Time management** - Finish faster = more time for other positions

### Example: Alpha-Beta Pruning

```
Position: White to move, 30 legal moves

Bad ordering (quiet moves first):
- Search 25 quiet moves (all score around 0)
- Finally search capturing queen (scores +900)
- Wasted time on 25 moves we didn't need to fully search!

Good ordering (captures first):
- Search capturing queen first (scores +900)
- Set alpha = 900
- Beta cutoff on most quiet moves (they can't beat +900)
- Only searched 5-6 moves total!
```

## Testing the Fix

Run the test script:
```bash
./test_ordering_fix.sh
```

### What to Look For

**Good Signs:**
```
Evaluating move: Nxc6 -> eval: 892 cp (ordering: 10009000)
Evaluating move: Qb3 -> eval: -120 cp (ordering: 1000000000)
...
bestmove Nxc6
```

Here:
- `Qb3` has higher ordering (TT move, 1 billion)
- But `Nxc6` has better eval (+892 vs -120)
- Bot correctly chooses `Nxc6` ✓

**Bad Signs:**
```
Evaluating move: d4 -> eval: -50 cp (ordering: 1000000000)
Evaluating move: Nxe5 -> eval: 850 cp (ordering: 10009000)
...
bestmove d4
```

Here:
- Bot chose `d4` which has worse eval (-50)
- Just because it had higher ordering score
- This would indicate the fix isn't working ✗

## Technical Details

### Where Ordering is Used

The `scoreMove()` function is ONLY called in these places:

1. **Line 164:** Root move ordering
   ```java
   legalMoves.sort((a, b) -> Integer.compare(scoreMove(b, 0), scoreMove(a, 0)));
   ```

2. **Line 492:** Negamax move ordering
   ```java
   legalMoves.sort((a, b) -> Integer.compare(scoreMove(b, ply), scoreMove(a, ply)));
   ```

3. **Line 664:** Quiescence move ordering
   ```java
   moves.sort((a, b) -> Integer.compare(scoreMove(b, -1), scoreMove(a, -1)));
   ```

All are for sorting moves before search - **never** for deciding which move to play.

### Where Evaluation is Used

The actual move choice is based on:

**Line 204:** Negamax evaluation
```java
int score = -negamax(depth - 1, -beta, -alpha, 1);
```

**Line 222-224:** Best move selection
```java
if (score > bestScore) {
    bestScore = score;
    bestMove = move;
}
```

The `score` here is centipawns (cp), not the ordering score!

## Before and After Comparison

### Before Fix

```
scoreMove() returned:
- TT quiet move: 100,000
- Non-TT capture: 10,009,000

Result: Capture ordered first ✓
BUT: If TT had higher eval bonus elsewhere, wrong move could be chosen ✗
```

### After Fix

```
scoreMove() returned:
- TT quiet move: 1,000,000,000 (ordering)
- Non-TT capture: 10,009,000 (ordering)

negamax() returned:
- TT quiet move: -50 cp (evaluation)
- Non-TT capture: +850 cp (evaluation)

Result:
- TT move searched first (efficient) ✓
- Capture chosen as best move (correct) ✓
```

## Related Files Changed

1. **ChessEngine.java** (lines 281-346)
   - Refactored `scoreMove()` with clear documentation
   - TT moves get 1 billion ordering priority
   - But evaluation is independent

2. **ChessEngine.java** (lines 163-173, 220-221)
   - Added debug output showing both ordering and eval
   - Clarified in comments what each score means

3. **test_ordering_fix.sh** (new file)
   - Verifies the fix is working
   - Shows both scores in output

## Summary

**What we fixed:**
- Separated move ordering (efficiency) from move evaluation (quality)
- TT moves now only get search priority, not evaluation bonuses
- Added debug output to show both scores

**Why it matters:**
- Bot no longer thinks moves are good just because they're in TT
- Move selection based on actual position quality
- Search is still efficient (TT moves searched first)

**How to verify:**
- Run `./test_ordering_fix.sh`
- Check that eval scores (not ordering) determine best move
- Confirm TT moves have high ordering but independent eval

This fix ensures the engine makes decisions based on **chess understanding** (material, tactics, position) rather than **search heuristics** (what we've seen before).
