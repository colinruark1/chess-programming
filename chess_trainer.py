#!/usr/bin/env python3
"""
chess_trainer.py — Human-guided positional evaluation trainer.

Streams random middlegame positions from a .pgn.zst file and presents them
one at a time in a pygame GUI. The user labels each position's evaluation;
the network updates via backpropagation. Tactic positions are skipped.

Network:  768 → 64 (ReLU) → 32 (ReLU) → 1  (centipawns, White's perspective)
Inputs:   (piece_value + PST_bonus) / 1000 per piece-square, white in [0..383],
          black in [384..767].  Mirrors the Java HCE exactly.
Weights:  hce_network.npz  (auto-loaded/saved)
Export:   hce_network.bin   (for HCENetwork.java — write via Save & Export button)
"""

import io
import random
import struct
import sys
from pathlib import Path

import chess
import chess.pgn
import numpy as np
import pygame
import zstandard as zstd

# ─── Tunable constants ──────────────────────────────────────────────────────────

POOL_SIZE          = 3000   # positions pre-loaded from PGN at startup
MIN_MOVE           = 10     # filter: earliest full-move number
MAX_MOVE           = 45     # filter: latest full-move number
MAX_MATERIAL_DELTA = 200    # filter: skip positions imbalanced by more than N cp
LR                 = 1e-4   # learning rate (kept low — positional fine-tuning)
SAVE_EVERY         = 10     # auto-save after this many labeled positions

WEIGHTS_FILE = "hce_network.npz"
EXPORT_FILE  = "hce_network.bin"

# ─── Layout ─────────────────────────────────────────────────────────────────────

SQ          = 60            # pixels per square
BOARD_PX    = SQ * 8       # 480
SIDEBAR_W   = 270
STATUS_H    = 44
WIN_W       = BOARD_PX + SIDEBAR_W
WIN_H       = BOARD_PX + STATUS_H

LIGHT  = (240, 217, 181)
DARK   = (181, 136,  99)
BG     = ( 30,  30,  30)
FG     = (230, 230, 230)
DIM    = (140, 140, 140)

# (label, target centipawns, RGB)
EVAL_LABELS = [
    ("Forced Mate  ▲",   3000, ( 14,  70, 180)),
    ("Very Winning ▲",    500, ( 40, 110, 210)),
    ("Winning      ▲",    200, ( 80, 150, 220)),
    ("Better       ▲",     75, (120, 180, 230)),
    ("Balanced",                  0, (100, 100, 100)),
    ("Better       ▼",    -75, (210, 150, 100)),
    ("Winning      ▼",   -200, (220, 110,  60)),
    ("Very Winning ▼",   -500, (210,  70,  30)),
    ("Forced Mate  ▼",  -3000, (170,  30,  14)),
]

# ─── PST tables — exact copy of PieceSquareTables.java ──────────────────────────
# Index convention: White → sq  (a1=0, h8=63),  Black → 63 - sq

