package pitiupi.minesweeper.control;

import pitiupi.application.messaging.TransportMode;
import pitiupi.minesweeper.game.Board;
import pitiupi.minesweeper.game.GameRules;
import pitiupi.minesweeper.net.FinishMessage;
import pitiupi.minesweeper.net.JoinRequestMessage;
import pitiupi.minesweeper.net.JoinResponseMessage;
import pitiupi.minesweeper.net.JoinStatus;
import pitiupi.minesweeper.net.LeaveMessage;
import pitiupi.minesweeper.net.MineMessage;
import pitiupi.minesweeper.net.Outcome;
import pitiupi.minesweeper.net.RematchMessage;
import pitiupi.minesweeper.net.ResultMessage;
import pitiupi.minesweeper.net.ResultReason;
import pitiupi.minesweeper.net.RoomAnnounceMessage;
import pitiupi.minesweeper.net.RoomClosedMessage;
import pitiupi.minesweeper.net.RoomPhase;
import pitiupi.minesweeper.net.RoomQueryMessage;
import pitiupi.minesweeper.net.StartMessage;
import pitiupi.minesweeper.net.StatusMessage;
import pitiupi.presentation.shell.PeerIpv4;

import javax.swing.Timer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Regras e protocolo do Campo Minado 1v1, sem desenhar nada.
 *
 * <h2>Papéis</h2>
 * Quem cria a sala é o <b>anfitrião</b> e funciona como árbitro: sorteia a semente, comanda a
 * contagem e declara o vencedor. Quem entra é o <b>convidado</b>. Só existe uma partida por
 * sala e só o anfitrião decide, então os dois nunca discordam do resultado.
 *
 * <h2>Quem fala com quem</h2>
 * <ul>
 *   <li><b>Lobby</b> (UDP): {@link RoomAnnounceMessage}, {@link RoomClosedMessage} e
 *       {@link RoomQueryMessage} vão para a sala toda (multicast ou uma cópia unicast por pessoa).</li>
 *   <li><b>Sala e jogo</b> (TCP, só entre os dois jogadores): entrada, início, progresso, fim,
 *       resultado, saída e revanche. TCP porque "começou" e "quem ganhou" não podem se perder.</li>
 * </ul>
 *
 * <h2>Thread</h2>
 * Todos os métodos públicos, os timers e os callbacks de {@link Net} rodam na thread do Swing. O
 * estado não precisa de {@code synchronized}.
 */
public final class GameController {

    public enum Phase { LOBBY, JOINING, WAITING, COUNTDOWN, PLAYING, FINISHED, CLOSED }

    public enum Role { NONE, HOST, GUEST }

    /** Tempos do protocolo. Os testes encurtam; o jogo usa os padrões. */
    public static final class Settings {
        public int countdownSeconds = 5;
        public int countdownTickMs = 1000;
        public int announceMs = 2000;
        public int keepaliveMs = 1000;
        public int roomTtlMs = 7000;
        public int peerTimeoutMs = 8000;
        public int joinTimeoutMs = 5000;
        public int resultTimeoutMs = 6000;
    }

    /** Avisos para a tela. Sempre chamados na thread do Swing. */
    public interface Listener {
        void lobbyChanged();
        void sessionChanged();
        void boardChanged();
        void notice(String text);
    }

    private static final Listener NO_OP = new Listener() {
        @Override public void lobbyChanged() { }
        @Override public void sessionChanged() { }
        @Override public void boardChanged() { }
        @Override public void notice(String text) { }
    };

    private static final long STATUS_THROTTLE_NANOS = 200_000_000L;
    private static final long ANNOUNCE_DEBOUNCE_NANOS = 300_000_000L;

    private final Net net;
    private final Supplier<String> nameSupplier;
    private final Settings settings;
    private final String myId = UUID.randomUUID().toString();
    private final Random random = new Random();
    private final Map<String, RoomInfo> rooms = new LinkedHashMap<>();

    private final Timer announceTimer;
    private final Timer tickTimer;
    private final Timer countdownTimer;
    private final Timer joinTimer;
    private final Timer resultTimer;

    private Listener listener = NO_OP;

