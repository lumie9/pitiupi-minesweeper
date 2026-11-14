package pitiupi.minesweeper.net;

/**
 * UDP, lobby: o anfitrião repete este aviso a cada poucos segundos. Quem não recebe o aviso
 * por um tempo tira a sala da lista. O remetente ({@code senderId/Name/Ip}) é o anfitrião.
 */
public class RoomAnnounceMessage extends MineMessage {

    private static final long serialVersionUID = 1L;

    private String roomName;
    private RoomPhase phase;
    private int players;
    private String guestName;

    public RoomAnnounceMessage() {
    }

    public RoomAnnounceMessage(
            String roomId, String hostId, String hostName, String hostIp,
            String roomName, RoomPhase phase, int players, String guestName
    ) {
        super(roomId, hostId, hostName, hostIp);
        this.roomName = roomName;
        this.phase = phase;
        this.players = players;
        this.guestName = guestName;
    }

    public String getRoomName() { return roomName; }
    public RoomPhase getPhase() { return phase; }
    public int getPlayers() { return players; }
    public String getGuestName() { return guestName; }
}
