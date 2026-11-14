package pitiupi.minesweeper.GUI;

import pitiupi.minesweeper.control.GameController;

import javax.swing.JPanel;
import java.awt.CardLayout;
import java.awt.event.HierarchyEvent;

/**
 * Painel que a shell mostra na aba do plugin: o lobby, ou a sala quando a pessoa está numa.
 * Implementa {@link GameController.Listener}, então só reage ao que o controller avisa.
 */
public final class MinesweeperPanel extends JPanel implements GameController.Listener {

    private static final String LOBBY = "lobby";
    private static final String GAME = "game";

    private final GameController controller;
    private final CardLayout cards = new CardLayout();
    private final LobbyView lobby;
    private final GameView game;

    public MinesweeperPanel(GameController controller) {
        super();
        this.controller = controller;
        setLayout(cards);
        this.lobby = new LobbyView(controller);
        this.game = new GameView(controller);
        add(lobby, LOBBY);
        add(game, GAME);
        controller.setListener(this);

        // Ao abrir a aba, pede às salas que anunciem na hora.
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0
                    && isShowing() && controller.phase() == GameController.Phase.LOBBY) {
                controller.refreshRooms();
            }
        });
    }

    @Override
    public void lobbyChanged() {
        lobby.refresh();
    }

    @Override
    public void sessionChanged() {
        boolean inLobby = controller.phase() == GameController.Phase.LOBBY
                || controller.phase() == GameController.Phase.JOINING;
        cards.show(this, inLobby ? LOBBY : GAME);
        lobby.refresh();
        game.refresh();
    }

    @Override
    public void boardChanged() {
        game.refresh();
    }

    @Override
    public void notice(String text) {
        lobby.setStatus(text);
    }
}
