package pitiupi.minesweeper.net;

/** TCP, anfitrião para convidado: aceitou ou a sala já está cheia. */
public class JoinResponseMessage extends MineMessage {

    private static final long serialVersionUID = 1L;

    private JoinStatus status;
    private String roomName;

    public JoinResponseMessage() {
    }

    public JoinResponseMessage(
            String roomId, String hostId, String hostName, String hostIp,
            JoinStatus status, String roomName
    ) {
        super(roomId, hostId, hostName, hostIp);
        this.status = status;
        this.roomName = roomName;
    }

    public JoinStatus getStatus() { return status; }
    public String getRoomName() { return roomName; }
}
