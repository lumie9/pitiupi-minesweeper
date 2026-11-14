package pitiupi.minesweeper.game;

/** Tamanho fixo do campo de todas as partidas: 16×16 com 40 minas. */
public final class GameRules {

    public static final int ROWS = 16;
    public static final int COLS = 16;
    public static final int MINES = 40;

    private GameRules() {
    }

    /** Texto curto para a tela, por exemplo {@code 16×16, 40 minas}. */
    public static String describe() {
        return ROWS + "×" + COLS + ", " + MINES + " minas";
    }
}
