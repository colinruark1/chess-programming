#!/usr/bin/env python3
"""
nnue_trainer.py  –  Train a simplified NNUE from Lichess PGN data.

Architecture (must match NNUE.java exactly):
  Half-KP features (1 king bucket): 768 inputs
  → Feature Transform: 768 → FT_WIDTH=256, clippedReLU, built separately per side
  → Concat [stm | opp]: 512
  → L1: 512 → 32, clippedReLU
  → L2:  32 → 32, clippedReLU
  → Out:  32 → 1,  linear

Output file: binary nncpu-probe format (little-endian float32), N_K_INDICES=1.
NNUE.java will auto-detect N_KI=1 from file size and load it directly.

Usage:
  python nnue_trainer.py [path/to/file.pgn.zst] [--games N] [--out trained.nnue]
"""

import argparse
import io
import random
import struct
from pathlib import Path

import chess
import chess.pgn
import numpy as np
import zstandard as zstd

# ── Architecture (must match NNUE.java constants) ─────────────────────────────
N_KI     = 1    # King-bucket count; 1 = no bucketing (simplest)
FT_WIDTH = 256  # Feature-transform output width
L1       = 32   # Hidden layer 1 neurons
L2       = 32   # Hidden layer 2 neurons

# ── Training hyper-parameters ─────────────────────────────────────────────────
LR            = 0.001
WEIGHT_DECAY  = 1e-5
BATCH_SIZE    = 256

# Position-filter defaults (overridable via CLI)
DEFAULT_GAMES      = 5000
DEFAULT_MIN_ELO    = 2000
DEFAULT_MAT_DELTA  = 1.5   # max pawn-equivalent imbalance
DEFAULT_MOVE_LO    = 10    # min full-move number (middlegame)
DEFAULT_MOVE_HI    = 40    # max full-move number

DEFAULT_ZST  = "lichess_db_standard_rated_2024-11.pgn.zst"
DEFAULT_OUT  = "trained.nnue"

# ── Material values for balance filter ───────────────────────────────────────
_MAT = {
    chess.PAWN: 1.0, chess.KNIGHT: 3.0, chess.BISHOP: 3.1,
    chess.ROOK: 5.0, chess.QUEEN:  9.0,
}

# ─────────────────────────────────────────────────────────────────────────────
# Network weights  (module-level, modified in-place throughout training)
# ─────────────────────────────────────────────────────────────────────────────
_rng  = np.random.default_rng(42)

# ft_w[feature_idx, ft_out] – feature_idx in [0, N_KI*768)
ft_w  = _rng.normal(0, 0.01, (N_KI * 768, FT_WIDTH)).astype(np.float32)
ft_b  = np.zeros(FT_WIDTH, np.float32)

# h1_w[in, out] – input-major, shape [2*FT_WIDTH, L1]
h1_w  = _rng.normal(0, 0.01, (2 * FT_WIDTH, L1)).astype(np.float32)
h1_b  = np.zeros(L1, np.float32)

# h2_w[in, out] – input-major, shape [L1, L2]
h2_w  = _rng.normal(0, 0.01, (L1, L2)).astype(np.float32)
h2_b  = np.zeros(L2, np.float32)

out_w = _rng.normal(0, 0.01, L2).astype(np.float32)
out_b = np.zeros(1, np.float32)

# ─────────────────────────────────────────────────────────────────────────────
# Gradient accumulators (zeroed each batch)
# ─────────────────────────────────────────────────────────────────────────────
g_ft_w  = np.zeros_like(ft_w)
g_ft_b  = np.zeros_like(ft_b)
g_h1_w  = np.zeros_like(h1_w)
g_h1_b  = np.zeros_like(h1_b)
g_h2_w  = np.zeros_like(h2_w)
g_h2_b  = np.zeros_like(h2_b)
g_out_w = np.zeros_like(out_w)
g_out_b = np.zeros_like(out_b)


def _zero_grads() -> None:
    for g in (g_ft_w, g_ft_b, g_h1_w, g_h1_b, g_h2_w, g_h2_b, g_out_w, g_out_b):
        g[:] = 0.0


# ─────────────────────────────────────────────────────────────────────────────
# Feature extraction  (mirrors NNUE.java buildAccum exactly)
# ─────────────────────────────────────────────────────────────────────────────