    private Phase phase = Phase.LOBBY;
    private Role role = Role.NONE;
    private String roomId;
    private String roomName;
    private String oppId;
    private String oppName;
    private String oppIp;

    private int round;
    private Board board;
    private boolean decided;
    private boolean localDone;
    private boolean rematchRequested;
    private int countdownLeft;
    private int oppRevealed;
    private Boolean won;
    private ResultReason reason;
    private long winnerElapsedMs = -1;
    private String banner = "";

    private long startNanos;
    private long endNanos;
    private long lastHeardNanos;
    private long lastStatusNanos;
    private long lastAnnounceNanos;

    public GameController(Net net, Supplier<String> nameSupplier) {
        this(net, nameSupplier, new Settings());
    }

    public GameController(Net net, Supplier<String> nameSupplier, Settings settings) {
        this.net = net;
        this.nameSupplier = nameSupplier;
        this.settings = settings;
        this.announceTimer = new Timer(settings.announceMs, e -> announceNow());
        this.tickTimer = new Timer(settings.keepaliveMs, e -> onTick());
        this.countdownTimer = new Timer(settings.countdownTickMs, e -> onCountdownTick());
        this.joinTimer = new Timer(settings.joinTimeoutMs, e -> onJoinTimeout());
        this.resultTimer = new Timer(settings.resultTimeoutMs, e -> onResultTimeout());
        joinTimer.setRepeats(false);
        resultTimer.setRepeats(false);
    }

    public void setListener(Listener listener) {
        this.listener = listener != null ? listener : NO_OP;
    }

    /** Liga o relógio de expiração das salas e dos sinais de vida. */
    public void start() {
        tickTimer.start();
    }

    /** Sai da sala (se estiver numa) e para tudo. */
    public void shutdown() {
        leaveRoom();
        tickTimer.stop();
        announceTimer.stop();
    }

    // =====================================================================
    // Consulta (a tela só lê)
    // =====================================================================

    public String myId() { return myId; }
    public Phase phase() { return phase; }
    public Role role() { return role; }
    public String roomName() { return roomName; }
    public String opponentName() { return oppName; }
    public boolean hasOpponent() { return oppId != null; }
    public Board board() { return board; }
    public int countdownLeft() { return countdownLeft; }
    public boolean isLocalDone() { return localDone; }
    public boolean isRematchRequested() { return rematchRequested; }
    /** {@code null} até o resultado sair. */
    public Boolean won() { return won; }
    public ResultReason resultReason() { return reason; }
    public long winnerElapsedMs() { return winnerElapsedMs; }
    public String banner() { return banner; }
    public String myName() {
        String name = nameSupplier.get();
        return name == null || name.isBlank() ? "Jogador" : name.trim();
    }

    public int opponentProgressPercent() {
        if (board == null || board.safeTotal() == 0) {
            return 0;
        }
        return (int) Math.min(100, Math.round(100.0 * oppRevealed / board.safeTotal()));
    }

    /** Tempo de jogo: corre enquanto joga, congela quando termina. */
    public long elapsedMillis() {
        if (startNanos == 0) {
            return 0;
        }
        long end = (localDone && endNanos != 0) ? endNanos : System.nanoTime();
        return Math.max(0, (end - startNanos) / 1_000_000L);
    }

    public List<RoomInfo> rooms() {
        List<RoomInfo> list = new ArrayList<>(rooms.values());
        list.sort(Comparator.comparing(RoomInfo::roomName, String.CASE_INSENSITIVE_ORDER));
        return list;
    }

    // =====================================================================
    // Ações da tela
    // =====================================================================

    public void setAnnounceTransport(TransportMode mode) {
        net.setAnnounceTransport(mode);
    }

    /** Pede aos anfitriões que anunciem agora, sem esperar o próximo ciclo. */
    public void refreshRooms() {
        net.announce(new RoomQueryMessage(myId, myName(), net.localIp()));
    }