_PAWN_MG = [
     0,  0,  0,  0,  0,  0,  0,  0,
    50, 50, 50, 50, 50, 50, 50, 50,
    10, 10, 20, 30, 30, 20, 10, 10,
     5,  5, 10, 27, 27, 10,  5,  5,
     0,  0,  0, 25, 25,  0,  0,  0,
     5, -5,-10,  0,  0,-10, -5,  5,
     5, 10, 10,-25,-25, 10, 10,  5,
     0,  0,  0,  0,  0,  0,  0,  0,
]
_PAWN_EG = [
      0,  0,  0,  0,  0,  0,  0,  0,
    150,150,150,150,150,150,150,150,
     80, 80, 80, 80, 80, 80, 80, 80,
     40, 40, 40, 50, 50, 40, 40, 40,
     20, 20, 20, 30, 30, 20, 20, 20,
     10, 10, 10, 15, 15, 10, 10, 10,
      5,  5,  5,  5,  5,  5,  5,  5,
      0,  0,  0,  0,  0,  0,  0,  0,
]
_KNIGHT = [
    -50,-40,-30,-30,-30,-30,-40,-50,
    -40,-20,  0,  0,  0,  0,-20,-40,
    -30,  0, 10, 15, 15, 10,  0,-30,
    -30,  5, 15, 20, 20, 15,  5,-30,
    -30,  0, 15, 20, 20, 15,  0,-30,
    -30,  5, 10, 15, 15, 10,  5,-30,
    -40,-20,  0,  5,  5,  0,-20,-40,
    -50,-40,-20,-30,-30,-20,-40,-50,
]
_BISHOP = [
    -20,-10,-10,-10,-10,-10,-10,-20,
    -10,  0,  0,  0,  0,  0,  0,-10,
    -10,  0,  5, 10, 10,  5,  0,-10,
    -10,  5,  5, 10, 10,  5,  5,-10,
    -10,  0, 10, 10, 10, 10,  0,-10,
    -10, 10, 10, 10, 10, 10, 10,-10,
    -10,  5,  0,  0,  0,  0,  5,-10,
    -20,-10,-40,-10,-10,-40,-10,-20,
]
_ROOK_MG = [
     0,  0,  0,  0,  0,  0,  0,  0,
    50, 50, 50, 50, 50, 50, 50, 50,
    -5,  0,  0,  0,  0,  0,  0, -5,
    -5,  0,  0,  0,  0,  0,  0, -5,
    -5,  0,  0,  0,  0,  0,  0, -5,
    -5,  0,  0,  0,  0,  0,  0, -5,
    -5,  0,  0,  0,  0,  0,  0, -5,
     0,  0,  0,  5,  5,  0,  0,  0,
]
_ROOK_EG = [
     0,  0,  0,  0,  0,  0,  0,  0,
     5,  5,  5,  5,  5,  5,  5,  5,
    -5,  0,  0,  0,  0,  0,  0, -5,
    -5,  0,  0,  0,  0,  0,  0, -5,
    -5,  0,  0,  0,  0,  0,  0, -5,
    -5,  0,  0,  0,  0,  0,  0, -5,
    -5,  0,  0,  0,  0,  0,  0, -5,
     0,  0,  0,  0,  0,  0,  0,  0,
]
_QUEEN_MG = [
    -20,-10,-10, -5, -5,-10,-10,-20,
    -10,  0,  0,  0,  0,  0,  0,-10,
    -10,  0,  5,  5,  5,  5,  0,-10,
     -5,  0,  5,  5,  5,  5,  0, -5,
      0,  0,  5,  5,  5,  5,  0, -5,
    -10,  5,  5,  5,  5,  5,  0,-10,
    -10,  0,  5,  0,  0,  0,  0,-10,
    -20,-10,-10, -5, -5,-10,-10,-20,
]
_QUEEN_EG = [
    -20,-10,-10, -5, -5,-10,-10,-20,
    -10,  0,  5,  5,  5,  5,  0,-10,
    -10,  5,  5,  5,  5,  5,  5,-10,
     -5,  0,  5,  5,  5,  5,  0, -5,
     -5,  0,  5,  5,  5,  5,  0, -5,
    -10,  0,  5,  5,  5,  5,  0,-10,
    -10,  0,  0,  0,  0,  0,  0,-10,
    -20,-10,-10, -5, -5,-10,-10,-20,
]
_KING_MG = [
    -30,-40,-40,-50,-50,-40,-40,-30,
    -30,-40,-40,-50,-50,-40,-40,-30,
    -30,-40,-40,-50,-50,-40,-40,-30,
    -30,-40,-40,-50,-50,-40,-40,-30,
    -20,-30,-30,-40,-40,-30,-30,-20,
    -10,-20,-20,-20,-20,-20,-20,-10,
     20, 20,  0,  0,  0,  0, 20, 20,
     20, 30, 10,  0,  0, 10, 30, 20,
]
_KING_EG = [
    -50,-40,-30,-20,-20,-30,-40,-50,
    -30,-20,-10,  0,  0,-10,-20,-30,
    -30,-10, 20, 30, 30, 20,-10,-30,
    -30,-10, 30, 40, 40, 30,-10,-30,
    -30,-10, 30, 40, 40, 30,-10,-30,
    -30,-10, 20, 30, 30, 20,-10,-30,
    -30,-30,  0,  0,  0,  0,-30,-30,
    -50,-30,-30,-30,-30,-30,-30,-50,
]

_BASE = {
    chess.PAWN: 100, chess.KNIGHT: 320, chess.BISHOP: 330,
    chess.ROOK: 500, chess.QUEEN:  900, chess.KING:     0,
}
_TYPE_IDX = {
    chess.PAWN: 0, chess.KNIGHT: 1, chess.BISHOP: 2,
    chess.ROOK: 3, chess.QUEEN:  4, chess.KING:   5,
}

# ─── Phase & PST helpers ────────────────────────────────────────────────────────

