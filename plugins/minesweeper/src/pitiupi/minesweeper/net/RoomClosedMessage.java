package pitiupi.minesweeper.net;

/** UDP, lobby: o anfitrião fechou a sala; some da lista na hora, sem esperar o tempo limite. */
public class RoomClosedMessage extends MineMessage {

    private static final long serialVersionUID = 1L;

    public RoomClosedMessage() {
    }

    public RoomClosedMessage(String roomId, String hostId, String hostName, String hostIp) {
        super(roomId, hostId, hostName, hostIp);
    }
}
