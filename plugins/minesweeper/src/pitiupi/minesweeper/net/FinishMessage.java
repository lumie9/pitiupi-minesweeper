package pitiupi.minesweeper.net;

/** TCP, convidado para anfitrião: terminei (limpei o campo ou abri uma mina). */
public class FinishMessage extends MineMessage {

    private static final long serialVersionUID = 1L;

    private int round;
    private Outcome outcome;
    private long elapsedMs;

    public FinishMessage() {
    }

    public FinishMessage(String roomId, String senderId, String senderName, String senderIp,
                         int round, Outcome outcome, long elapsedMs) {
        super(roomId, senderId, senderName, senderIp);
        this.round = round;
        this.outcome = outcome;
        this.elapsedMs = elapsedMs;
    }

    public int getRound() { return round; }
    public Outcome getOutcome() { return outcome; }
    public long getElapsedMs() { return elapsedMs; }
}
