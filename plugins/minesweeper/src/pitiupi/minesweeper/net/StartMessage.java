package pitiupi.minesweeper.net;

/**
 * TCP, anfitrião para convidado: nova rodada. A semente faz os dois computadores gerarem o
 * mesmo campo, sem trafegar o tabuleiro. Os relógios dos dois PCs não são sincronizados, então
 * a contagem vai em segundos e cada lado conta no próprio relógio a partir do recebimento.
 */
public class StartMessage extends MineMessage {

    private static final long serialVersionUID = 1L;

    private int round;
    private long seed;
    private int countdownSeconds;

    public StartMessage() {
    }

    public StartMessage(
            String roomId, String hostId, String hostName, String hostIp,
            int round, long seed, int countdownSeconds
    ) {
        super(roomId, hostId, hostName, hostIp);
        this.round = round;
        this.seed = seed;
        this.countdownSeconds = countdownSeconds;
    }

    public int getRound() { return round; }
    public long getSeed() { return seed; }
    public int getCountdownSeconds() { return countdownSeconds; }
}