def _phase(board: chess.Board) -> str:
    mat = sum(_BASE[pt] * (len(board.pieces(pt, chess.WHITE)) + len(board.pieces(pt, chess.BLACK)))
              for pt in _BASE)
    return 'endgame' if mat < 3000 else ('opening' if mat >= 6400 else 'middlegame')

def _pst(pt: int, color: bool, sq: int, phase: str) -> int:
    idx = sq if color == chess.WHITE else 63 - sq
    eg  = phase == 'endgame'
    if   pt == chess.PAWN:   return _PAWN_EG[idx]   if eg else _PAWN_MG[idx]
    elif pt == chess.KNIGHT: return _KNIGHT[idx]
    elif pt == chess.BISHOP: return _BISHOP[idx]
    elif pt == chess.ROOK:   return _ROOK_EG[idx]   if eg else _ROOK_MG[idx]
    elif pt == chess.QUEEN:  return _QUEEN_EG[idx]  if eg else _QUEEN_MG[idx]
    elif pt == chess.KING:   return _KING_EG[idx]   if eg else _KING_MG[idx]
    return 0

# ─── Feature extraction ─────────────────────────────────────────────────────────

def board_features(board: chess.Board) -> np.ndarray:
    """
    768-element float32 input vector.
    [  0..383]: white piece values  — features[type_idx*64 + sq] = (base + PST) / 1000
    [384..767]: black piece values  — same layout, independent channel
    Values are signed (King PST can be mildly negative); no clamping applied.
    """
    feats = np.zeros(768, dtype=np.float32)
    ph = _phase(board)
    for sq in chess.SQUARES:
        p = board.piece_at(sq)
        if p is None:
            continue
        val = (_BASE[p.piece_type] + _pst(p.piece_type, p.color, sq, ph)) / 1000.0
        slot = _TYPE_IDX[p.piece_type] * 64 + sq
        feats[slot if p.color == chess.WHITE else 384 + slot] = val
    return feats

def hce_eval(board: chess.Board) -> int:
    """Hand-crafted eval in centipawns from White's perspective (mirrors Java)."""
    ph = _phase(board)
    score = 0
    for sq in chess.SQUARES:
        p = board.piece_at(sq)
        if p is None:
            continue
        val = _BASE[p.piece_type] + _pst(p.piece_type, p.color, sq, ph)
        score += val if p.color == chess.WHITE else -val
    return score

# ─── Network (768 → 64 → 32 → 1) ───────────────────────────────────────────────

_rng = np.random.default_rng(42)
W1 = _rng.normal(0, 0.01, (768, 64)).astype(np.float32)
b1 = np.zeros(64, dtype=np.float32)
W2 = _rng.normal(0, 0.05,  (64, 32)).astype(np.float32)
b2 = np.zeros(32, dtype=np.float32)
W3 = _rng.normal(0, 0.05,   32     ).astype(np.float32)
b3 = np.float32(0.0)


def forward(feats: np.ndarray):
    h1p = feats @ W1 + b1
    h1  = np.maximum(0.0, h1p)
    h2p = h1   @ W2 + b2
    h2  = np.maximum(0.0, h2p)
    out = float(h2 @ W3 + b3)
    return out, {'feats': feats, 'h1p': h1p, 'h1': h1, 'h2p': h2p, 'h2': h2}


def train_step(score: float, target: float, cache: dict) -> None:
    global b3
    d    = 2.0 * (score - target)          # dL/d_score (MSE)
    dW3  = d * cache['h2']
    db3  = d
    dh2  = d * W3 * (cache['h2p'] > 0)
    dW2  = np.outer(cache['h1'], dh2)
    db2  = dh2
    dh1  = (dh2 @ W2.T) * (cache['h1p'] > 0)
    dW1  = np.outer(cache['feats'], dh1)
    db1_ = dh1
    W1 -= LR * dW1
    b1 -= LR * db1_
    W2 -= LR * dW2
    b2 -= LR * db2
    W3 -= LR * dW3
    b3  = np.float32(float(b3) - LR * float(db3))


def save_weights() -> None:
    np.savez(WEIGHTS_FILE, W1=W1, b1=b1, W2=W2, b2=b2, W3=W3, b3=np.array([float(b3)]))
    print(f"Saved weights → {WEIGHTS_FILE}")