    public boolean createRoom(String name) {
        if (phase != Phase.LOBBY) {
            return false;
        }
        String cleaned = name == null ? "" : name.trim();
        if (cleaned.length() > 32) {
            cleaned = cleaned.substring(0, 32);
        }
        roomName = cleaned.isEmpty() ? "Sala de " + myName() : cleaned;
        roomId = UUID.randomUUID().toString();
        role = Role.HOST;
        phase = Phase.WAITING;
        round = 0;
        clearOpponent();
        clearRound();
        banner = "";
        announceTimer.restart();
        announceNow();
        listener.sessionChanged();
        return true;
    }

    public void joinRoom(String targetRoomId) {
        if (phase != Phase.LOBBY) {
            return;
        }
        RoomInfo info = rooms.get(targetRoomId);
        if (info == null || !info.isJoinable()) {
            listener.notice("Essa sala não está mais disponível.");
            return;
        }
        role = Role.GUEST;
        phase = Phase.JOINING;
        roomId = info.roomId();
        roomName = info.roomName();
        oppId = info.hostId();
        oppName = info.hostName();
        oppIp = PeerIpv4.normalize(info.hostIp());
        round = 0;
        clearRound();
        banner = "";

        final String attemptedRoom = roomId;
        final String hostLabel = oppName + " (" + oppIp + ")";
        net.sendReliable(oppIp, new JoinRequestMessage(roomId, myId, myName(), net.localIp()), ok -> {
            if (!ok && phase == Phase.JOINING && attemptedRoom.equals(roomId)) {
                abortJoin("Não foi possível conectar a " + hostLabel + ".");
            }
        });
        joinTimer.restart();
        listener.sessionChanged();
    }

    /** Sair da sala, ou voltar ao lobby quando a sala já foi fechada. */
    public void leaveRoom() {
        if (phase == Phase.LOBBY) {
            return;
        }
        if (role == Role.HOST) {
            net.announce(new RoomClosedMessage(roomId, myId, myName(), net.localIp()));
            if (oppId != null) {
                net.sendReliable(oppIp, new LeaveMessage(roomId, myId, myName(), net.localIp()), null);
            }
        } else if (role == Role.GUEST && phase != Phase.CLOSED && oppIp != null) {
            net.sendReliable(oppIp, new LeaveMessage(roomId, myId, myName(), net.localIp()), null);
        }
        resetToLobby();
    }

    /** Revanche: o anfitrião abre nova contagem; o convidado pede ao anfitrião. */
    public void requestRematch() {
        if (phase != Phase.FINISHED || oppId == null) {
            return;
        }
        if (role == Role.HOST) {
            beginCountdown();
        } else if (role == Role.GUEST && !rematchRequested) {
            rematchRequested = true;
            net.sendReliable(oppIp, new RematchMessage(roomId, myId, myName(), net.localIp()), null);
            listener.sessionChanged();
        }
    }

    /** Clique esquerdo. Em número já aberto faz o "chord". */
    public void reveal(int r, int c) {
        if (!canPlay()) {
            return;
        }
        int opened = board.state(r, c) == Board.REVEALED ? board.chord(r, c) : board.reveal(r, c);
        afterMove(opened);
    }

    public void toggleFlag(int r, int c) {
        if (!canPlay()) {
            return;
        }
        board.toggleFlag(r, c);
        listener.boardChanged();
    }

    private boolean canPlay() {
        return phase == Phase.PLAYING && !localDone && board != null;
    }

    private void afterMove(int opened) {
        listener.boardChanged();
        if (board.isExploded()) {
            finishLocal(Outcome.EXPLODED);
        } else if (board.isCleared()) {
            finishLocal(Outcome.CLEARED);
        } else if (opened > 0) {
            sendStatus(false);
        }
    }

    // =====================================================================
    // Recebimento (entrada única do plugin)
    // =====================================================================

