package pitiupi.minesweeper.plugin;

import pitiupi.api.plugin.PeerActionsRegistrar;
import pitiupi.api.plugin.PluginWorkspace;
import pitiupi.application.peeractions.PeerAction;
import pitiupi.application.peeractions.PeerActionTarget;
import pitiupi.control.ConnectionManager;
import pitiupi.minesweeper.GUI.MinesweeperPanel;
import pitiupi.minesweeper.control.GameController;
import pitiupi.minesweeper.control.PitiupiNet;
import pitiupi.minesweeper.net.FinishMessage;
import pitiupi.minesweeper.net.JoinRequestMessage;
import pitiupi.minesweeper.net.JoinResponseMessage;
import pitiupi.minesweeper.net.LeaveMessage;
import pitiupi.minesweeper.net.MineMessage;
import pitiupi.minesweeper.net.RematchMessage;
import pitiupi.minesweeper.net.ResultMessage;
import pitiupi.minesweeper.net.RoomAnnounceMessage;
import pitiupi.minesweeper.net.RoomClosedMessage;
import pitiupi.minesweeper.net.RoomQueryMessage;
import pitiupi.minesweeper.net.StartMessage;
import pitiupi.minesweeper.net.StatusMessage;
import pitiupi.models.Messages.Message;
import pitiupi.models.Plugin;

import javax.swing.SwingUtilities;
import java.util.List;

/**
 * Campo Minado 1v1. Liga o controller e a tela ao núcleo; o jogo em si está em
 * {@link GameController}. O protocolo está descrito em {@code plugins/minesweeper/README.md}.
 */
public class MinesweeperPlugin extends Plugin {

    private PluginWorkspace workspace;
    private GameController controller;
    private MinesweeperPanel panel;

    @Override
    public String getName() {
        return "Campo Minado";
    }

    @Override
    public String getDisplayName() {
        return "Campo Minado 1v1";
    }

    @Override
    public String getAuthor() {
        return "Projeto de Redes de Computadores";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getIdentifier() {
        return "pitiupi.minesweeper";
    }

    @Override
    public void createPlugin(ConnectionManager connectionManager, PluginWorkspace workspace) {
        this.workspace = workspace;
        this.controller = new GameController(new PitiupiNet(connectionManager), connectionManager::getUserName);
        this.panel = new MinesweeperPanel(controller);
        controller.start();
        bindShellWorkspaceContent(panel);
        installPeerActions(workspace.peerActions());
    }

    @Override
    public void installPeerActions(PeerActionsRegistrar registrar) {
        registrar.register(PeerAction.of(
                "minesweeper.open",
                "Abrir Campo Minado 1v1",
                PeerActionTarget.ALL_PEERS,
                target -> workspace.openOrFocusTab(getIdentifier(), getDisplayName())
        ));
    }

    @Override
    public List<Class<? extends Message>> getMessages() {
        return List.of(
                RoomQueryMessage.class,
                RoomAnnounceMessage.class,
                RoomClosedMessage.class,
                JoinRequestMessage.class,
                JoinResponseMessage.class,
                LeaveMessage.class,
                StartMessage.class,
                StatusMessage.class,
                FinishMessage.class,
                ResultMessage.class,
                RematchMessage.class
        );
    }

    @Override
    public void receiveMessage(Message message) {
        if (message instanceof MineMessage mine && controller != null) {
            // O núcleo chama aqui na thread do socket; o controller só mexe no estado na do Swing.
            SwingUtilities.invokeLater(() -> controller.receive(mine));
        }
    }

    @Override
    public void onShellTabClosed() {
        if (controller != null) {
            SwingUtilities.invokeLater(controller::leaveRoom);
        }
    }
}
