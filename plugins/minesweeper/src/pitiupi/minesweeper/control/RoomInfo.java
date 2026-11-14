package pitiupi.minesweeper.control;

import pitiupi.minesweeper.net.RoomPhase;

/** Uma sala vista no lobby, montada a partir do último anúncio recebido. */
public final class RoomInfo {

    private final String roomId;
    private final String roomName;
    private final String hostId;
    private final String hostName;
    private final String hostIp;
    private final RoomPhase phase;
    private final int players;
    private final String guestName;
    private final long lastSeenMillis;

    public RoomInfo(String roomId, String roomName, String hostId, String hostName, String hostIp,
                    RoomPhase phase, int players, String guestName,
                    long lastSeenMillis) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.hostId = hostId;
        this.hostName = hostName;
        this.hostIp = hostIp;
        this.phase = phase;
        this.players = players;
        this.guestName = guestName;
        this.lastSeenMillis = lastSeenMillis;
    }

    public String roomId() { return roomId; }
    public String roomName() { return roomName; }
    public String hostId() { return hostId; }
    public String hostName() { return hostName; }
    public String hostIp() { return hostIp; }
    public RoomPhase phase() { return phase; }
    public int players() { return players; }
    public String guestName() { return guestName; }
    public long lastSeenMillis() { return lastSeenMillis; }

    /** Cabe mais um jogador e a sala ainda não começou. */
    public boolean isJoinable() {
        return players < 2 && phase == RoomPhase.WAITING;
    }

    /** Só o que aparece na tabela; o horário do último aviso não conta. */
    boolean sameVisibleState(RoomInfo other) {
        return other != null
                && roomName.equals(other.roomName)
                && hostName.equals(other.hostName)
                && phase == other.phase
                && players == other.players;
    }
}
