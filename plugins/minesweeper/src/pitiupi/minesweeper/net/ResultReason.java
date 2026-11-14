package pitiupi.minesweeper.net;

/** Motivo pelo qual o anfitrião declarou o vencedor. */
public enum ResultReason {
    /** O vencedor limpou o campo primeiro. */
    CLEARED,
    /** O perdedor abriu uma mina. */
    OPPONENT_EXPLODED,
    /** O perdedor saiu ou parou de responder. */
    OPPONENT_LEFT
}
