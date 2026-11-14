package pitiupi.minesweeper.GUI;

import pitiupi.minesweeper.control.GameController;
import pitiupi.minesweeper.game.Board;
import pitiupi.minesweeper.game.GameRules;
import pitiupi.minesweeper.net.ResultReason;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.Component;

/** Tela da sala: campo, contagem, progresso dos dois jogadores e resultado. */
public final class GameView extends JPanel {

    private final GameController controller;
    private final BoardView boardView = new BoardView();

    private final JLabel titleLabel = new JLabel(" ");
    private final JLabel statusLabel = new JLabel(" ");
    private final JLabel bannerLabel = new JLabel(" ");
    private final JLabel timerLabel = new JLabel("0:00.0");
    private final JLabel minesLabel = new JLabel(" ");
    private final JLabel myNameLabel = new JLabel(" ");
    private final JLabel oppNameLabel = new JLabel(" ");
    private final JProgressBar myProgress = new JProgressBar(0, 100);
    private final JProgressBar oppProgress = new JProgressBar(0, 100);
    private final JButton rematchButton = new JButton("Jogar de novo");
    private final JButton leaveButton = new JButton("Sair da sala");
    private final Timer clock = new Timer(100, e -> updateClock());

    public GameView(GameController controller) {
        this.controller = controller;
        setLayout(new BorderLayout(12, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 22f));
        bannerLabel.setForeground(new Color(0x546E7A));
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        for (JLabel label : new JLabel[]{titleLabel, statusLabel, bannerLabel}) {
            label.setAlignmentX(LEFT_ALIGNMENT);
            header.add(label);
        }
        add(header, BorderLayout.NORTH);

        boardView.setActions(new BoardView.Actions() {
            @Override
            public void reveal(int row, int col) {
                controller.reveal(row, col);
            }

            @Override
            public void flag(int row, int col) {
                controller.toggleFlag(row, col);
            }
        });
        JPanel boardHolder = new JPanel(new GridBagLayout());
        boardHolder.add(boardView);
        JScrollPane scroll = new JScrollPane(boardHolder);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setOpaque(false);
        add(scroll, BorderLayout.CENTER);

        add(buildSidebar(), BorderLayout.EAST);

        leaveButton.addActionListener(e -> onLeave());
        rematchButton.addActionListener(e -> controller.requestRematch());
        clock.start();
    }

