package mybot;

import java.io.*;
import java.nio.*;

/**
 * Non-STOCK nncpu-probe network loader and evaluator.
 *
 * Binary format (little-endian):
 *   4 bytes  : version = 0x00000000
 *   [sq(64) × kidx(N) × pc(12) × FT_WIDTH] float32  : input weights
 *   [FT_WIDTH]                               float32  : input biases
 *   [2*FT_WIDTH × L1] float32 (input-major) : hidden-1 weights
 *   [L1]               float32              : hidden-1 biases
 *   [L1 × L2]          float32 (input-major): hidden-2 weights
 *   [L2]               float32              : hidden-2 biases
 *   [L2]               float32              : output weights
 *   1                  float32              : output bias
 *
 * N (king bucket count) is auto-detected from file size.
 * Supported values: 1, 2, 4, 8, 16, 32.
 */
public class NNUE {

    // ── Architecture constants ────────────────────────────────────────────────
    private static final int FT_WIDTH = 256;
    private static final int L1       = 32;
    private static final int L2       = 32;

    // Converts raw network output to centipawns (matches nncpu-probe's formula).
    private static final float OUTPUT_SCALE = (float)(1.0 / 0.00575646273);

    // ── King-bucket lookup tables (from nncpu-probe KINDEX) ──────────────────
    // Index: r*4 + (f-4), where r/f are the king's rank/file after perspective
    // normalization (rank flipped for black, file folded to right half).
    private static final int[] KINDEX_32 = {
         0, 1, 2, 3, 4, 5, 6, 7, 8, 9,10,11,
        12,13,14,15,16,17,18,19,20,21,22,23,
        24,25,26,27,28,29,30,31
    };
    private static final int[] KINDEX_16 = {
         0, 1, 2, 3, 4, 5, 6, 7, 8, 8, 9, 9,
        10,10,11,11,12,12,13,13,12,12,13,13,
        14,14,15,15,14,14,15,15
    };
    private static final int[] KINDEX_8 = {
        0,1,2,3,4,4,5,5,6,6,6,6,
        7,7,7,7,7,7,7,7,7,7,7,7,
        7,7,7,7,7,7,7,7
    };
    private static final int[] KINDEX_4 = {
        0,0,1,1,2,2,2,2,3,3,3,3,
        3,3,3,3,3,3,3,3,3,3,3,3,
        3,3,3,3,3,3,3,3
    };
    private static final int[] KINDEX_2 = {
        0,0,0,0,1,1,1,1,1,1,1,1,
        1,1,1,1,1,1,1,1,1,1,1,1,
        1,1,1,1,1,1,1,1
    };
    private static final int[] KINDEX_1 = new int[32]; // all zeros

    // ── Loaded network weights ────────────────────────────────────────────────
    private int     nKi;   // N_K_INDICES (auto-detected)
    private int[]   kt;    // active KINDEX table
    private float[] iw;    // input weights  [nKi * 768 * FT_WIDTH]
    private float[] ib;    // input biases   [FT_WIDTH]
    private float[] h1w;   // hidden-1 weights [2*FT_WIDTH * L1], input-major
    private float[] h1b;   // hidden-1 biases  [L1]
    private float[] h2w;   // hidden-2 weights [L1 * L2], input-major
    private float[] h2b;   // hidden-2 biases  [L2]
    private float[] ow;    // output weights  [L2]
    private float   ob;    // output bias

    // ── Per-evaluation reusable buffers (no GC pressure during search) ────────
    private final float[] wAccum = new float[FT_WIDTH];
    private final float[] bAccum = new float[FT_WIDTH];
    private final float[] inVec  = new float[2 * FT_WIDTH];
    private final float[] h1Out  = new float[L1];
    private final float[] h2Out  = new float[L2];

    private boolean ready = false;

    // ── Loading ───────────────────────────────────────────────────────────────

    /**
     * Loads a non-STOCK nncpu-probe weight file (float, version 0x00000000).
     * Reads the whole file into a ByteBuffer then populates float arrays;
     * the raw byte array is released after this method returns.
     */
    public void load(String path) throws IOException {
        File file = new File(path);
        long len  = file.length();

        // Detect N_K_INDICES from file size.
        // File = 4 header bytes + (N*196608 + 17761) floats * 4 bytes.
        if ((len - 4) % 4 != 0)
            throw new IOException("File size not divisible by 4: " + len);
        long nFloats = (len - 4) / 4;
        long n = (nFloats - 17761) / 196608;
        if (n < 1 || n > 32 || (nFloats - 17761) % 196608 != 0)
            throw new IOException("Unrecognised network file size " + len
                + " (expected a non-STOCK nncpu-probe float net)");
        nKi = (int) n;
        kt = switch (nKi) {
            case 32 -> KINDEX_32; case 16 -> KINDEX_16; case 8 -> KINDEX_8;
            case 4  -> KINDEX_4;  case 2  -> KINDEX_2;  default -> KINDEX_1;
        };

        // Bulk-read then release — avoids keeping both byte[] and float[] live.
        byte[] raw = new byte[(int) len];
        try (FileInputStream fis = new FileInputStream(file)) {
            int off = 0;
            while (off < raw.length) off += fis.read(raw, off, raw.length - off);
        }
        ByteBuffer bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);

        int ver = bb.getInt();
        if (ver != 0)
            throw new IOException("Expected non-STOCK version 0x00000000, got 0x"
                + Integer.toHexString(ver));

