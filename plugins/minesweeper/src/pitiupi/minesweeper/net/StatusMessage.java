package pitiupi.minesweeper.net;

/**
 * TCP, nos dois sentidos: progresso (casas seguras abertas) e sinal de vida. Sai a cada
 * segundo e logo após uma jogada. Se ficar quieto por muito tempo, o outro lado considera
 * que o jogador caiu.
 */
public class StatusMessage extends MineMessage {

    private static final long serialVersionUID = 1L;

    private int round;
    private int revealedSafe;

    public StatusMessage() {
    }

    public StatusMessage(String roomId, String senderId, String senderName, String senderIp,
                         int round, int revealedSafe) {
        super(roomId, senderId, senderName, senderIp);
        this.round = round;
        this.revealedSafe = revealedSafe;
    }

    public int getRound() { return round; }
    public int getRevealedSafe() { return revealedSafe; }
}
