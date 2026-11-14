package pitiupi.minesweeper.GUI;

import pitiupi.minesweeper.game.Board;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Desenha o campo e traduz cliques em jogadas. Não conhece rede nem regras: lê o {@link Board}
 * e chama {@link Actions}.
 */
public final class BoardView extends JComponent {

    public interface Actions {
        void reveal(int row, int col);
        void flag(int row, int col);
    }

    private static final int PAD = 6;

    private static final Color BG = new Color(0x37474F);
    private static final Color HIDDEN = new Color(0xB0BEC5);
    private static final Color HIDDEN_HOVER = new Color(0xC5D0D6);
    private static final Color HIDDEN_LIGHT = new Color(0xECEFF1);
    private static final Color HIDDEN_DARK = new Color(0x78909C);
    private static final Color OPEN = new Color(0xECEFF1);
    private static final Color OPEN_LINE = new Color(0xCFD8DC);
    private static final Color BOOM = new Color(0xE53935);
    private static final Color[] NUMBER = {
            Color.BLACK,
            new Color(0x1565C0), new Color(0x2E7D32), new Color(0xC62828), new Color(0x283593),
            new Color(0x6D4C41), new Color(0x00838F), new Color(0x212121), new Color(0x757575)
    };

    private Board board;
    private int rows = 9;
    private int cols = 9;
    private int cell = 34;
    private boolean interactive;
    private boolean showMines;
    private String overlayBig;
    private String overlaySmall;
    private Actions actions;
    private int hoverRow = -1;
    private int hoverCol = -1;