def _build_accum(board: chess.Board, side: bool) -> tuple[np.ndarray, list[int]]:
    """
    Compute the FT_WIDTH pre-activation accumulator for `side`.

    Returns (accum, active_feature_indices).
    side: chess.WHITE (True) or chess.BLACK (False).
    """
    king_sq = board.king(side)
    if king_sq is None:
        return ft_b.copy(), []

    # King-perspective normalisation (mirrors Java buildAccum)
    flip_rank = (side == chess.BLACK)
    f = chess.square_file(king_sq)
    r = chess.square_rank(king_sq)
    flip_file = (f < 4)
    if flip_rank:
        r = 7 - r
    if flip_file:
        f = 7 - f
    kidx = 0  # N_KI = 1 → always bucket 0

    accum   = ft_b.copy()
    active: list[int] = []

    for sq in chess.SQUARES:
        piece = board.piece_at(sq)
        if piece is None:
            continue

        pt    = piece.piece_type   # 1=PAWN .. 6=KING (matches java Piece.type)
        white = piece.color        # True = WHITE

        # Map to nncpu 1-indexed piece code:
        #   white: KING=1, QUEEN=2, ROOK=3, BISHOP=4, KNIGHT=5, PAWN=6
        #   black: adds 6 to the above
        # Java: nnPc = (7 - Piece.type(p)) + (Piece.color(p)==BLACK ? 6 : 0)
        nn_pc = (7 - pt) + (0 if white else 6)

        tsq = sq
        tpc = nn_pc
        if flip_rank:
            tsq ^= 56                                       # mirror rank
            tpc = (tpc - 6) if (tpc > 6) else (tpc + 6)   # swap color
        if flip_file:
            tsq ^= 7                                        # mirror file

        # Feature index: (kidx * 768 + (tpc-1) * 64 + tsq)
        fidx = kidx * 768 + (tpc - 1) * 64 + tsq
        accum += ft_w[fidx]
        active.append(fidx)

    return accum, active


# ─────────────────────────────────────────────────────────────────────────────
# Forward pass  (mirrors NNUE.java evaluate)
# ─────────────────────────────────────────────────────────────────────────────

def forward(board: chess.Board) -> tuple[float, dict]:
    """
    Full forward pass.  Returns (raw_score, cache) where cache carries all
    intermediate values needed for backpropagation.
    raw_score is the unscaled network output (not centipawns).
    """
    w_acc, w_act = _build_accum(board, chess.WHITE)
    b_acc, b_act = _build_accum(board, chess.BLACK)

    stm = board.turn  # True = WHITE
    stm_acc, stm_act = (w_acc, w_act) if stm else (b_acc, b_act)
    opp_acc, opp_act = (b_acc, b_act) if stm else (w_acc, w_act)

    # Clipped ReLU on each 256-element half; save derivative masks
    stm_c = np.clip(stm_acc, 0.0, 1.0)
    opp_c = np.clip(opp_acc, 0.0, 1.0)
    stm_m = (stm_acc > 0.0) & (stm_acc < 1.0)  # mask: 1 where gradient flows
    opp_m = (opp_acc > 0.0) & (opp_acc < 1.0)

    in_vec = np.concatenate([stm_c, opp_c])      # [512]

    h1_pre = h1_b + in_vec @ h1_w                # [32]
    h1     = np.clip(h1_pre, 0.0, 1.0)
    h1_m   = (h1_pre > 0.0) & (h1_pre < 1.0)

    h2_pre = h2_b + h1 @ h2_w                    # [32]
    h2     = np.clip(h2_pre, 0.0, 1.0)
    h2_m   = (h2_pre > 0.0) & (h2_pre < 1.0)

    score = float(out_b[0] + h2 @ out_w)         # scalar

    cache = dict(
        stm_acc=stm_acc, opp_acc=opp_acc,
        stm_m=stm_m,     opp_m=opp_m,
        stm_act=stm_act, opp_act=opp_act,
        in_vec=in_vec,
        h1=h1, h1_m=h1_m,
        h2=h2, h2_m=h2_m,
    )
    return score, cache


# ─────────────────────────────────────────────────────────────────────────────
# Backward pass  (MSE loss, accumulates into gradient arrays)
# ─────────────────────────────────────────────────────────────────────────────

