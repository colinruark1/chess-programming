package mybot;

import java.io.*;
import java.nio.*;

/**
 * Small feedforward network trained by chess_trainer.py.
 *
 * Architecture:  768 → 64 (ReLU) → 32 (ReLU) → 1  (centipawns, White's perspective)
 *
 * Input features (768 total, little-endian float32):
 *   [  0..383] White piece-squares: (base_value + PST_bonus) / 1000  for each type×sq
 *   [384..767] Black piece-squares: same layout, independent channel
 *   Type order: PAWN=0, KNIGHT=1, BISHOP=2, ROOK=3, QUEEN=4, KING=5
 *
 * Binary file layout (hce_network.bin, little-endian):
 *   4 bytes   magic = 0x48434501
 *   768*64    float32  W1 (row-major: W1[i*64 + j])
 *   64        float32  b1
 *   64*32     float32  W2 (row-major: W2[i*32 + j])
 *   32        float32  b2
 *   32        float32  W3
 *   1         float32  b3
 */
public class HCENetwork {

    private static final int IN  = 768;
    private static final int H1  = 64;
    private static final int H2  = 32;
    private static final int MAGIC = 0x48434501;

    private float[] w1, b1, w2, b2, w3;
    private float   b3;
    private boolean ready = false;

    // Reusable buffers — no allocation per evaluation
    private final float[] feats = new float[IN];
    private final float[] h1out = new float[H1];
    private final float[] h2out = new float[H2];

    // ── Loading ───────────────────────────────────────────────────────────────

    public void load(String path) throws IOException {
        File file = new File(path);
        byte[] raw = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file)) {
            int off = 0;
            while (off < raw.length) off += fis.read(raw, off, raw.length - off);
        }
        ByteBuffer bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);

        int magic = bb.getInt();
        if (magic != MAGIC)
            throw new IOException(String.format(
                "HCENetwork: bad magic 0x%08X (expected 0x%08X)", magic, MAGIC));

        w1 = readFloats(bb, IN * H1);
        b1 = readFloats(bb, H1);
        w2 = readFloats(bb, H1 * H2);
        b2 = readFloats(bb, H2);
        w3 = readFloats(bb, H2);
        b3 = bb.getFloat();

        ready = true;
        System.out.printf("info string HCENetwork loaded: %s%n", path);
    }

    private static float[] readFloats(ByteBuffer bb, int n) {
        float[] a = new float[n];
        for (int i = 0; i < n; i++) a[i] = bb.getFloat();
        return a;
    }

    public boolean isReady() { return ready; }

    // ── Evaluation ────────────────────────────────────────────────────────────

    /**
     * Returns the evaluation in centipawns from White's perspective.
     * The caller should negate if needed for side-to-move convention.
     */
    public int evaluate(Board board) {
        buildFeatures(board);

        // H1: 768 → 64 with ReLU
        for (int j = 0; j < H1; j++) {
            float s = b1[j];
            for (int i = 0; i < IN; i++) s += feats[i] * w1[i * H1 + j];
            h1out[j] = s > 0f ? s : 0f;
        }

        // H2: 64 → 32 with ReLU
        for (int j = 0; j < H2; j++) {
            float s = b2[j];
            for (int i = 0; i < H1; i++) s += h1out[i] * w2[i * H2 + j];
            h2out[j] = s > 0f ? s : 0f;
        }

        // Output: 32 → 1 linear
        float score = b3;
        for (int i = 0; i < H2; i++) score += h2out[i] * w3[i];
        return Math.round(score);
    }

    // ── Feature extraction ────────────────────────────────────────────────────

    private void buildFeatures(Board board) {
        for (int i = 0; i < IN; i++) feats[i] = 0f;

        // Determine game phase (mirrors GamePhase.detectPhase)
        int mat = 0;
        for (int sq = 0; sq < 64; sq++) {
            int p = board.pieceAt(sq);
            if (p != Piece.EMPTY) mat += TrackedPiece.getBaseValue(Piece.type(p));
        }
        GamePhase.Phase phase = GamePhase.detectPhase(mat);

        for (int sq = 0; sq < 64; sq++) {
            int p = board.pieceAt(sq);
            if (p == Piece.EMPTY) continue;

            int type  = Piece.type(p);
            int color = Piece.color(p);
            int typeIdx = typeIndex(type);
            if (typeIdx < 0) continue;

            int base = TrackedPiece.getBaseValue(type);
            int pst  = PieceSquareTables.getBonus(type, color, sq, phase);
            float val = (base + pst) / 1000f;

            int slot = typeIdx * 64 + sq;
            if (color == Piece.WHITE) {
                feats[slot] = val;
            } else {
                feats[384 + slot] = val;
            }
        }
    }

    private static int typeIndex(int type) {
        return switch (type) {
            case Piece.PAWN   -> 0;
            case Piece.KNIGHT -> 1;
            case Piece.BISHOP -> 2;
            case Piece.ROOK   -> 3;
            case Piece.QUEEN  -> 4;
            case Piece.KING   -> 5;
            default           -> -1;
        };
    }
}
