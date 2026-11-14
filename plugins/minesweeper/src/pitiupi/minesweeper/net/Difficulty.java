package pitiupi.minesweeper.net;

/**
 * Tamanhos de campo. O anfitrião escolhe ao criar a sala e a escolha viaja no anúncio.
 */
public enum Difficulty {
    EASY("Fácil", 9, 9, 10),
    MEDIUM("Médio", 16, 16, 40),
    HARD("Difícil", 16, 30, 99);

    private final String label;
    private final int rows;
    private final int cols;
    private final int mines;

    Difficulty(String label, int rows, int cols, int mines) {
        this.label = label;
        this.rows = rows;
        this.cols = cols;
        this.mines = mines;
    }

    public String label() { return label; }
    public int rows() { return rows; }
    public int cols() { return cols; }
    public int mines() { return mines; }

    /** Texto para tabela e combo, por exemplo {@code Fácil (9×9, 10 minas)}. */
    public String describe() {
        return label + " (" + rows + "×" + cols + ", " + mines + " minas)";
    }

    @Override
    public String toString() {
        return describe();
    }
}