    private JPanel buildSidebar() {
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setPreferredSize(new Dimension(230, 10));

        timerLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 30));
        myNameLabel.setFont(myNameLabel.getFont().deriveFont(Font.BOLD, 14f));
        oppNameLabel.setFont(oppNameLabel.getFont().deriveFont(Font.BOLD, 14f));
        myProgress.setStringPainted(true);
        oppProgress.setStringPainted(true);

        addLeft(side, sectionLabel("VOCÊ"));
        addLeft(side, myNameLabel);
        addLeft(side, Box.createVerticalStrut(4));
        addLeft(side, timerLabel);
        addLeft(side, minesLabel);
        addLeft(side, Box.createVerticalStrut(6));
        addLeft(side, myProgress);
        addLeft(side, Box.createVerticalStrut(18));
        addLeft(side, sectionLabel("OPONENTE"));
        addLeft(side, oppNameLabel);
        addLeft(side, Box.createVerticalStrut(6));
        addLeft(side, oppProgress);
        addLeft(side, Box.createVerticalStrut(18));
        JLabel help = new JLabel("<html><small>Botão esquerdo abre.<br/>Botão direito põe bandeira.<br/>"
                + "Clique num número com as bandeiras certas ao redor abre as vizinhas.</small></html>");
        help.setForeground(new Color(0x78909C));
        addLeft(side, help);
        side.add(Box.createVerticalGlue());
        addLeft(side, rematchButton);
        addLeft(side, Box.createVerticalStrut(6));
        addLeft(side, leaveButton);
        for (Component c : side.getComponents()) {
            if (c instanceof JProgressBar || c instanceof JButton) {
                ((javax.swing.JComponent) c).setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
            }
        }
        return side;
    }

    private static JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        label.setForeground(new Color(0x78909C));
        return label;
    }

    private static void addLeft(JPanel panel, Component component) {
        ((javax.swing.JComponent) component).setAlignmentX(LEFT_ALIGNMENT);
        panel.add(component);
    }

    private void onLeave() {
        GameController.Phase phase = controller.phase();
        if (phase == GameController.Phase.PLAYING && !controller.isLocalDone()) {
            int answer = JOptionPane.showConfirmDialog(this,
                    "Sair no meio da partida dá a vitória ao oponente. Sair mesmo assim?",
                    "Sair da sala", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }
        }
        controller.leaveRoom();
    }

    /** Relê o controller e atualiza todos os componentes. */
    public void refresh() {
        GameController.Phase phase = controller.phase();
        Board board = controller.board();

        titleLabel.setText(controller.roomName() == null ? " "
                : controller.roomName() + "  ·  " + GameRules.describe());
        statusLabel.setText(statusText(phase, board));
        statusLabel.setForeground(statusColor(phase));
        String banner = controller.banner();
        if (banner.isEmpty() && phase == GameController.Phase.FINISHED) {
            banner = resultDetail();
        }
        bannerLabel.setText(banner.isEmpty() ? " " : banner);

        myNameLabel.setText(controller.myName());
        oppNameLabel.setText(controller.opponentName() == null ? "—" : controller.opponentName());
        myProgress.setValue(board == null ? 0 : board.progressPercent());
        oppProgress.setValue(controller.opponentProgressPercent());
        minesLabel.setText(board == null ? " " : "Minas restantes: " + board.minesLeft());

        boardView.setBoard(board, GameRules.ROWS, GameRules.COLS);
        boardView.setInteractive(phase == GameController.Phase.PLAYING && !controller.isLocalDone());
        boardView.setShowMines(board != null && (phase == GameController.Phase.FINISHED || board.isExploded()));
        if (phase == GameController.Phase.COUNTDOWN) {
            boardView.setOverlay(Integer.toString(controller.countdownLeft()), "Prepare-se!");
        } else if (phase == GameController.Phase.WAITING) {
            boardView.setOverlay("Aguardando oponente…", "Quem entrar na sala começa a partida.");
        } else if (phase == GameController.Phase.CLOSED && board == null) {
            boardView.setOverlay("Sala encerrada", null);
        } else {
            boardView.setOverlay(null, null);
        }

        boolean finished = phase == GameController.Phase.FINISHED && controller.hasOpponent();
        rematchButton.setVisible(finished);
        if (controller.role() == GameController.Role.HOST) {
            rematchButton.setText("Jogar de novo");
            rematchButton.setEnabled(true);
        } else {
            rematchButton.setText(controller.isRematchRequested() ? "Revanche pedida…" : "Pedir revanche");
            rematchButton.setEnabled(!controller.isRematchRequested());
        }
        leaveButton.setText(phase == GameController.Phase.CLOSED ? "Voltar ao lobby" : "Sair da sala");
        updateClock();
        revalidate();
        repaint();
    }

    private String statusText(GameController.Phase phase, Board board) {
        String opp = controller.opponentName() == null ? "o oponente" : controller.opponentName();
        switch (phase) {
            case WAITING:
                return controller.hasOpponent() ? "Conectando com " + opp + "…" : "Aguardando outro jogador…";
            case COUNTDOWN:
                return "Começa em " + controller.countdownLeft() + "…";
            case PLAYING:
                if (controller.isLocalDone()) {
                    return board != null && board.isExploded()
                            ? "Você abriu uma mina! Aguardando o resultado…"
                            : "Campo limpo! Aguardando confirmação…";
                }
                return "Valendo! Limpe o campo antes de " + opp + ".";
            case FINISHED:
                return Boolean.TRUE.equals(controller.won()) ? "Você venceu!" : "Você perdeu.";
            case CLOSED:
                return "Sala encerrada";
            default:
                return " ";
        }
    }

    private Color statusColor(GameController.Phase phase) {
        if (phase == GameController.Phase.FINISHED) {
            return Boolean.TRUE.equals(controller.won()) ? new Color(0x2E7D32) : new Color(0xC62828);
        }
        return UIColors.text();
    }

    private String resultDetail() {
        boolean won = Boolean.TRUE.equals(controller.won());
        String opp = controller.opponentName() == null ? "O oponente" : controller.opponentName();
        ResultReason reason = controller.resultReason();
        if (reason == null) {
            return "";
        }
        long ms = controller.winnerElapsedMs();
        String time = ms >= 0 ? " em " + formatTime(ms) : "";
        switch (reason) {
            case CLEARED:
                return won ? "Você limpou o campo" + time + "." : opp + " limpou o campo primeiro" + time + ".";
            case OPPONENT_EXPLODED:
                return won ? opp + " abriu uma mina." : "Você abriu uma mina.";
            default:
                return won ? opp + " saiu da partida." : "Você saiu da partida.";
        }
    }

    private void updateClock() {
        timerLabel.setText(formatTime(controller.elapsedMillis()));
    }

    static String formatTime(long ms) {
        long tenths = ms / 100;
        long minutes = tenths / 600;
        long seconds = (tenths / 10) % 60;
        return minutes + ":" + (seconds < 10 ? "0" : "") + seconds + "." + (tenths % 10);
    }
}
