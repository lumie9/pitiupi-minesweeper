package pitiupi.minesweeper.game;

import java.util.ArrayDeque;
import java.util.Random;

/**
 * Campo minado sem Swing e sem rede: só as regras.
 *
 * <p>O campo sai inteiro da {@code seed}. Os dois jogadores constroem {@code new Board(...)} com a
 * mesma semente e ficam com o mesmo campo, sem que ele trafegue pela rede. Por isso o sorteio usa
 * só {@link Random#nextInt(int)}, cujo algoritmo é fixado na documentação do Java.</p>
 *
 * <p>Não há "primeiro clique seguro" porque a rodada começa para os dois ao mesmo tempo. No lugar
 * disso, a semente também sorteia uma casa inicial: ela e as oito vizinhas nunca têm mina, e
 * {@link #openStart()} abre essa região para os dois jogadores no início.</p>
 *
 * <p>Não é thread-safe: o controller só mexe nele na thread do Swing.</p>
 */
public final class Board {

    public static final byte HIDDEN = 0;
    public static final byte REVEALED = 1;
    public static final byte FLAGGED = 2;

    private final int rows;
    private final int cols;
    private final int mines;
    private final long seed;
    private final boolean[][] mine;
    private final int[][] adjacent;
    private final byte[][] state;
    private final int startRow;
    private final int startCol;

    private int revealedSafe;
    private int flags;
    private boolean exploded;
    private int explodedRow = -1;
    private int explodedCol = -1;

    public Board(int rows, int cols, int mines, long seed) {
        if (rows < 3 || cols < 3) {
            throw new IllegalArgumentException("O campo precisa ter pelo menos 3x3.");
        }
        if (mines < 1 || mines > rows * cols - 9) {
            throw new IllegalArgumentException("Minas demais para este campo.");
        }
        this.rows = rows;
        this.cols = cols;
        this.mines = mines;
        this.seed = seed;
        this.mine = new boolean[rows][cols];
        this.adjacent = new int[rows][cols];
        this.state = new byte[rows][cols];

        Random random = new Random(seed);
        this.startRow = 1 + random.nextInt(rows - 2);
        this.startCol = 1 + random.nextInt(cols - 2);

        int[] candidates = new int[rows * cols];
        int count = 0;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (Math.abs(r - startRow) <= 1 && Math.abs(c - startCol) <= 1) {
                    continue;
                }
                candidates[count++] = r * cols + c;
            }
        }
        // Fisher-Yates parcial: as primeiras "mines" posições do vetor viram as minas.
        for (int i = 0; i < mines; i++) {
            int j = i + random.nextInt(count - i);
            int tmp = candidates[i];
            candidates[i] = candidates[j];
            candidates[j] = tmp;
            mine[candidates[i] / cols][candidates[i] % cols] = true;
        }

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int n = 0;
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        if ((dr != 0 || dc != 0) && inside(r + dr, c + dc) && mine[r + dr][c + dc]) {
                            n++;
                        }
                    }
                }
                adjacent[r][c] = n;
            }
        }
    }

    // ---- jogadas ----

    /** Abre a região inicial (a mesma para os dois jogadores). Devolve quantas casas abriu. */
    public int openStart() {
        return flood(startRow, startCol);
    }

    /**
     * Abre uma casa. Casa já aberta ou com bandeira não muda nada. Mina marca o campo como
     * explodido. Devolve quantas casas seguras foram abertas agora.
     */
    public int reveal(int r, int c) {
        if (exploded || !inside(r, c) || state[r][c] != HIDDEN) {
            return 0;
        }
        if (mine[r][c]) {
            state[r][c] = REVEALED;
            exploded = true;
            explodedRow = r;
            explodedCol = c;
            return 0;
        }
        return flood(r, c);
    }

    /**
     * Clique em número já aberto: se o número de bandeiras ao redor for igual ao número da casa,
     * abre as vizinhas sem bandeira. Bandeira errada faz explodir, como no jogo clássico.
     */
    public int chord(int r, int c) {
        if (exploded || !inside(r, c) || state[r][c] != REVEALED || adjacent[r][c] == 0) {
            return 0;
        }
        int flagged = 0;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int nr = r + dr;
                int nc = c + dc;
                if ((dr != 0 || dc != 0) && inside(nr, nc) && state[nr][nc] == FLAGGED) {
                    flagged++;
                }
            }
        }
        if (flagged != adjacent[r][c]) {
            return 0;
        }
        int opened = 0;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int nr = r + dr;
                int nc = c + dc;
                if ((dr != 0 || dc != 0) && inside(nr, nc)) {
                    opened += reveal(nr, nc);
                }
            }
        }
        return opened;
    }

    /** Liga ou desliga a bandeira de uma casa fechada. */
    public void toggleFlag(int r, int c) {
        if (exploded || !inside(r, c)) {
            return;
        }
        if (state[r][c] == HIDDEN) {
            state[r][c] = FLAGGED;
            flags++;
        } else if (state[r][c] == FLAGGED) {
            state[r][c] = HIDDEN;
            flags--;
        }
    }

    private int flood(int startR, int startC) {
        if (state[startR][startC] != HIDDEN || mine[startR][startC]) {
            return 0;
        }
        int opened = 0;
        ArrayDeque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{startR, startC});
        state[startR][startC] = REVEALED;
        opened++;
        while (!stack.isEmpty()) {
            int[] cell = stack.pop();
            if (adjacent[cell[0]][cell[1]] != 0) {
                continue;
            }
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    int nr = cell[0] + dr;
                    int nc = cell[1] + dc;
                    if ((dr != 0 || dc != 0) && inside(nr, nc) && state[nr][nc] == HIDDEN && !mine[nr][nc]) {
                        state[nr][nc] = REVEALED;
                        opened++;
                        stack.push(new int[]{nr, nc});
                    }
                }
            }
        }
        revealedSafe += opened;
        return opened;
    }

    // ---- consulta ----

    private boolean inside(int r, int c) {
        return r >= 0 && r < rows && c >= 0 && c < cols;
    }

    public int rows() { return rows; }
    public int cols() { return cols; }
    public int mines() { return mines; }
    public long seed() { return seed; }
    public byte state(int r, int c) { return state[r][c]; }
    public int adjacent(int r, int c) { return adjacent[r][c]; }
    public boolean isMine(int r, int c) { return mine[r][c]; }
    public int startRow() { return startRow; }
    public int startCol() { return startCol; }
    public int explodedRow() { return explodedRow; }
    public int explodedCol() { return explodedCol; }
    public boolean isExploded() { return exploded; }
    public int flags() { return flags; }
    public int minesLeft() { return mines - flags; }
    public int revealedSafe() { return revealedSafe; }
    public int safeTotal() { return rows * cols - mines; }
    public boolean isCleared() { return !exploded && revealedSafe == safeTotal(); }

    /** 0 a 100. */
    public int progressPercent() {
        return (int) Math.round(100.0 * revealedSafe / safeTotal());
    }
}
