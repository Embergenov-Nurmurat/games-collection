package com.example.minigames.chess

/**
 * A chess puzzle: white to move and force mate in [mateIn] moves. [first] is the (only) first move in SAN.
 * Every entry is verified by the tests (tests_puzzles/PuzzleTest.kt): the mate length is exactly [mateIn],
 * the first move is unique, an independent search agrees.
 */
class ChessPuzzle(val fen: String, val mateIn: Int, val first: String)

object ChessPuzzles {
    val all: List<ChessPuzzle> = listOf(
        // mate in 1 (sorted by number of pieces)
        ChessPuzzle("8/8/k7/8/5p2/1R1K4/8/3Q4 w - - 0 1", 1, "Qa1#"),
        ChessPuzzle("2R5/5P2/8/8/3p2P1/8/5K2/7k w - - 0 1", 1, "Rh8#"),
        ChessPuzzle("k7/6R1/4R3/8/K7/5p2/4N3/8 w - - 0 1", 1, "Re8#"),
        ChessPuzzle("7N/8/8/7R/1R6/5n1K/k7/8 w - - 0 1", 1, "Ra5#"),
        ChessPuzzle("4N3/7k/8/8/2K3R1/3R4/8/4n3 w - - 0 1", 1, "Rh3#"),
        ChessPuzzle("1k1NN3/3p4/2Q2K2/8/8/8/8/8 w - - 0 1", 1, "Qb7#"),
        ChessPuzzle("1r1R4/k7/2K5/5R2/8/8/6P1/8 w - - 0 1", 1, "Ra5#"),
        ChessPuzzle("8/B4rR1/8/1N5k/8/8/4R3/3K4 w - - 0 1", 1, "Rh2#"),
        ChessPuzzle("1k6/3Q4/6BR/8/3P4/4K3/7p/8 w - - 0 1", 1, "Rh8#"),
        ChessPuzzle("3K4/1Q6/8/5P2/7p/3N2N1/8/k7 w - - 0 1", 1, "Qb2#"),
        ChessPuzzle("3kB3/1Q6/8/1R5N/8/8/2r5/7K w - - 0 1", 1, "Qd7#"),
        ChessPuzzle("8/8/1R6/6n1/3R1K2/8/6P1/kB6 w - - 0 1", 1, "Ra4#"),
        ChessPuzzle("3k4/R7/8/8/2PKp3/1p3N2/6Q1/8 w - - 0 1", 1, "Qg8#"),
        ChessPuzzle("4r3/8/8/1BQ2R2/5K1k/8/3r1N2/8 w - - 0 1", 1, "Rh5#"),
        ChessPuzzle("k1N2b2/P5N1/K7/8/8/7p/8/3N4 w - - 0 1", 1, "Nb6#"),
        ChessPuzzle("2R5/p7/3RK3/1kp5/8/1P6/8/2Q5 w - - 0 1", 1, "Qxc5#"),
        ChessPuzzle("4R3/K3p3/3Nr3/8/p7/7R/4Q3/2k5 w - - 0 1", 1, "Rh1#"),
        ChessPuzzle("R3r1N1/6K1/8/1Q6/b7/3b4/k7/6R1 w - - 0 1", 1, "Rxa4#"),
        ChessPuzzle("1R4b1/8/2N5/7R/8/2bK4/5Q1n/2k1b3 w - - 0 1", 1, "Qc2#"),
        ChessPuzzle("3k3K/1R6/2Q5/r7/6pN/1nr5/7R/1b6 w - - 0 1", 1, "Qd7#"),
        // mate in 2 (sorted by number of pieces)
        ChessPuzzle("5K2/8/7k/8/8/6Q1/8/r3R3 w - - 0 1", 2, "Rxa1"),
        ChessPuzzle("k3K3/8/8/1R6/8/2rQ4/8/8 w - - 0 1", 2, "Qxc3"),
        ChessPuzzle("2kN4/3p4/7Q/5K2/8/8/8/4R3 w - - 0 1", 2, "Qd6"),
        ChessPuzzle("1N6/N7/8/7n/3K4/8/4Q3/2k5 w - - 0 1", 2, "Kc3"),
        ChessPuzzle("8/8/6Q1/4P3/4N3/2p5/8/2K1k3 w - - 0 1", 2, "Qg2"),
        ChessPuzzle("8/4Q3/6B1/1N6/8/8/1p2K3/7k w - - 0 1", 2, "Kf2"),
        ChessPuzzle("6k1/7p/2K4Q/8/8/p7/R7/8 w - - 0 1", 2, "Rf2"),
        ChessPuzzle("8/8/1K6/Q7/1P5b/7p/1R6/7k w - - 0 1", 2, "Qa1+"),
        ChessPuzzle("6k1/N7/pp6/8/3R2K1/8/1Q6/8 w - - 0 1", 2, "Rd7"),
        ChessPuzzle("7K/4p3/8/8/4R1n1/5R1Q/2k5/8 w - - 0 1", 2, "Re2+"),
        ChessPuzzle("4R3/8/p4K2/3P4/8/8/7R/1B1k4 w - - 0 1", 2, "Rg8"),
        ChessPuzzle("4k3/1P6/7R/3p1B2/8/7P/8/K7 w - - 0 1", 2, "Rh7"),
        ChessPuzzle("6k1/pB6/K7/8/2R5/8/P7/7R w - - 0 1", 2, "Rc7"),
        ChessPuzzle("2Q5/1R2nn1k/8/8/8/8/5K2/6R1 w - - 0 1", 2, "Rxe7"),
        ChessPuzzle("8/8/2p3R1/p1N1B3/2K2B2/8/k7/8 w - - 0 1", 2, "Rg1"),
        ChessPuzzle("8/7R/3P4/8/1b3R1p/3K4/3B4/1k6 w - - 0 1", 2, "Rxb4+"),
        ChessPuzzle("6k1/nB2R3/8/p4R2/8/8/5Pp1/3K4 w - - 0 1", 2, "Bd5+"),
        ChessPuzzle("4k3/1K6/8/7p/1p5p/3pb1R1/6R1/1Q6 w - - 0 1", 2, "Rxe3+"),
        ChessPuzzle("2n2NB1/2pb2k1/2Q5/8/8/7p/7n/3K4 w - - 0 1", 2, "Qg6+"),
        ChessPuzzle("8/1RQ5/1b4B1/1k6/4p1n1/8/4pn2/4R1K1 w - - 0 1", 2, "Rxb6+"),
        // mate in 3 (sorted by number of pieces)
        ChessPuzzle("4k3/8/Q1K5/3R4/8/8/1N6/5r2 w - - 0 1", 3, "Qxf1"),
        ChessPuzzle("5K2/8/3N4/7k/8/1Q6/3b2N1/8 w - - 0 1", 3, "Qh3+"),
        ChessPuzzle("4k2n/8/1r4R1/2K5/8/8/2P5/7Q w - - 0 1", 3, "Qxh8+"),
        ChessPuzzle("7k/8/3N1p2/8/K7/3R4/8/R5N1 w - - 0 1", 3, "Rg3"),
        ChessPuzzle("8/4NNNK/8/Q7/2R5/6k1/2r5/8 w - - 0 1", 3, "Rxc2"),
        ChessPuzzle("8/k1P5/6K1/2p5/P5p1/1B6/5R2/8 w - - 0 1", 3, "c8=Q"),
        ChessPuzzle("2K4k/8/1rR5/2B5/8/4R3/2PB4/8 w - - 0 1", 3, "Rc7"),
        ChessPuzzle("8/8/3K1N2/1N3BQ1/8/1P2r3/k7/8 w - - 0 1", 3, "Qxe3"),
        ChessPuzzle("6k1/1R1n4/3p1P2/4B3/8/8/8/1K3Q2 w - - 0 1", 3, "Rxd7"),
        ChessPuzzle("1k1K4/5N2/1N2B3/N7/2R5/8/6b1/8 w - - 0 1", 3, "Rc7"),
        ChessPuzzle("8/B3K2p/7k/6R1/2b5/R7/1p3p2/8 w - - 0 1", 3, "Rg2"),
        ChessPuzzle("7k/4B3/4p3/1KB5/6b1/4R3/5R2/7N w - - 0 1", 3, "Rg3"),
        ChessPuzzle("6N1/kp4B1/8/1R2b3/6p1/6Q1/4R3/6K1 w - - 0 1", 3, "Qxe5"),
        ChessPuzzle("6k1/5pp1/2b3N1/6R1/8/6Q1/3Kp3/B6N w - - 0 1", 3, "Qb8+")
    )
}
