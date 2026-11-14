package pitiupi.minesweeper.net;

/** TCP: quem envia saiu da sala (botão Sair, aba fechada). */
public class LeaveMessage extends MineMessage {

    private static final long serialVersionUID = 1L;

    public LeaveMessage() {
    }

    public LeaveMessage(String roomId, String senderId, String senderName, String senderIp) {
        super(roomId, senderId, senderName, senderIp);
    }
}