    public BoardView() {
        setOpaque(false);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!interactive || actions == null) {
                    return;
                }
                int c = (e.getX() - PAD) / cell;
                int r = (e.getY() - PAD) / cell;
                if (e.getX() < PAD || e.getY() < PAD || r < 0 || r >= rows || c < 0 || c >= cols) {
                    return;
                }
                if (SwingUtilities.isRightMouseButton(e)) {
                    actions.flag(r, c);
                } else {
                    actions.reveal(r, c);
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                int c = (e.getX() - PAD) / cell;
                int r = (e.getY() - PAD) / cell;
                if (e.getX() < PAD || e.getY() < PAD || r >= rows || c >= cols) {
                    r = -1;
                    c = -1;
                }
                if (r != hoverRow || c != hoverCol) {
                    hoverRow = r;
                    hoverCol = c;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverRow = -1;
                hoverCol = -1;
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    public void setActions(Actions actions) {
        this.actions = actions;
    }

    /** {@code board} pode ser nulo: nesse caso desenha um campo vazio com as dimensões dadas. */
    public void setBoard(Board board, int fallbackRows, int fallbackCols) {
        this.board = board;
        int newRows = board != null ? board.rows() : fallbackRows;
        int newCols = board != null ? board.cols() : fallbackCols;
        if (newRows != rows || newCols != cols) {
            rows = newRows;
            cols = newCols;
            cell = cols >= 28 ? 26 : cols >= 16 ? 30 : 36;
            revalidate();
        }
        repaint();
    }

    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
        setCursor(interactive ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
    }

    public void setShowMines(boolean showMines) {
        this.showMines = showMines;
    }

    public void setOverlay(String big, String small) {
        this.overlayBig = big;
        this.overlaySmall = small;
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(cols * cell + 2 * PAD, rows * cell + 2 * PAD);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(BG);
        g.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);

        Font numberFont = new Font(Font.SANS_SERIF, Font.BOLD, Math.round(cell * 0.56f));
        g.setFont(numberFont);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                paintCell(g, r, c, PAD + c * cell, PAD + r * cell);
            }
        }
        if (overlayBig != null) {
            paintOverlay(g);
        }
        g.dispose();
    }

    private void paintCell(Graphics2D g, int r, int c, int x, int y) {
        byte state = board == null ? Board.HIDDEN : board.state(r, c);
        boolean exploded = board != null && board.isExploded();
        boolean boomCell = exploded && r == board.explodedRow() && c == board.explodedCol();
        boolean revealMine = board != null && board.isMine(r, c) && (showMines || exploded);

        if (state == Board.REVEALED || (revealMine && state != Board.FLAGGED)) {
            g.setColor(boomCell ? BOOM : OPEN);
            g.fillRect(x, y, cell - 1, cell - 1);
            g.setColor(OPEN_LINE);
            g.drawRect(x, y, cell - 2, cell - 2);
            if (board.isMine(r, c)) {
                paintMine(g, x, y);
            } else if (board.adjacent(r, c) > 0) {
                paintNumber(g, board.adjacent(r, c), x, y);
            }
            return;
        }

        boolean hover = interactive && r == hoverRow && c == hoverCol;
        g.setColor(hover ? HIDDEN_HOVER : HIDDEN);
        g.fillRect(x, y, cell - 1, cell - 1);
        g.setColor(HIDDEN_LIGHT);
        g.drawLine(x, y, x + cell - 2, y);
        g.drawLine(x, y, x, y + cell - 2);
        g.setColor(HIDDEN_DARK);
        g.drawLine(x + cell - 2, y, x + cell - 2, y + cell - 2);
        g.drawLine(x, y + cell - 2, x + cell - 2, y + cell - 2);
        if (state == Board.FLAGGED) {
            paintFlag(g, x, y);
            if (exploded && !board.isMine(r, c)) {
                g.setColor(BOOM);
                g.setStroke(new BasicStroke(2f));
                g.drawLine(x + 5, y + 5, x + cell - 7, y + cell - 7);
                g.drawLine(x + cell - 7, y + 5, x + 5, y + cell - 7);
            }
        }
    }

    private void paintNumber(Graphics2D g, int n, int x, int y) {
        String text = Integer.toString(n);
        FontMetrics fm = g.getFontMetrics();
        g.setColor(NUMBER[Math.min(n, 8)]);
        g.drawString(text, x + (cell - 1 - fm.stringWidth(text)) / 2,
                y + (cell - 1 - fm.getHeight()) / 2 + fm.getAscent());
    }

    private void paintMine(Graphics2D g, int x, int y) {
        int cx = x + cell / 2;
        int cy = y + cell / 2;
        int radius = Math.round(cell * 0.22f);
        g.setColor(new Color(0x212121));
        g.setStroke(new BasicStroke(2f));
        g.drawLine(cx - radius - 4, cy, cx + radius + 4, cy);
        g.drawLine(cx, cy - radius - 4, cx, cy + radius + 4);
        g.drawLine(cx - radius - 2, cy - radius - 2, cx + radius + 2, cy + radius + 2);
        g.drawLine(cx - radius - 2, cy + radius + 2, cx + radius + 2, cy - radius - 2);
        g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
        g.setColor(Color.WHITE);
        g.fillOval(cx - radius / 2, cy - radius / 2, Math.max(2, radius / 2), Math.max(2, radius / 2));
    }

    private void paintFlag(Graphics2D g, int x, int y) {
        int cx = x + cell / 2;
        int top = y + Math.round(cell * 0.2f);
        int mid = y + Math.round(cell * 0.46f);
        int bottom = y + Math.round(cell * 0.74f);
        g.setColor(new Color(0x263238));
        g.fillRect(cx, top, 2, bottom - top);
        g.fillRect(cx - Math.round(cell * 0.16f), bottom, Math.round(cell * 0.34f), 3);
        Polygon flag = new Polygon();
        flag.addPoint(cx, top);
        flag.addPoint(cx, mid);
        flag.addPoint(cx - Math.round(cell * 0.3f), (top + mid) / 2);
        g.setColor(new Color(0xE53935));
        g.fillPolygon(flag);
    }

    private void paintOverlay(Graphics2D g) {
        g.setColor(new Color(20, 28, 33, 190));
        g.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
        g.setColor(Color.WHITE);
        int bigSize = overlayBig.length() <= 2 ? Math.min(getHeight() / 2, 140) : 28;
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, bigSize));
        FontMetrics fm = g.getFontMetrics();
        int y = getHeight() / 2 + fm.getAscent() / 3;
        if (overlaySmall != null) {
            y -= 14;
        }
        g.drawString(overlayBig, (getWidth() - fm.stringWidth(overlayBig)) / 2, y);
        if (overlaySmall != null) {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
            FontMetrics small = g.getFontMetrics();
            g.setColor(new Color(0xCFD8DC));
            g.drawString(overlaySmall, (getWidth() - small.stringWidth(overlaySmall)) / 2, y + 34);
        }
    }
}