    public void receive(MineMessage m) {
        if (m == null || m.getSenderId() == null || m.getSenderId().equals(myId)) {
            return; // o multicast volta para quem enviou
        }
        if (m instanceof RoomAnnounceMessage a) {
            onAnnounce(a);
            return;
        }
        if (m instanceof RoomClosedMessage c) {
            if (rooms.remove(c.getRoomId()) != null) {
                listener.lobbyChanged();
            }
            return;
        }
        if (m instanceof RoomQueryMessage) {
            if (role == Role.HOST && System.nanoTime() - lastAnnounceNanos > ANNOUNCE_DEBOUNCE_NANOS) {
                announceNow();
            }
            return;
        }
        if (roomId == null || !roomId.equals(m.getRoomId())) {
            return; // outra sala
        }
        if (m instanceof JoinRequestMessage j) {
            onJoinRequest(j);
            return;
        }
        if (!m.getSenderId().equals(oppId)) {
            return; // só o outro jogador desta sala fala comigo
        }
        lastHeardNanos = System.nanoTime();
        if (m instanceof JoinResponseMessage r) {
            onJoinResponse(r);
        } else if (m instanceof StartMessage s) {
            onStart(s);
        } else if (m instanceof StatusMessage s) {
            onStatus(s);
        } else if (m instanceof FinishMessage f) {
            onFinish(f);
        } else if (m instanceof ResultMessage r) {
            onResult(r);
        } else if (m instanceof RematchMessage) {
            if (role == Role.HOST && phase == Phase.FINISHED) {
                beginCountdown();
            }
        } else if (m instanceof LeaveMessage) {
            if (role == Role.HOST) {
                guestGone(oppName + " saiu da sala.");
            } else {
                hostGone("O anfitrião fechou a sala.");
            }
        }
    }

    // ---- lobby ----

    private void onAnnounce(RoomAnnounceMessage a) {
        if (a.getRoomId() == null || a.getPhase() == null) {
            return;
        }
        RoomInfo info = new RoomInfo(
                a.getRoomId(), a.getRoomName() == null ? "Sala" : a.getRoomName(),
                a.getSenderId(), a.getSenderName() == null ? "?" : a.getSenderName(),
                a.getSenderIp(), a.getPhase(), a.getPlayers(),
                a.getGuestName(), System.currentTimeMillis());
        RoomInfo old = rooms.put(info.roomId(), info);
        if (old == null || !old.sameVisibleState(info)) {
            listener.lobbyChanged();
        }
    }

    private void expireRooms() {
        long now = System.currentTimeMillis();
        if (rooms.values().removeIf(r -> now - r.lastSeenMillis() > settings.roomTtlMs)) {
            listener.lobbyChanged();
        }
    }

    private void announceNow() {
        if (role != Role.HOST || roomId == null) {
            return;
        }
        lastAnnounceNanos = System.nanoTime();
        RoomPhase published = switch (phase) {
            case COUNTDOWN -> RoomPhase.COUNTDOWN;
            case PLAYING -> RoomPhase.PLAYING;
            case FINISHED -> RoomPhase.FINISHED;
            default -> RoomPhase.WAITING;
        };
        net.announce(new RoomAnnounceMessage(
                roomId, myId, myName(), net.localIp(), roomName,
                published, oppId == null ? 1 : 2, oppName));
    }

    // ---- entrada na sala ----

    private void onJoinRequest(JoinRequestMessage j) {
        if (role != Role.HOST) {
            return;
        }
        String guestIp = PeerIpv4.normalize(j.getSenderIp());
        if (oppId != null) {
            if (oppId.equals(j.getSenderId())) {
                sendJoinResponse(oppIp, JoinStatus.ACCEPTED); // pedido repetido
            } else {
                sendJoinResponse(guestIp, JoinStatus.FULL);
            }
            return;
        }
        oppId = j.getSenderId();
        oppName = j.getSenderName() == null ? "Jogador" : j.getSenderName();
        oppIp = guestIp;
        lastHeardNanos = System.nanoTime();
        sendJoinResponse(oppIp, JoinStatus.ACCEPTED);
        beginCountdown();
    }

    private void sendJoinResponse(String ip, JoinStatus status) {
        net.sendReliable(ip, new JoinResponseMessage(
                roomId, myId, myName(), net.localIp(), status, roomName), null);
    }

