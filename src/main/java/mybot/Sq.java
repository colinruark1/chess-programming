package mybot;

public final class Sq {
    public static final int A1=0,  B1=1,  C1=2,  D1=3,  E1=4,  F1=5,  G1=6,  H1=7;
    public static final int A2=8,  B2=9,  C2=10, D2=11, E2=12, F2=13, G2=14, H2=15;
    public static final int A3=16, B3=17, C3=18, D3=19, E3=20, F3=21, G3=22, H3=23;
    public static final int A4=24, B4=25, C4=26, D4=27, E4=28, F4=29, G4=30, H4=31;
    public static final int A5=32, B5=33, C5=34, D5=35, E5=36, F5=37, G5=38, H5=39;
    public static final int A6=40, B6=41, C6=42, D6=43, E6=44, F6=45, G6=46, H6=47;
    public static final int A7=48, B7=49, C7=50, D7=51, E7=52, F7=53, G7=54, H7=55;
    public static final int A8=56, B8=57, C8=58, D8=59, E8=60, F8=61, G8=62, H8=63;

    public static final int NONE = -1;

    private static final String[] NAMES = {
        "a1","b1","c1","d1","e1","f1","g1","h1",
        "a2","b2","c2","d2","e2","f2","g2","h2",
        "a3","b3","c3","d3","e3","f3","g3","h3",
        "a4","b4","c4","d4","e4","f4","g4","h4",
        "a5","b5","c5","d5","e5","f5","g5","h5",
        "a6","b6","c6","d6","e6","f6","g6","h6",
        "a7","b7","c7","d7","e7","f7","g7","h7",
        "a8","b8","c8","d8","e8","f8","g8","h8"
    };

    private Sq() {}

    public static int of(int file, int rank) { return rank * 8 + file; }
    public static int file(int sq) { return sq & 7; }
    public static int rank(int sq) { return sq >> 3; }

    public static String name(int sq) {
        if (sq < 0 || sq > 63) return "-";
        return NAMES[sq];
    }

    public static int parse(String s) {
        if (s == null || s.length() < 2) return NONE;
        int file = s.charAt(0) - 'a';
        int rank = s.charAt(1) - '1';
        if (file < 0 || file > 7 || rank < 0 || rank > 7) return NONE;
        return of(file, rank);
    }

    public static int mirrorRank(int sq) { return sq ^ 56; }
}
