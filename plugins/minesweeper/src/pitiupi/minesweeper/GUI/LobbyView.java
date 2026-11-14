package pitiupi.minesweeper.GUI;

import pitiupi.application.messaging.TransportMode;
import pitiupi.minesweeper.control.GameController;
import pitiupi.minesweeper.control.RoomInfo;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/** Lista de salas, botão de criar e escolha do transporte dos anúncios. */
public final class LobbyView extends JPanel {

    private static final String CARD_TABLE = "table";
    private static final String CARD_EMPTY = "empty";
    private static final String[] COLUMNS = {"Sala", "Anfitrião", "Jogadores", "Estado"};

    private final GameController controller;
    private final DefaultTableModel model = new DefaultTableModel(COLUMNS, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(model);
    private final CardLayout cards = new CardLayout();
    private final JPanel center = new JPanel(cards);
    private final JButton createButton = new JButton("Criar sala…");
    private final JButton joinButton = new JButton("Entrar na sala");
    private final JButton refreshButton = new JButton("Atualizar");
    private final JComboBox<String> transportCombo = new JComboBox<>(new String[]{
            "UDP multicast", "UDP unicast (uma cópia por pessoa)"});
    private final JLabel statusLabel = new JLabel(" ");

    private List<RoomInfo> shown = new ArrayList<>();

    public LobbyView(GameController controller) {
        this.controller = controller;
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        JLabel title = new JLabel("Campo Minado 1v1");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        JLabel subtitle = new JLabel("<html>Escolha uma sala ou crie a sua. Com dois jogadores a contagem começa "
                + "sozinha e vence quem limpar o campo primeiro. Quem abrir uma mina perde.</html>");
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        title.setAlignmentX(LEFT_ALIGNMENT);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);
        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(subtitle);
        add(header, BorderLayout.NORTH);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(26);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(220);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    joinSelected();
                }
            }
        });
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateButtons();
            }
        });
        JLabel empty = new JLabel("<html><center>Nenhuma sala aberta por aqui.<br/>"
                + "Crie uma e chame alguém da sala para entrar.</center></html>", SwingConstants.CENTER);
        empty.setForeground(java.awt.Color.GRAY);
        center.add(new JScrollPane(table), CARD_TABLE);
        center.add(empty, CARD_EMPTY);
        center.setPreferredSize(new Dimension(520, 220));
        add(center, BorderLayout.CENTER);

        createButton.addActionListener(e -> showCreateDialog());
        joinButton.addActionListener(e -> joinSelected());
        refreshButton.addActionListener(e -> controller.refreshRooms());
        transportCombo.setToolTipText("<html>Como as salas são anunciadas na rede.<br/>"
                + "Multicast é um aviso só para todos. Se ninguém aparece na lista (Wi-Fi costuma<br/>"
                + "bloquear multicast), troque para unicast: uma cópia direta para cada pessoa.</html>");
        transportCombo.addActionListener(e -> controller.setAnnounceTransport(
                transportCombo.getSelectedIndex() == 0 ? TransportMode.MULTICAST_UDP : TransportMode.UNICAST_UDP));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.add(createButton);
        buttons.add(joinButton);
        buttons.add(refreshButton);
        buttons.add(Box.createHorizontalStrut(16));
        buttons.add(new JLabel("Anúncios:"));
        buttons.add(transportCombo);

        JPanel south = new JPanel(new BorderLayout(0, 6));
        south.add(buttons, BorderLayout.NORTH);
        south.add(statusLabel, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);

        refresh();
    }

    public void setStatus(String text) {
        statusLabel.setText(text == null || text.isEmpty() ? " " : text);
    }

    /** Relê as salas do controller e reconstrói a tabela, mantendo a seleção. */
    public void refresh() {
        String selectedId = selectedRoomId();
        shown = controller.rooms();
        model.setRowCount(0);
        int reselect = -1;
        for (int i = 0; i < shown.size(); i++) {
            RoomInfo room = shown.get(i);
            model.addRow(new Object[]{
                    room.roomName(), room.hostName(),
                    room.players() + "/2", room.phase().label()});
            if (room.roomId().equals(selectedId)) {
                reselect = i;
            }
        }
        if (reselect >= 0) {
            table.setRowSelectionInterval(reselect, reselect);
        }
        cards.show(center, shown.isEmpty() ? CARD_EMPTY : CARD_TABLE);
        if (controller.phase() == GameController.Phase.JOINING) {
            setStatus("Entrando na sala \"" + controller.roomName() + "\"…");
        }
        updateButtons();
    }

    private String selectedRoomId() {
        int row = table.getSelectedRow();
        return row >= 0 && row < shown.size() ? shown.get(row).roomId() : null;
    }

    private void updateButtons() {
        boolean lobby = controller.phase() == GameController.Phase.LOBBY;
        int row = table.getSelectedRow();
        boolean canJoin = lobby && row >= 0 && row < shown.size() && shown.get(row).isJoinable();
        createButton.setEnabled(lobby);
        joinButton.setEnabled(canJoin);
        refreshButton.setEnabled(lobby);
    }

    private void joinSelected() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= shown.size()) {
            return;
        }
        RoomInfo room = shown.get(row);
        if (!room.isJoinable()) {
            setStatus(room.players() >= 2 ? "Essa sala já está cheia." : "Essa sala já começou.");
            return;
        }
        setStatus(" ");
        controller.joinRoom(room.roomId());
    }

    private void showCreateDialog() {
        JTextField name = new JTextField("Sala de " + controller.myName(), 22);
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(new JLabel("Nome da sala:"));
        form.add(name);
        int choice = JOptionPane.showConfirmDialog(
                this, form, "Criar sala", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice == JOptionPane.OK_OPTION) {
            setStatus(" ");
            controller.createRoom(name.getText());
        }
    }
}