    private void onJoinResponse(JoinResponseMessage r) {
        if (role != Role.GUEST || phase != Phase.JOINING) {
            return;
        }
        joinTimer.stop();
        if (r.getStatus() == JoinStatus.ACCEPTED) {
            phase = Phase.WAITING;
            if (r.getRoomName() != null) {
                roomName = r.getRoomName();
            }
            listener.sessionChanged();
        } else {
            String name = roomName;
            resetToLobby();
            listener.notice("A sala \"" + name + "\" já está cheia.");
        }
    }

    private void onJoinTimeout() {
        if (phase == Phase.JOINING) {
            abortJoin("A sala não respondeu.");
        }
    }

    private void abortJoin(String text) {
        resetToLobby();
        listener.notice(text);
    }

    // ---- contagem e rodada ----

    /** Anfitrião: sorteia a semente, avisa o convidado e começa a contar. */
    private void beginCountdown() {
        if (role != Role.HOST || oppId == null) {
            return;
        }
        round++;
        long seed = random.nextLong();
        board = new Board(GameRules.ROWS, GameRules.COLS, GameRules.MINES, seed);
        clearRound();
        phase = Phase.COUNTDOWN;
        countdownLeft = settings.countdownSeconds;
        net.sendReliable(oppIp, new StartMessage(
                roomId, myId, myName(), net.localIp(), round, seed, countdownLeft), null);
        countdownTimer.restart();
        announceNow();
        listener.sessionChanged();
        listener.boardChanged();
    }

    /** Convidado: mesma semente, mesmo campo. */
    private void onStart(StartMessage s) {
        if (role != Role.GUEST || s.getRound() <= round) {
            return;
        }
        if (phase != Phase.JOINING && phase != Phase.WAITING && phase != Phase.FINISHED) {
            return;
        }
        joinTimer.stop();
        resultTimer.stop();
        round = s.getRound();
        board = new Board(GameRules.ROWS, GameRules.COLS, GameRules.MINES, s.getSeed());
        clearRound();
        phase = Phase.COUNTDOWN;
        countdownLeft = Math.max(1, s.getCountdownSeconds());
        countdownTimer.restart();
        listener.sessionChanged();
        listener.boardChanged();
    }

    private void onCountdownTick() {
        countdownLeft--;
        if (countdownLeft > 0) {
            listener.sessionChanged();
            return;
        }
        countdownTimer.stop();
        if (phase == Phase.COUNTDOWN && board != null) {
            enterPlaying();
        }
    }

    private void enterPlaying() {
        phase = Phase.PLAYING;
        board.openStart();
        startNanos = System.nanoTime();
        endNanos = 0;
        announceNow();
        sendStatus(true);
        listener.sessionChanged();
        listener.boardChanged();
    }

    private void onStatus(StatusMessage s) {
        if (s.getRound() == round && phase == Phase.PLAYING) {
            oppRevealed = s.getRevealedSafe();
            listener.boardChanged();
        }
    }

    private void sendStatus(boolean force) {
        if (oppId == null || oppIp == null || roomId == null) {
            return;
        }
        long now = System.nanoTime();
        if (!force && now - lastStatusNanos < STATUS_THROTTLE_NANOS) {
            return;
        }
        lastStatusNanos = now;
        net.sendReliable(oppIp, new StatusMessage(
                roomId, myId, myName(), net.localIp(), round, board == null ? 0 : board.revealedSafe()), null);
    }

    // ---- fim da rodada ----

    private void finishLocal(Outcome outcome) {
        localDone = true;
        endNanos = System.nanoTime();
        long elapsed = elapsedMillis();
        if (role == Role.HOST) {
            if (outcome == Outcome.CLEARED) {
                decide(myId, ResultReason.CLEARED, elapsed);
            } else {
                decide(oppId, ResultReason.OPPONENT_EXPLODED, -1);
            }
        } else {
            net.sendReliable(oppIp, new FinishMessage(
                    roomId, myId, myName(), net.localIp(), round, outcome, elapsed), null);
            resultTimer.restart(); // o anfitrião tem de confirmar
            listener.sessionChanged();
            listener.boardChanged();
        }
    }