def load_weights() -> None:
    global W1, b1, W2, b2, W3, b3
    p = Path(WEIGHTS_FILE)
    if not p.exists():
        return
    d   = np.load(str(p))
    W1[:] = d['W1']; b1[:] = d['b1']
    W2[:] = d['W2']; b2[:] = d['b2']
    W3[:] = d['W3']; b3 = np.float32(float(d['b3'][0]))
    print(f"Loaded weights ← {WEIGHTS_FILE}")


def export_binary() -> None:
    """Write weights in little-endian float32 for HCENetwork.java."""
    with open(EXPORT_FILE, 'wb') as f:
        f.write(struct.pack('<I', 0x48434501))       # magic "HCE\x01"
        for arr in (W1.flatten(), b1,
                    W2.flatten(), b2,
                    W3, np.array([float(b3), ])):
            f.write(arr.astype('<f4').tobytes())
    print(f"Exported → {EXPORT_FILE}")

# ─── PGN streaming ──────────────────────────────────────────────────────────────

def load_positions(zst_path: str, n: int = POOL_SIZE) -> list[str]:
    positions: list[str] = []
    print(f"Loading up to {n} positions from {zst_path} …")
    with open(zst_path, 'rb') as fh:
        dctx = zstd.ZstdDecompressor()
        with dctx.stream_reader(fh) as reader:
            text = io.TextIOWrapper(reader, encoding='utf-8')
            while len(positions) < n:
                game = chess.pgn.read_game(text)
                if game is None:
                    break
                board = game.board()
                pool: list[str] = []
                for move in game.mainline_moves():
                    board.push(move)
                    mn = board.fullmove_number
                    if not (MIN_MOVE <= mn <= MAX_MOVE):
                        continue
                    if board.is_check():
                        continue
                    mat_diff = sum(
                        _BASE[pt] * (len(board.pieces(pt, chess.WHITE))
                                     - len(board.pieces(pt, chess.BLACK)))
                        for pt in _BASE
                    )
                    if abs(mat_diff) > MAX_MATERIAL_DELTA:
                        continue
                    pool.append(board.fen())
                if pool:
                    positions.append(random.choice(pool))
                if len(positions) % 500 == 0 and positions:
                    print(f"  {len(positions)}/{n}")
    random.shuffle(positions)
    print(f"Ready: {len(positions)} positions.")
    return positions

# ─── Piece images ───────────────────────────────────────────────────────────────

def load_piece_images() -> dict:
    names = {chess.PAWN:'pawn', chess.KNIGHT:'knight', chess.BISHOP:'bishop',
             chess.ROOK:'rook', chess.QUEEN:'queen',   chess.KING:'king'}
    imgs  = {}
    base  = Path(__file__).parent / 'pieces'
    for col, cname in ((chess.WHITE,'white'), (chess.BLACK,'black')):
        for pt, pname in names.items():
            p = base / f'{cname}-{pname}.png'
            if p.exists():
                img = pygame.image.load(str(p))
                imgs[(col, pt)] = pygame.transform.smoothscale(img, (SQ, SQ))
    return imgs

# ─── Board rendering ────────────────────────────────────────────────────────────

def draw_board(surface: pygame.Surface, board: chess.Board, imgs: dict) -> None:
    for rank in range(8):
        for file in range(8):
            sq  = chess.square(file, rank)
            x   = file * SQ
            y   = (7 - rank) * SQ
            col = LIGHT if (rank + file) % 2 == 0 else DARK
            pygame.draw.rect(surface, col, (x, y, SQ, SQ))
            p   = board.piece_at(sq)
            if p:
                img = imgs.get((p.color, p.piece_type))
                if img:
                    surface.blit(img, (x, y))

# ─── Button helpers ─────────────────────────────────────────────────────────────

