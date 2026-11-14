package pitiupi.minesweeper.net;

/** TCP, convidado para anfitrião: quero jogar de novo. O anfitrião abre nova contagem. */
public class RematchMessage extends MineMessage {

    private static final long serialVersionUID = 1L;

    public RematchMessage() {
    }

    public RematchMessage(String roomId, String senderId, String senderName, String senderIp) {
        super(roomId, senderId, senderName, senderIp);
    }
}