    /** Anfitrião: o primeiro fim de rodada que chega define o vencedor. */
    private void onFinish(FinishMessage f) {
        if (role != Role.HOST || decided || phase != Phase.PLAYING || f.getRound() != round) {
            return;
        }
        if (f.getOutcome() == Outcome.CLEARED) {
            decide(oppId, ResultReason.CLEARED, f.getElapsedMs());
        } else {
            decide(myId, ResultReason.OPPONENT_EXPLODED, -1);
        }
    }

    private void decide(String winnerId, ResultReason why, long winnerElapsed) {
        if (decided) {
            return;
        }
        decided = true;
        resultTimer.stop();
        countdownTimer.stop();
        phase = Phase.FINISHED;
        won = myId.equals(winnerId);
        reason = why;
        winnerElapsedMs = winnerElapsed;
        if (!localDone) {
            localDone = true;
            endNanos = System.nanoTime();
        }
        net.sendReliable(oppIp, new ResultMessage(
                roomId, myId, myName(), net.localIp(), round, winnerId, why, winnerElapsed), null);
        announceNow();
        listener.sessionChanged();
        listener.boardChanged();
    }

    private void onResult(ResultMessage r) {
        if (role != Role.GUEST || r.getRound() != round || phase == Phase.FINISHED) {
            return;
        }
        resultTimer.stop();
        countdownTimer.stop();
        decided = true;
        phase = Phase.FINISHED;
        won = myId.equals(r.getWinnerId());
        reason = r.getReason();
        winnerElapsedMs = r.getWinnerElapsedMs();
        if (!localDone) {
            localDone = true;
            endNanos = System.nanoTime();
        }
        listener.sessionChanged();
        listener.boardChanged();
    }

    private void onResultTimeout() {
        if (role == Role.GUEST && phase == Phase.PLAYING && localDone) {
            hostGone("O anfitrião não confirmou o resultado.");
        }
    }

    // ---- saída e queda ----

    /** Anfitrião: o convidado saiu ou sumiu. A sala volta a aguardar alguém. */
    private void guestGone(String text) {
        boolean midRound = (phase == Phase.COUNTDOWN || phase == Phase.PLAYING) && !decided;
        countdownTimer.stop();
        resultTimer.stop();
        clearOpponent();
        clearRound();
        board = null;
        phase = Phase.WAITING;
        banner = text + (midRound ? " Você venceu por W.O." : "");
        announceNow();
        listener.sessionChanged();
        listener.boardChanged();
    }

    /** Convidado: o anfitrião fechou a sala ou sumiu. Não há como continuar. */
    private void hostGone(String text) {
        boolean midRound = (phase == Phase.COUNTDOWN || phase == Phase.PLAYING) && !decided;
        countdownTimer.stop();
        resultTimer.stop();
        phase = Phase.CLOSED;
        localDone = true;
        banner = text + (midRound ? " Você venceu por W.O." : "");
        listener.sessionChanged();
        listener.boardChanged();
    }

    private void onTick() {
        expireRooms();
        if (oppId == null || phase == Phase.LOBBY || phase == Phase.JOINING || phase == Phase.CLOSED) {
            return;
        }
        sendStatus(true);
        long silentMs = (System.nanoTime() - lastHeardNanos) / 1_000_000L;
        if (silentMs > settings.peerTimeoutMs) {
            if (role == Role.HOST) {
                guestGone(oppName + " parou de responder.");
            } else {
                hostGone("O anfitrião parou de responder.");
            }
        }
    }

    // ---- estado ----

    private void clearOpponent() {
        oppId = null;
        oppName = null;
        oppIp = null;
    }

    private void clearRound() {
        decided = false;
        localDone = false;
        rematchRequested = false;
        oppRevealed = 0;
        won = null;
        reason = null;
        winnerElapsedMs = -1;
        startNanos = 0;
        endNanos = 0;
        countdownLeft = 0;
        banner = "";
    }

    private void resetToLobby() {
        announceTimer.stop();
        countdownTimer.stop();
        joinTimer.stop();
        resultTimer.stop();
        role = Role.NONE;
        phase = Phase.LOBBY;
        roomId = null;
        roomName = null;
        board = null;
        clearOpponent();
        clearRound();
        listener.sessionChanged();
        listener.lobbyChanged();
        refreshRooms();
    }
}