def draw_button(surface, rect, label, color, font, hover=False, text_color=(255,255,255)):
    c = tuple(min(255, v + 35) for v in color) if hover else color
    pygame.draw.rect(surface, c, rect, border_radius=7)
    pygame.draw.rect(surface, (200, 200, 200), rect, 1, border_radius=7)
    txt = font.render(label, True, text_color)
    surface.blit(txt, (rect.x + (rect.w - txt.get_width()) // 2,
                       rect.y + (rect.h - txt.get_height()) // 2))

# ─── Main ────────────────────────────────────────────────────────────────────────

def main() -> None:
    zst_files = sorted(Path('.').glob('*.pgn.zst'))
    if not zst_files:
        print("Error: no .pgn.zst file found in the working directory.")
        sys.exit(1)
    zst_path = str(zst_files[0])
    print(f"PGN source: {zst_path}")

    load_weights()
    positions = load_positions(zst_path)
    if not positions:
        print("No qualifying positions found in the PGN. Exiting.")
        sys.exit(1)

    pygame.init()
    screen = pygame.display.set_mode((WIN_W, WIN_H))
    pygame.display.set_caption("HCE Positional Trainer")
    clock  = pygame.time.Clock()

    font_btn  = pygame.font.SysFont('Arial', 15, bold=True)
    font_info = pygame.font.SysFont('Arial', 14)
    font_sq   = pygame.font.SysFont('Arial', 11)

    imgs = load_piece_images()

    # ── Button layout ────────────────────────────────────────────────────────────
    SX    = BOARD_PX + 10        # sidebar x start
    BW    = SIDEBAR_W - 20       # button width
    BH    = 37
    GAP   = 5
    y_cur = 8

    eval_btns = []
    for label, target, color in EVAL_LABELS:
        eval_btns.append({
            'rect':   pygame.Rect(SX, y_cur, BW, BH),
            'label':  label,
            'target': target,
            'color':  color,
        })
        y_cur += BH + GAP

    y_cur += 8
    tactic_rect = pygame.Rect(SX, y_cur, BW, BH);         y_cur += BH + GAP
    save_rect   = pygame.Rect(SX, y_cur, BW, BH // 2 + 4)

    # ── State ────────────────────────────────────────────────────────────────────
    pos_idx  = 0
    labeled  = 0
    skipped  = 0

    def load_pos():
        b     = chess.Board(positions[pos_idx])
        feats = board_features(b)
        ev, c = forward(feats)
        hce   = hce_eval(b)
        return b, feats, ev, c, hce

    board, feats, net_ev, cache, hce = load_pos()

    running = True
    while running:
        mouse = pygame.mouse.get_pos()
        screen.fill(BG)

        # Board
        draw_board(screen, board, imgs)

        # Rank / file labels
        for i in range(8):
            lf = font_sq.render(chr(ord('a') + i), True, DIM)
            lr = font_sq.render(str(i + 1), True, DIM)
            screen.blit(lf, (i * SQ + SQ // 2 - lf.get_width() // 2, BOARD_PX - 14))
            screen.blit(lr, (3, (7 - i) * SQ + SQ // 2 - lr.get_height() // 2))

        # Eval buttons
        for btn in eval_btns:
            draw_button(screen, btn['rect'], btn['label'], btn['color'],
                        font_btn, hover=btn['rect'].collidepoint(mouse))

        # Tactic button
        draw_button(screen, tactic_rect, 'Tactic — Skip', (160, 150, 20),
                    font_btn, hover=tactic_rect.collidepoint(mouse), text_color=(0, 0, 0))

        # Save & Export button
        draw_button(screen, save_rect, 'Save & Export', (50, 140, 50),
                    font_sq, hover=save_rect.collidepoint(mouse))

        # Status bar
        stm   = "White" if board.turn == chess.WHITE else "Black"
        line1 = f"{stm} to move  |  Position {pos_idx + 1}/{len(positions)}"
        line2 = (f"HCE: {hce:+d} cp    Network: {net_ev:+.0f} cp"
                 f"    Labeled: {labeled}    Skipped: {skipped}")
        screen.blit(font_info.render(line1, True, FG),  (6, BOARD_PX + 6))
        screen.blit(font_info.render(line2, True, DIM), (6, BOARD_PX + 24))

        pygame.display.flip()
        clock.tick(60)

        def advance():
            nonlocal pos_idx, board, feats, net_ev, cache, hce
            pos_idx = (pos_idx + 1) % len(positions)
            board, feats, net_ev, cache, hce = load_pos()

        for event in pygame.event.get():
            if event.type == pygame.QUIT:
                save_weights()
                running = False

            elif event.type == pygame.MOUSEBUTTONDOWN and event.button == 1:
                pt = event.pos

                for btn in eval_btns:
                    if btn['rect'].collidepoint(pt):
                        train_step(net_ev, float(btn['target']), cache)
                        labeled += 1
                        if labeled % SAVE_EVERY == 0:
                            save_weights()
                        advance()
                        break

                if tactic_rect.collidepoint(pt):
                    skipped += 1
                    advance()

                if save_rect.collidepoint(pt):
                    save_weights()
                    export_binary()

    pygame.quit()


if __name__ == '__main__':
    main()