def backward(score: float, target: float, cache: dict) -> None:
    d = 2.0 * (score - target)           # d_loss / d_score  (MSE gradient)

    # Output layer
    g_out_w[:] += d * cache['h2']
    g_out_b[:] += d

    # L2
    d_h2    = d * out_w * cache['h2_m']  # [32]
    g_h2_w[:] += np.outer(cache['h1'], d_h2)
    g_h2_b[:] += d_h2

    # L1
    d_h1    = (d_h2 @ h2_w.T) * cache['h1_m']   # [32]
    g_h1_w[:] += np.outer(cache['in_vec'], d_h1)
    g_h1_b[:] += d_h1

    # Feature transform
    d_in    = d_h1 @ h1_w.T                      # [512]
    d_stm   = d_in[:FT_WIDTH] * cache['stm_m']   # [256]
    d_opp   = d_in[FT_WIDTH:] * cache['opp_m']   # [256]

    # ft_b receives gradient from both perspectives
    g_ft_b[:] += d_stm + d_opp

    for fidx in cache['stm_act']:
        g_ft_w[fidx] += d_stm
    for fidx in cache['opp_act']:
        g_ft_w[fidx] += d_opp


# ─────────────────────────────────────────────────────────────────────────────
# SGD update
# ─────────────────────────────────────────────────────────────────────────────

def _apply_gradients(n: int) -> None:
    """Apply accumulated batch gradients with L2 weight decay, then zero them."""
    s  = LR / max(n, 1)
    wd = LR * WEIGHT_DECAY

    ft_w[:]  -= s * g_ft_w  + wd * ft_w
    ft_b[:]  -= s * g_ft_b
    h1_w[:]  -= s * g_h1_w  + wd * h1_w
    h1_b[:]  -= s * g_h1_b
    h2_w[:]  -= s * g_h2_w  + wd * h2_w
    h2_b[:]  -= s * g_h2_b
    out_w[:] -= s * g_out_w + wd * out_w
    out_b[:] -= s * g_out_b

    _zero_grads()


# ─────────────────────────────────────────────────────────────────────────────
# PGN streaming  (filter for high-ELO, balanced, middlegame positions)
# ─────────────────────────────────────────────────────────────────────────────

def stream_positions(
    zst_path: str,
    max_games: int,
    min_elo: int,
    mat_delta: float,
    move_lo: int,
    move_hi: int,
):
    """
    Yield (fen, result) pairs from a .pgn.zst file.
    result is float in {0.0, 0.5, 1.0} (0=black win, 0.5=draw, 1=white win).
    One random balanced middlegame position is chosen per qualifying game.
    """
    with open(zst_path, 'rb') as fh:
        dctx = zstd.ZstdDecompressor()
        with dctx.stream_reader(fh) as reader:
            text       = io.TextIOWrapper(reader, encoding='utf-8')
            game_count = 0

            while game_count < max_games:
                game = chess.pgn.read_game(text)
                if game is None:
                    break

                # ELO filter
                try:
                    w_elo = int(game.headers.get("WhiteElo", 0) or 0)
                    b_elo = int(game.headers.get("BlackElo", 0) or 0)
                except ValueError:
                    continue
                if w_elo < min_elo or b_elo < min_elo:
                    continue

                # Result filter (skip unfinished games)
                result_str = game.headers.get("Result", "*")
                result_map = {"1-0": 1.0, "0-1": 0.0, "1/2-1/2": 0.5}
                if result_str not in result_map:
                    continue
                result = result_map[result_str]

                # Walk moves, collect balanced middlegame positions
                board = game.board()
                pool: list[str] = []

                for move in game.mainline_moves():
                    board.push(move)

                    if not (move_lo < board.fullmove_number < move_hi):
                        continue

                    mat_diff = sum(
                        v * (len(board.pieces(pt, chess.WHITE)) -
                             len(board.pieces(pt, chess.BLACK)))
                        for pt, v in _MAT.items()
                    )
                    if abs(mat_diff) <= mat_delta:
                        pool.append(board.fen())

                if pool:
                    game_count += 1
                    yield random.choice(pool), result


# ─────────────────────────────────────────────────────────────────────────────
# Weight export  (nncpu-probe binary format, N_K_INDICES=1)
# ─────────────────────────────────────────────────────────────────────────────

