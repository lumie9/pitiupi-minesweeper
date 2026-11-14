package pitiupi.minesweeper.net;

/** TCP, anfitrião para convidado: quem venceu a rodada. Só o anfitrião decide. */
public class ResultMessage extends MineMessage {

    private static final long serialVersionUID = 1L;

    private int round;
    private String winnerId;
    private ResultReason reason;
    private long winnerElapsedMs;

    public ResultMessage() {
    }

    public ResultMessage(String roomId, String hostId, String hostName, String hostIp,
                         int round, String winnerId, ResultReason reason, long winnerElapsedMs) {
        super(roomId, hostId, hostName, hostIp);
        this.round = round;
        this.winnerId = winnerId;
        this.reason = reason;
        this.winnerElapsedMs = winnerElapsedMs;
    }

    public int getRound() { return round; }
    public String getWinnerId() { return winnerId; }
    public ResultReason getReason() { return reason; }
    /** Tempo de quem limpou o campo, em ms; {@code -1} quando a vitória não veio de limpar. */
    public long getWinnerElapsedMs() { return winnerElapsedMs; }
}