        // Input weights: file order = [sq][kidx][pc][ft].
        // Stored in iw as feature-row-major: iw[(kidx*768 + pc*64 + sq)*FT_WIDTH + ft].
        iw = new float[nKi * 768 * FT_WIDTH];
        for (int sq = 0; sq < 64; sq++)
            for (int ki = 0; ki < nKi; ki++)
                for (int pc = 0; pc < 12; pc++) {
                    int base = (ki * 768 + pc * 64 + sq) * FT_WIDTH;
                    for (int ft = 0; ft < FT_WIDTH; ft++)
                        iw[base + ft] = bb.getFloat();
                }

        ib  = readFloats(bb, FT_WIDTH);

        // Hidden weights stored input-major (the "transposed" convention in the source):
        //   h1w[i * L1 + j]  for input i, output j
        //   h2w[i * L2 + j]  for input i, output j
        h1w = readFloats(bb, 2 * FT_WIDTH * L1);
        h1b = readFloats(bb, L1);
        h2w = readFloats(bb, L1 * L2);
        h2b = readFloats(bb, L2);
        ow  = readFloats(bb, L2);
        ob  = bb.getFloat();

        ready = true;
        System.out.printf("info string NNUE loaded: N_K_INDICES=%d, %.1f KB%n",
            nKi, len / 1024.0);
    }

    private static float[] readFloats(ByteBuffer bb, int count) {
        float[] a = new float[count];
        for (int i = 0; i < count; i++) a[i] = bb.getFloat();
        return a;
    }

    public boolean isReady() { return ready; }

    // ── Evaluation ────────────────────────────────────────────────────────────

    /**
     * Returns the evaluation in centipawns from the perspective of the
     * side to move (positive = side to move is winning).
     */
    public int evaluate(Board board) {
        int stm = board.sideToMove();

        buildAccum(board, Piece.WHITE, wAccum);
        buildAccum(board, Piece.BLACK, bAccum);

        // Assemble 512-element input vector: [stm perspective | opponent perspective],
        // with clipped ReLU applied to each 256-element half.
        float[] stmA = (stm == Piece.WHITE) ? wAccum : bAccum;
        float[] oppA = (stm == Piece.WHITE) ? bAccum : wAccum;
        for (int i = 0; i < FT_WIDTH; i++) {
            inVec[i]            = clamp01(stmA[i]);
            inVec[i + FT_WIDTH] = clamp01(oppA[i]);
        }

        // L1: 512 → 32 with clipped ReLU
        for (int j = 0; j < L1; j++) {
            float s = h1b[j];
            for (int i = 0; i < 2 * FT_WIDTH; i++) s += inVec[i] * h1w[i * L1 + j];
            h1Out[j] = clamp01(s);
        }

        // L2: 32 → 32 with clipped ReLU
        for (int j = 0; j < L2; j++) {
            float s = h2b[j];
            for (int i = 0; i < L1; i++) s += h1Out[i] * h2w[i * L2 + j];
            h2Out[j] = clamp01(s);
        }

        // Output: 32 → 1 linear
        float score = ob;
        for (int i = 0; i < L2; i++) score += h2Out[i] * ow[i];

        return (int)(score * OUTPUT_SCALE);
    }

    private static float clamp01(float x) {
        return x < 0f ? 0f : (x > 1f ? 1f : x);
    }

    // ── Accumulator building ──────────────────────────────────────────────────

    /**
     * Builds the FT_WIDTH-element accumulator for the given side by adding the
     * weighted contribution of every piece on the board, using that side's king
     * position to determine the king bucket.
     */
    private void buildAccum(Board board, int side, float[] accum) {
        System.arraycopy(ib, 0, accum, 0, FT_WIDTH);

        // Find this side's king square.
        int kingSq = -1;
        for (int sq = 0; sq < 64 && kingSq < 0; sq++) {
            int p = board.pieceAt(sq);
            if (p != Piece.EMPTY && Piece.type(p) == Piece.KING && Piece.color(p) == side)
                kingSq = sq;
        }
        if (kingSq < 0) return;

        // Compute king bucket index.
        // Black's perspective: flip rank so black's back rank is rank 0.
        // File fold: always normalize the king to the right half of the board.
        boolean flipRank = (side == Piece.BLACK);
        int f = kingSq & 7;
        int r = kingSq >> 3;
        boolean flipFile = (f < 4);
        if (flipRank) r = 7 - r;
        if (flipFile) f = 7 - f;
        int kidx = kt[r * 4 + (f - 4)];

        // Accumulate each piece's feature vector.
        for (int sq = 0; sq < 64; sq++) {
            int p = board.pieceAt(sq);
            if (p == Piece.EMPTY) continue;

            // Map our piece encoding to nncpu 1-indexed code:
            //   white: KING=1, QUEEN=2, ROOK=3, BISHOP=4, KNIGHT=5, PAWN=6
            //   black: KING=7, QUEEN=8, ROOK=9, BISHOP=10, KNIGHT=11, PAWN=12
            // Our type: PAWN=1..KING=6  →  nncpu white = 7 - type
            int nnPc = (7 - Piece.type(p)) + (Piece.color(p) == Piece.BLACK ? 6 : 0);

            // Apply perspective transforms (mirrors the INFLUENCE macro).
            int tsq = sq;
            int tpc = nnPc;
            if (flipRank) {
                tsq ^= 56;                                     // MIRRORR64: flip rank
                tpc = (tpc > 6) ? tpc - 6 : tpc + 6;          // INVERT: swap piece color
            }
            if (flipFile) tsq ^= 7;                            // MIRRORF64: flip file

            // Feature index: (kidx * 768 + (pc-1) * 64 + sq) * FT_WIDTH
            int base = (kidx * 768 + (tpc - 1) * 64 + tsq) * FT_WIDTH;
            for (int i = 0; i < FT_WIDTH; i++) accum[i] += iw[base + i];
        }
    }
}