def save_nnue(path: str) -> None:
    """
    Write network weights in the binary format expected by NNUE.java load().

    Layout (little-endian float32):
      [4 bytes]  version = 0x00000000
      [sq(64) × ki(N_KI) × pc(12) × FT_WIDTH]  input weights
      [FT_WIDTH]                                  input biases
      [2*FT_WIDTH × L1]                           hidden-1 weights (input-major)
      [L1]                                        hidden-1 biases
      [L1 × L2]                                   hidden-2 weights (input-major)
      [L2]                                        hidden-2 biases
      [L2]                                        output weights
      [1]                                         output bias
    """
    with open(path, 'wb') as f:
        # Version header
        f.write(struct.pack('<I', 0))

        # Input weights: write order [sq][ki][pc][ft]
        # In memory: ft_w[ki*768 + pc*64 + sq, ft]
        for sq in range(64):
            for ki in range(N_KI):
                for pc in range(12):
                    row = ft_w[ki * 768 + pc * 64 + sq]
                    f.write(row.astype('<f4').tobytes())

        # Input biases
        f.write(ft_b.astype('<f4').tobytes())

        # Hidden-1: shape [2*FT_WIDTH, L1] is already input-major
        # Java reads h1w[i * L1 + j] → tobytes() gives the right layout
        f.write(h1_w.astype('<f4').tobytes())
        f.write(h1_b.astype('<f4').tobytes())

        # Hidden-2: shape [L1, L2]
        f.write(h2_w.astype('<f4').tobytes())
        f.write(h2_b.astype('<f4').tobytes())

        # Output
        f.write(out_w.astype('<f4').tobytes())
        f.write(struct.pack('<f', float(out_b[0])))

    size = Path(path).stat().st_size
    expected = 4 + (N_KI * 768 * FT_WIDTH + FT_WIDTH +
                    2 * FT_WIDTH * L1 + L1 +
                    L1 * L2 + L2 + L2 + 1) * 4
    assert size == expected, f"File size mismatch: got {size}, expected {expected}"
    print(f"Saved → {path}  ({size:,} bytes, N_KI={N_KI})")


# ─────────────────────────────────────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────────────────────────────────────

def main() -> None:
    parser = argparse.ArgumentParser(
        description="Train an NNUE from a Lichess .pgn.zst file and export "
                    "weights in nncpu-probe format for NNUE.java."
    )
    parser.add_argument("zst_file",  nargs="?", default=DEFAULT_ZST,
                        help="Path to .pgn.zst source file")
    parser.add_argument("--games",   type=int,   default=DEFAULT_GAMES,
                        help="Max games to sample from (default: %(default)s)")
    parser.add_argument("--min-elo", type=int,   default=DEFAULT_MIN_ELO,
                        help="Min ELO for both players (default: %(default)s)")
    parser.add_argument("--out",     default=DEFAULT_OUT,
                        help="Output .nnue file (default: %(default)s)")
    args = parser.parse_args()

    print(f"NNUE Trainer  (N_KI={N_KI}, FT={FT_WIDTH}, L1={L1}, L2={L2})")
    print(f"Source  : {args.zst_file}")
    print(f"Games   : {args.games}  |  min ELO: {args.min_elo}")
    print(f"Output  : {args.out}")
    print()

    total_loss    = 0.0
    report_loss   = 0.0
    batch_n       = 0
    total_samples = 0
    report_n      = 0

    gen = stream_positions(
        args.zst_file, args.games, args.min_elo,
        DEFAULT_MAT_DELTA, DEFAULT_MOVE_LO, DEFAULT_MOVE_HI,
    )

    for i, (fen, result) in enumerate(gen):
        board  = chess.Board(fen)
        stm    = board.turn   # True = WHITE

        # Training target from STM perspective: +1 = stm winning, -1 = losing
        target = (result - 0.5) * 2.0 * (1.0 if stm else -1.0)

        score, cache = forward(board)
        backward(score, target, cache)

        err           = (score - target) ** 2
        total_loss   += err
        report_loss  += err
        batch_n      += 1
        total_samples += 1
        report_n      += 1

        if batch_n >= BATCH_SIZE:
            _apply_gradients(batch_n)
            batch_n = 0

        if report_n >= 500:
            print(f"  Sample {total_samples:6d}  avg MSE: {report_loss / report_n:.6f}")
            report_loss = 0.0
            report_n    = 0

    # Final partial batch
    if batch_n > 0:
        _apply_gradients(batch_n)

    if total_samples == 0:
        print("No qualifying positions found. Check your ELO filter and file path.")
        return

    print(f"\nTraining complete. Samples: {total_samples}  "
          f"overall avg MSE: {total_loss / total_samples:.6f}")
    save_nnue(args.out)
    print()
    print("To use the trained network, place the .nnue file next to the JAR")
    print("and start the engine — it will load automatically.")
    print("  or in UCI mode: setoption name NNUEPath value trained.nnue")


if __name__ == "__main__":
    main()
