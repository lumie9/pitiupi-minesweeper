package pitiupi.minesweeper.net;

/** UDP, lobby: "quem tem sala aberta?". Cada anfitrião responde com um {@link RoomAnnounceMessage}. */
public class RoomQueryMessage extends MineMessage {

    private static final long serialVersionUID = 1L;

    public RoomQueryMessage() {
    }

    public RoomQueryMessage(String senderId, String senderName, String senderIp) {
        super(null, senderId, senderName, senderIp);
    }
}
