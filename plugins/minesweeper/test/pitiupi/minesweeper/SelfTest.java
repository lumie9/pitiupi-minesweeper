package pitiupi.minesweeper;

import pitiupi.application.messaging.TransportMode;
import pitiupi.minesweeper.control.GameController;
import pitiupi.minesweeper.control.GameController.Phase;
import pitiupi.minesweeper.control.Net;
import pitiupi.minesweeper.control.RoomInfo;
import pitiupi.minesweeper.game.Board;
import pitiupi.minesweeper.game.GameRules;
import pitiupi.minesweeper.net.*;

import javax.swing.SwingUtilities;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Testes sem JUnit: {@code java -cp ... pitiupi.minesweeper.SelfTest}. Cobre o tabuleiro, a
 * serialização das mensagens e o protocolo inteiro entre dois (ou três) controllers ligados por
 * uma rede em memória. Sai com código 1 se algo falhar.
 */
public final class SelfTest {

    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        run("Board: mesma semente, mesmo campo", SelfTest::boardDeterminism);
        run("Board: invariantes em 600 sementes", SelfTest::boardInvariants);
        run("Board: abrir tudo vence, mina explode, chord", SelfTest::boardRules);
        run("Mensagens: serialização ida e volta", SelfTest::messagesRoundTrip);
        run("Protocolo: sala, contagem, anfitrião vence", SelfTest::hostWins);
        run("Protocolo: convidado vence", SelfTest::guestWins);
        run("Protocolo: convidado explode, anfitrião vence", SelfTest::guestExplodes);
        run("Protocolo: anfitrião explode, convidado vence", SelfTest::hostExplodes);
        run("Protocolo: revanche usa nova semente", SelfTest::rematch);
        run("Protocolo: terceiro jogador recebe sala cheia", SelfTest::fullRoom);
        run("Protocolo: convidado sai no meio (W.O.)", SelfTest::guestLeaves);
        run("Protocolo: anfitrião sai no meio (W.O.)", SelfTest::hostLeaves);
        run("Protocolo: oponente some (timeout)", SelfTest::peerTimeout);
        run("Lobby: sala expira sem anúncio e some ao fechar", SelfTest::roomExpiry);
        run("Lobby: falha de conexão volta ao lobby", SelfTest::joinFails);
        run("Protocolo: só o anfitrião decide, duas conclusões seguidas", SelfTest::firstFinishWins);
        System.out.println();
        System.out.println(passed + " ok, " + failed + " falhou");
        System.exit(failed == 0 ? 0 : 1);
    }

    // ------------------------------------------------------------------ infraestrutura

    private interface Body { void run() throws Exception; }

    private static void run(String name, Body body) {
        try {
            body.run();
            passed++;
            System.out.println("  ok   " + name);
        } catch (Throwable t) {
            failed++;
            System.out.println("  FALHOU " + name + " -> " + t);
            t.printStackTrace(System.out);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static <T> T edt(Supplier<T> supplier) throws Exception {
        AtomicReference<T> out = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> out.set(supplier.get()));
        return out.get();
    }

    private static void edtRun(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    private static void waitFor(String what, BooleanSupplier condition) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            if (edt(condition::getAsBoolean)) {
                return;
            }
            Thread.sleep(15);
        }
        throw new AssertionError("tempo esgotado esperando: " + what);
    }

    /** Rede em memória: cada IP aponta para um controller. */
    private static final class Hub {
        final Map<String, Peer> peers = new HashMap<>();
        volatile boolean dropAll;
        volatile boolean failSends;

        Peer add(String ip, String name, GameController.Settings s) throws Exception {
            Peer p = new Peer(this, ip);
            p.controller = edt(() -> new GameController(p, () -> name, s));
            peers.put(ip, p);
            edtRun(p.controller::start);
            return p;
        }
    }

    private static final class Peer implements Net {
        final Hub hub;
        final String ip;
        GameController controller;
        volatile boolean mute;
        final List<MineMessage> sentReliable = new ArrayList<>();
        TransportMode mode = TransportMode.MULTICAST_UDP;

        Peer(Hub hub, String ip) {
            this.hub = hub;
            this.ip = ip;
        }

        @Override public void announce(MineMessage m) {
            if (mute || hub.dropAll) return;
            for (Peer other : hub.peers.values()) {
                deliver(other, m); // inclui quem enviou, como no multicast real
            }
        }

        @Override public void sendReliable(String to, MineMessage m, Consumer<Boolean> onResult) {
            synchronized (sentReliable) { sentReliable.add(m); }
            boolean ok = !hub.failSends && !mute && !hub.dropAll && hub.peers.containsKey(to);
            if (ok) {
                deliver(hub.peers.get(to), m);
            }
            if (onResult != null) {
                SwingUtilities.invokeLater(() -> onResult.accept(ok));
            }
        }

        private void deliver(Peer to, MineMessage m) {
            try {
                // passa pela serialização de verdade, como no núcleo
                MineMessage copy = roundTrip(m);
                SwingUtilities.invokeLater(() -> to.controller.receive(copy));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override public void setAnnounceTransport(TransportMode m) { mode = m; }
        @Override public TransportMode getAnnounceTransport() { return mode; }
        @Override public String localIp() { return ip; }
    }

    private static MineMessage roundTrip(MineMessage m) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(m);
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (MineMessage) in.readObject();
        }
    }

    private static GameController.Settings fast() {
        GameController.Settings s = new GameController.Settings();
        s.countdownSeconds = 2;
        s.countdownTickMs = 40;
        s.announceMs = 80;
        s.keepaliveMs = 40;
        s.roomTtlMs = 400;
        s.peerTimeoutMs = 300;
        s.joinTimeoutMs = 400;
        s.resultTimeoutMs = 300;
        return s;
    }

    /** Anfitrião cria sala, convidado entra, os dois chegam em PLAYING. */
    private static Peer[] startMatch(Hub hub, GameController.Settings s) throws Exception {
        Peer host = hub.add("10.0.0.1", "Ana", s);
        Peer guest = hub.add("10.0.0.2", "Bruno", s);
        edtRun(() -> host.controller.createRoom("Sala da Ana"));
        waitFor("convidado ver a sala", () -> guest.controller.rooms().size() == 1);
        String roomId = edt(() -> guest.controller.rooms().get(0).roomId());
        edtRun(() -> guest.controller.joinRoom(roomId));
        waitFor("anfitrião em PLAYING", () -> host.controller.phase() == Phase.PLAYING);
        waitFor("convidado em PLAYING", () -> guest.controller.phase() == Phase.PLAYING);
        return new Peer[]{host, guest};
    }

    /** Abre todas as casas seguras de quem joga (trapaça de teste: lê o próprio tabuleiro). */
    private static void solve(Peer p) throws Exception {
        edtRun(() -> {
            Board b = p.controller.board();
            for (int r = 0; r < b.rows(); r++) {
                for (int c = 0; c < b.cols(); c++) {
                    if (!b.isMine(r, c)) {
                        p.controller.reveal(r, c);
                    }
                }
            }
        });
    }

    private static void explode(Peer p) throws Exception {
        edtRun(() -> {
            Board b = p.controller.board();
            for (int r = 0; r < b.rows(); r++) {
                for (int c = 0; c < b.cols(); c++) {
                    if (b.isMine(r, c)) {
                        p.controller.reveal(r, c);
                        return;
                    }
                }
            }
        });
    }

    private static void shutdown(Peer... peers) throws Exception {
        for (Peer p : peers) {
            edtRun(p.controller::shutdown);
        }
    }

    // ------------------------------------------------------------------ Board

    private static void boardDeterminism() {
        Board a = new Board(16, 16, 40, 123456789L);
        Board b = new Board(16, 16, 40, 123456789L);
        for (int r = 0; r < 16; r++) {
            for (int c = 0; c < 16; c++) {
                check(a.isMine(r, c) == b.isMine(r, c), "minas diferentes em " + r + "," + c);
                check(a.adjacent(r, c) == b.adjacent(r, c), "números diferentes");
            }
        }
        check(a.startRow() == b.startRow() && a.startCol() == b.startCol(), "início diferente");
        Board other = new Board(16, 16, 40, 987654321L);
        boolean differs = false;
        for (int r = 0; r < 16 && !differs; r++) {
            for (int c = 0; c < 16; c++) {
                if (a.isMine(r, c) != other.isMine(r, c)) { differs = true; break; }
            }
        }
        check(differs, "sementes diferentes deram o mesmo campo");
    }

    private static void boardInvariants() {
        for (long seed = 0; seed < 600; seed++) {
            Board b = new Board(GameRules.ROWS, GameRules.COLS, GameRules.MINES, seed * 7919 + 13);
            int mines = 0;
            for (int r = 0; r < b.rows(); r++) {
                for (int c = 0; c < b.cols(); c++) {
                    if (b.isMine(r, c)) mines++;
                }
            }
            check(mines == GameRules.MINES, "número de minas errado: " + mines);
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    check(!b.isMine(b.startRow() + dr, b.startCol() + dc), "mina perto do início");
                }
            }
            int opened = b.openStart();
            check(opened >= 9, "o início deve abrir pelo menos 3x3");
            check(!b.isExploded() && !b.isCleared(), "estado inicial errado");
            check(b.revealedSafe() == opened, "contagem de abertas errada");
        }
    }

    private static void boardRules() {
        Board b = new Board(9, 9, 10, 42);
        b.openStart();
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (!b.isMine(r, c)) b.reveal(r, c);
            }
        }
        check(b.isCleared() && !b.isExploded(), "abrir tudo deve vencer");
        check(b.progressPercent() == 100, "progresso deveria ser 100");

        Board m = new Board(9, 9, 10, 42);
        outer:
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (m.isMine(r, c)) {
                    m.reveal(r, c);
                    break outer;
                }
            }
        }
        check(m.isExploded() && !m.isCleared(), "mina deve explodir");
        check(m.reveal(0, 0) == 0, "depois de explodir nada abre");

        // chord: marca as minas ao redor de um número e clica no número
        Board k = new Board(9, 9, 10, 7);
        k.openStart();
        int[] target = null;
        for (int r = 0; r < 9 && target == null; r++) {
            for (int c = 0; c < 9; c++) {
                if (k.state(r, c) == Board.REVEALED && k.adjacent(r, c) > 0) {
                    target = new int[]{r, c};
                    break;
                }
            }
        }
        check(target != null, "esperava um número aberto no início");
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int nr = target[0] + dr, nc = target[1] + dc;
                if (nr >= 0 && nr < 9 && nc >= 0 && nc < 9 && k.isMine(nr, nc)) k.toggleFlag(nr, nc);
            }
        }
        int before = k.revealedSafe();
        k.chord(target[0], target[1]);
        check(!k.isExploded(), "chord com bandeiras certas não explode");
        check(k.revealedSafe() >= before, "chord não deve diminuir as abertas");

        Board f = new Board(9, 9, 10, 7);
        f.toggleFlag(0, 0);
        check(f.state(0, 0) == Board.FLAGGED && f.flags() == 1, "bandeira");
        check(f.reveal(0, 0) == 0 && f.state(0, 0) == Board.FLAGGED, "bandeira protege a casa");
        f.toggleFlag(0, 0);
        check(f.state(0, 0) == Board.HIDDEN && f.flags() == 0, "tirar bandeira");
    }

    // ------------------------------------------------------------------ mensagens

    private static void messagesRoundTrip() throws Exception {
        List<MineMessage> all = List.of(
                new RoomQueryMessage("a", "Ana", "10.0.0.1"),
                new RoomAnnounceMessage("r", "a", "Ana", "10.0.0.1", "Sala", RoomPhase.PLAYING, 2, "Bruno"),
                new RoomClosedMessage("r", "a", "Ana", "10.0.0.1"),
                new JoinRequestMessage("r", "b", "Bruno", "10.0.0.2"),
                new JoinResponseMessage("r", "a", "Ana", "10.0.0.1", JoinStatus.FULL, "Sala"),
                new LeaveMessage("r", "b", "Bruno", "10.0.0.2"),
                new StartMessage("r", "a", "Ana", "10.0.0.1", 3, -5L, 5),
                new StatusMessage("r", "b", "Bruno", "10.0.0.2", 3, 41),
                new FinishMessage("r", "b", "Bruno", "10.0.0.2", 3, Outcome.CLEARED, 61234L),
                new ResultMessage("r", "a", "Ana", "10.0.0.1", 3, "b", ResultReason.CLEARED, 61234L),
                new RematchMessage("r", "b", "Bruno", "10.0.0.2"));
        for (MineMessage m : all) {
            MineMessage copy = roundTrip(m);
            check(copy.getClass() == m.getClass(), "classe mudou: " + m.getClass());
            check(java.util.Objects.equals(copy.getSenderId(), m.getSenderId()), "senderId");
            check(java.util.Objects.equals(copy.getRoomId(), m.getRoomId()), "roomId");
            check(copy.isPublic(), "deve ser pública");
        }
        StartMessage s = (StartMessage) roundTrip(all.get(6));
        check(s.getSeed() == -5L && s.getRound() == 3 && s.getCountdownSeconds() == 5, "campos do Start");
    }

    // ------------------------------------------------------------------ protocolo

    private static void hostWins() throws Exception {
        Hub hub = new Hub();
        Peer[] p = startMatch(hub, fast());
        Peer host = p[0], guest = p[1];
        check(edt(() -> host.controller.board().seed()).equals(edt(() -> guest.controller.board().seed())),
                "os dois precisam ter a mesma semente");
        check(edt(() -> host.controller.board().revealedSafe()) > 0, "início aberto para o anfitrião");
        check(edt(() -> guest.controller.board().revealedSafe()).equals(edt(() -> host.controller.board().revealedSafe())),
                "o início aberto deve ser igual");
        solve(host);
        waitFor("convidado receber o resultado", () -> guest.controller.phase() == Phase.FINISHED);
        check(edt(() -> host.controller.won()) == Boolean.TRUE, "anfitrião deveria ganhar");
        check(edt(() -> guest.controller.won()) == Boolean.FALSE, "convidado deveria perder");
        check(edt(() -> guest.controller.resultReason()) == ResultReason.CLEARED, "motivo CLEARED");
        check(edt(() -> guest.controller.winnerElapsedMs()) >= 0, "tempo do vencedor");
        shutdown(host, guest);
    }

    private static void guestWins() throws Exception {
        Hub hub = new Hub();
        Peer[] p = startMatch(hub, fast());
        Peer host = p[0], guest = p[1];
        solve(guest);
        waitFor("anfitrião decidir", () -> host.controller.phase() == Phase.FINISHED);
        waitFor("convidado receber a confirmação", () -> guest.controller.phase() == Phase.FINISHED);
        check(edt(() -> guest.controller.won()) == Boolean.TRUE, "convidado deveria ganhar");
        check(edt(() -> host.controller.won()) == Boolean.FALSE, "anfitrião deveria perder");
        shutdown(host, guest);
    }

    private static void guestExplodes() throws Exception {
        Hub hub = new Hub();
        Peer[] p = startMatch(hub, fast());
        Peer host = p[0], guest = p[1];
        explode(guest);
        waitFor("anfitrião decidir", () -> host.controller.phase() == Phase.FINISHED);
        waitFor("convidado receber o resultado", () -> guest.controller.phase() == Phase.FINISHED);
        check(edt(() -> host.controller.won()) == Boolean.TRUE, "anfitrião ganha");
        check(edt(() -> guest.controller.won()) == Boolean.FALSE, "convidado perde");
        check(edt(() -> host.controller.resultReason()) == ResultReason.OPPONENT_EXPLODED, "motivo");
        shutdown(host, guest);
    }

    private static void hostExplodes() throws Exception {
        Hub hub = new Hub();
        Peer[] p = startMatch(hub, fast());
        Peer host = p[0], guest = p[1];
        explode(host);
        waitFor("convidado receber o resultado", () -> guest.controller.phase() == Phase.FINISHED);
        check(edt(() -> guest.controller.won()) == Boolean.TRUE, "convidado ganha");
        check(edt(() -> host.controller.won()) == Boolean.FALSE, "anfitrião perde");
        shutdown(host, guest);
    }

    private static void rematch() throws Exception {
        Hub hub = new Hub();
        Peer[] p = startMatch(hub, fast());
        Peer host = p[0], guest = p[1];
        long firstSeed = edt(() -> host.controller.board().seed()).longValue();
        solve(host);
        waitFor("fim da primeira rodada", () -> guest.controller.phase() == Phase.FINISHED);
        edtRun(() -> guest.controller.requestRematch());
        waitFor("anfitrião em nova contagem ou jogo", () ->
                host.controller.phase() == Phase.COUNTDOWN || host.controller.phase() == Phase.PLAYING);
        waitFor("nova rodada nos dois", () ->
                host.controller.phase() == Phase.PLAYING && guest.controller.phase() == Phase.PLAYING);
        long secondSeed = edt(() -> host.controller.board().seed()).longValue();
        check(secondSeed != firstSeed, "revanche deveria sortear outra semente");
        check(secondSeed == edt(() -> guest.controller.board().seed()).longValue(), "sementes iguais na revanche");
        check(edt(() -> host.controller.won()) == null, "resultado deve zerar");
        solve(guest);
        waitFor("fim da revanche", () -> host.controller.phase() == Phase.FINISHED);
        check(edt(() -> guest.controller.won()) == Boolean.TRUE, "convidado ganha a revanche");
        shutdown(host, guest);
    }

    private static void fullRoom() throws Exception {
        Hub hub = new Hub();
        GameController.Settings s = fast();
        s.countdownSeconds = 50; // deixa a sala em contagem enquanto o terceiro tenta
        Peer host = hub.add("10.0.0.1", "Ana", s);
        Peer guest = hub.add("10.0.0.2", "Bruno", s);
        Peer third = hub.add("10.0.0.3", "Carla", s);
        edtRun(() -> host.controller.createRoom("Cheia"));
        waitFor("salas visíveis", () -> guest.controller.rooms().size() == 1 && third.controller.rooms().size() == 1);
        String roomId = edt(() -> guest.controller.rooms().get(0).roomId());
        edtRun(() -> guest.controller.joinRoom(roomId));
        waitFor("sala em contagem", () -> host.controller.phase() == Phase.COUNTDOWN);
        // Carla ainda tem o anúncio antigo "aguardando" em cache e tenta entrar assim mesmo.
        edtRun(() -> third.controller.joinRoom(roomId));
        waitFor("Carla de volta ao lobby", () ->
                third.controller.phase() == Phase.LOBBY || third.controller.phase() == Phase.JOINING);
        waitFor("Carla recusada", () -> third.controller.phase() == Phase.LOBBY);
        check(edt(() -> host.controller.opponentName()).equals("Bruno"), "o convidado original continua na sala");
        shutdown(host, guest, third);
    }

    private static void guestLeaves() throws Exception {
        Hub hub = new Hub();
        Peer[] p = startMatch(hub, fast());
        Peer host = p[0], guest = p[1];
        edtRun(() -> guest.controller.leaveRoom());
        waitFor("anfitrião voltar a aguardar", () ->
                host.controller.phase() == Phase.WAITING && !host.controller.hasOpponent());
        check(edt(() -> host.controller.banner()).contains("W.O."), "banner de W.O.");
        check(edt(() -> guest.controller.phase()) == Phase.LOBBY, "convidado no lobby");
        waitFor("sala anunciada com 1 jogador", () -> {
            for (RoomInfo r : guest.controller.rooms()) {
                if (r.players() == 1 && r.isJoinable()) return true;
            }
            return false;
        });
        shutdown(host, guest);
    }

    private static void hostLeaves() throws Exception {
        Hub hub = new Hub();
        Peer[] p = startMatch(hub, fast());
        Peer host = p[0], guest = p[1];
        edtRun(() -> host.controller.leaveRoom());
        waitFor("convidado ver a sala fechada", () -> guest.controller.phase() == Phase.CLOSED);
        check(edt(() -> guest.controller.banner()).contains("W.O."), "banner de W.O. para o convidado");
        edtRun(() -> guest.controller.leaveRoom());
        check(edt(() -> guest.controller.phase()) == Phase.LOBBY, "voltou ao lobby");
        waitFor("sala fechada some do lobby", () -> guest.controller.rooms().isEmpty());
        shutdown(host, guest);
    }

    private static void peerTimeout() throws Exception {
        Hub hub = new Hub();
        Peer[] p = startMatch(hub, fast());
        Peer host = p[0], guest = p[1];
        hub.dropAll = true; // cabo puxado
        waitFor("anfitrião perceber a queda", () -> host.controller.phase() == Phase.WAITING);
        waitFor("convidado perceber a queda", () -> guest.controller.phase() == Phase.CLOSED);
        check(edt(() -> host.controller.banner()).contains("parou de responder"), "banner de timeout");
        shutdown(host, guest);
    }

    private static void roomExpiry() throws Exception {
        Hub hub = new Hub();
        GameController.Settings s = fast();
        Peer host = hub.add("10.0.0.1", "Ana", s);
        Peer guest = hub.add("10.0.0.2", "Bruno", s);
        edtRun(() -> host.controller.createRoom("Some logo"));
        waitFor("sala aparecer", () -> guest.controller.rooms().size() == 1);
        host.mute = true; // o anfitrião some sem fechar a sala
        waitFor("sala expirar", () -> guest.controller.rooms().isEmpty());
        host.mute = false;
        waitFor("sala voltar a aparecer", () -> guest.controller.rooms().size() == 1);
        edtRun(() -> host.controller.leaveRoom());
        waitFor("sala sumir ao fechar", () -> guest.controller.rooms().isEmpty());
        shutdown(host, guest);
    }

    private static void joinFails() throws Exception {
        Hub hub = new Hub();
        GameController.Settings s = fast();
        Peer host = hub.add("10.0.0.1", "Ana", s);
        Peer guest = hub.add("10.0.0.2", "Bruno", s);
        edtRun(() -> host.controller.createRoom("Inalcançável"));
        waitFor("sala aparecer", () -> guest.controller.rooms().size() == 1);
        String roomId = edt(() -> guest.controller.rooms().get(0).roomId());
        List<String> notices = new ArrayList<>();
        edtRun(() -> guest.controller.setListener(new GameController.Listener() {
            @Override public void lobbyChanged() { }
            @Override public void sessionChanged() { }
            @Override public void boardChanged() { }
            @Override public void notice(String text) { notices.add(text); }
        }));
        hub.failSends = true;
        edtRun(() -> guest.controller.joinRoom(roomId));
        waitFor("voltar ao lobby", () -> guest.controller.phase() == Phase.LOBBY);
        check(edt(() -> !notices.isEmpty() && notices.get(0).contains("Não foi possível conectar")), "aviso de falha");
        shutdown(host, guest);
    }

    private static void firstFinishWins() throws Exception {
        Hub hub = new Hub();
        Peer[] p = startMatch(hub, fast());
        Peer host = p[0], guest = p[1];
        // os dois terminam "ao mesmo tempo"; vale quem chegou primeiro ao anfitrião
        solve(guest);
        solve(host);
        waitFor("fim", () -> host.controller.phase() == Phase.FINISHED && guest.controller.phase() == Phase.FINISHED);
        Boolean hostWon = edt(() -> host.controller.won());
        Boolean guestWon = edt(() -> guest.controller.won());
        check(hostWon != null && guestWon != null && hostWon != guestWon, "os dois nunca podem concordar em ganhar ou perder");
        shutdown(host, guest);
    }
}
