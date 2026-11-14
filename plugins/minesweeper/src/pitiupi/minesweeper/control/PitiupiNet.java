package pitiupi.minesweeper.control;

import pitiupi.application.messaging.MessageDispatcher;
import pitiupi.application.messaging.SendTarget;
import pitiupi.application.messaging.TransportMode;
import pitiupi.control.ConnectionManager;
import pitiupi.minesweeper.net.MineMessage;
import pitiupi.presentation.shell.LocalNetworkInfo;
import pitiupi.presentation.shell.PeerIpv4;

import javax.swing.SwingUtilities;
import java.net.InetAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * {@link Net} sobre o {@link MessageDispatcher} do núcleo. O plugin não abre socket: só escolhe
 * o destino e o transporte.
 *
 * <p>Duas filas de uma thread cada. A dos anúncios (UDP) nunca espera por uma conexão TCP lenta.
 * A do TCP mantém a ordem das mensagens: "aceito" sempre sai antes de "começou", e "começou" antes
 * do progresso.</p>
 */
public final class PitiupiNet implements Net {

    private static final long IP_CACHE_MS = 10_000L;

    private final ConnectionManager connectionManager;
    private final ExecutorService udpQueue = daemonSingle("minesweeper-udp");
    private final ExecutorService tcpQueue = daemonSingle("minesweeper-tcp");

    private volatile TransportMode announceMode = TransportMode.MULTICAST_UDP;
    private volatile String cachedIp;
    private volatile long cachedAt;

    public PitiupiNet(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public void announce(MineMessage message) {
        final TransportMode mode = announceMode;
        udpQueue.execute(() -> {
            try {
                MessageDispatcher.send(connectionManager, message, SendTarget.broadcastAll(), mode);
            } catch (RuntimeException ex) {
                System.err.println("Campo Minado: falha ao anunciar: " + ex);
            }
        });
    }

    @Override
    public void sendReliable(String ip, MineMessage message, Consumer<Boolean> onResult) {
        tcpQueue.execute(() -> {
            boolean ok = false;
            try {
                InetAddress address = InetAddress.getByName(PeerIpv4.normalize(ip));
                ok = MessageDispatcher.send(
                        connectionManager, message, SendTarget.singlePeer(address), TransportMode.TCP) > 0;
            } catch (Exception ex) {
                System.err.println("Campo Minado: falha ao enviar para " + ip + ": " + ex);
            }
            if (onResult != null) {
                final boolean result = ok;
                SwingUtilities.invokeLater(() -> onResult.accept(result));
            }
        });
    }

    @Override
    public void setAnnounceTransport(TransportMode mode) {
        if (mode == TransportMode.MULTICAST_UDP || mode == TransportMode.UNICAST_UDP) {
            this.announceMode = mode;
        }
    }

    @Override
    public TransportMode getAnnounceTransport() {
        return announceMode;
    }

    @Override
    public String localIp() {
        long now = System.currentTimeMillis();
        String ip = cachedIp;
        if (ip == null || now - cachedAt > IP_CACHE_MS) {
            ip = PeerIpv4.normalize(LocalNetworkInfo.bestLocalIpv4());
            cachedIp = ip;
            cachedAt = now;
        }
        return ip;
    }

    private static ExecutorService daemonSingle(String name) {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        });
    }
}
