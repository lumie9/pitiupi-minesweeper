package pitiupi.minesweeper.net;

/** Como um jogador encerrou a própria rodada. */
public enum Outcome {
    /** Abriu todas as casas sem mina. */
    CLEARED,
    /** Abriu uma mina. */
    EXPLODED
}
