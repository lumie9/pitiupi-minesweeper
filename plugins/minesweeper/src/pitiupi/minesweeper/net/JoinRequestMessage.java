package pitiupi.minesweeper.net;

/** TCP, convidado para anfitrião: "quero entrar nesta sala". */
public class JoinRequestMessage extends MineMessage {

    private static final long serialVersionUID = 1L;

    public JoinRequestMessage() {
    }

    public JoinRequestMessage(String roomId, String guestId, String guestName, String guestIp) {
        super(roomId, guestId, guestName, guestIp);
    }
}
